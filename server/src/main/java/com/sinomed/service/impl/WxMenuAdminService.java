package com.sinomed.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMenuEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMenuRepository;
import lombok.Data;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公众号自定义菜单管理端半边（设计稿 §4.3）：本地编辑/草稿 CRUD（两级校验/容量/加权名长）
 * + 推送微信（cgi-bin/token → cgi-bin/menu/create，出网走可配基址，测试指本地桩）。
 * 只推 enabled=1（草稿不推送）；错误不静默，errcode 透传给管理端。
 */
@Component
public class WxMenuAdminService {

    private final WxAccountRepository accountRepository;
    private final WxMenuRepository menuRepository;
    private final RestTemplate wechatRestTemplate;

    /** 微信 API 出网基址（默认官方域；测试指向本地桩，D-OA4） */
    private final String wechatApiBaseUrl;

    /** 菜单类型合法值（设计稿 §4.3，微信自定义菜单协议全集） */
    private static final Set<String> MENU_TYPES = Set.of("view", "click", "scancode_push", "scancode_waitmsg",
            "pic_sysphoto", "pic_photo_or_album", "pic_weixin", "location_select", "view_miniprogram");
    /** 一级菜单仅允许的类型（微信协议：扫码/媒体类只能做二级） */
    private static final Set<String> TOP_MENU_TYPES = Set.of("view", "click");
    /** 需要事件 key 的菜单类型（微信协议：除 view 系外回调都要带 key 路由） */
    private static final Set<String> KEY_MENU_TYPES = Set.of("click", "scancode_push", "scancode_waitmsg",
            "pic_sysphoto", "pic_photo_or_album", "pic_weixin", "location_select");

    public WxMenuAdminService(WxAccountRepository accountRepository,
                              WxMenuRepository menuRepository,
                              RestTemplate wechatRestTemplate,
                              @org.springframework.beans.factory.annotation.Value("${sinomed.official-account.wechat-api-base-url:https://api.weixin.qq.com}") String wechatApiBaseUrl) {
        this.accountRepository = accountRepository;
        this.menuRepository = menuRepository;
        this.wechatRestTemplate = wechatRestTemplate;
        this.wechatApiBaseUrl = wechatApiBaseUrl;
    }

    /**
     * 管理端菜单保存（设计稿 §4.3「编辑/草稿」本地半边，创建与更新共用；推送微信半边压凭据）：
     * 两级结构（父必须是同账号顶级菜单）、一级仅 view/click、名字数按微信口径加权校验
     * （汉字/非 ASCII 记 2 分、ASCII 记 1 分；一级 ≤8 分、二级 ≤16 分，即「4/8 汉字或 2 倍字母」）、
     * view/click/view_miniprogram 必填字段、非 view 类型强制清空 url（微信忽略，本地不留误导数据）、
     * 容量上限（每号 ≤3 一级、每个一级 ≤5 二级，更新排除自身）。
     * 与 saveAutoReply 同口径：不依赖模块开关，先建后激活。
     */
    public WxMenuEntity saveMenu(WxMenuEntity incoming) {
        if (incoming.getAccountId() == null || accountRepository.findById(incoming.getAccountId()).isEmpty()) {
            throw new IllegalArgumentException("公众号账号不存在");
        }
        String menuType = incoming.getMenuType() == null ? "" : incoming.getMenuType().trim();
        if (!MENU_TYPES.contains(menuType)) {
            throw new IllegalArgumentException("menuType 必须是微信协议枚举（view/click/scancode_push/"
                    + "scancode_waitmsg/pic_sysphoto/pic_photo_or_album/pic_weixin/location_select/view_miniprogram）");
        }
        if (isBlank(incoming.getName())) {
            throw new IllegalArgumentException("菜单名不能为空");
        }
        String name = incoming.getName().trim();

        WxMenuEntity parent = null;
        if (incoming.getParentId() != null) {
            parent = menuRepository.findById(incoming.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("父菜单不存在"));
            if (!parent.getAccountId().equals(incoming.getAccountId())) {
                throw new IllegalArgumentException("父菜单必须是同账号的菜单");
            }
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("菜单最多两级，不能挂在二级菜单下");
            }
        }
        if (parent == null && !TOP_MENU_TYPES.contains(menuType)) {
            throw new IllegalArgumentException("一级菜单只能是 view 或 click（扫码/媒体类仅二级）");
        }
        int weight = menuNameWeight(name);
        if (parent == null && weight > 8) {
            throw new IllegalArgumentException("一级菜单名最长 4 个汉字（8 个字母等效）");
        }
        if (parent != null && weight > 16) {
            throw new IllegalArgumentException("二级菜单名最长 8 个汉字（16 个字母等效）");
        }

        String url = null;
        if ("view".equals(menuType)) {
            url = requireMenuUrl(incoming.getUrl(), "view 菜单必须填写跳转地址");
        }
        String eventKey = null;
        if (KEY_MENU_TYPES.contains(menuType)) {
            if (isBlank(incoming.getEventKey())) {
                throw new IllegalArgumentException(menuType + " 菜单必须填写事件 key");
            }
            eventKey = incoming.getEventKey().trim();
        }
        String miniprogramAppid = null;
        String miniprogramPagepath = null;
        if ("view_miniprogram".equals(menuType)) {
            if (isBlank(incoming.getMiniprogramAppid()) || isBlank(incoming.getMiniprogramPagepath())) {
                throw new IllegalArgumentException("view_miniprogram 菜单必须填写小程序 appid 与 pagepath");
            }
            // 备用网页是微信协议必填（低版本客户端兜底），建菜单时就要求，避免推送时才失败
            url = requireMenuUrl(incoming.getUrl(), "view_miniprogram 菜单必须填写备用网页地址");
            miniprogramAppid = incoming.getMiniprogramAppid().trim();
            miniprogramPagepath = incoming.getMiniprogramPagepath().trim();
        }

        WxMenuEntity entity;
        if (incoming.getId() == null) {
            entity = new WxMenuEntity();
        } else {
            entity = menuRepository.findById(incoming.getId())
                    .orElseThrow(() -> new IllegalArgumentException("菜单不存在"));
            if (!entity.getAccountId().equals(incoming.getAccountId())) {
                throw new IllegalArgumentException("菜单不能跨账号移动（子菜单会悬空）");
            }
        }
        entity.setAccountId(incoming.getAccountId());
        entity.setParentId(incoming.getParentId());
        entity.setName(name);
        entity.setMenuType(menuType);
        entity.setUrl(url);
        entity.setEventKey(eventKey);
        entity.setMiniprogramAppid(miniprogramAppid);
        entity.setMiniprogramPagepath(miniprogramPagepath);
        entity.setMediaId(isBlank(incoming.getMediaId()) ? null : incoming.getMediaId().trim());
        entity.setSort(incoming.getSort() == null ? 0 : incoming.getSort());
        entity.setEnabled(incoming.getEnabled() == null ? 1 : incoming.getEnabled());

        if (entity.getParentId() == null) {
            long tops = menuRepository.findAllByAccountIdAndParentIdIsNull(entity.getAccountId())
                    .stream().filter(other -> !other.getId().equals(entity.getId())).count();
            if (tops >= 3) {
                throw new IllegalArgumentException("每个公众号最多 3 个一级菜单");
            }
        } else {
            long children = menuRepository.findAllByParentId(entity.getParentId())
                    .stream().filter(other -> !other.getId().equals(entity.getId())).count();
            if (children >= 5) {
                throw new IllegalArgumentException("每个一级菜单最多 5 个二级菜单");
            }
        }
        return menuRepository.save(entity);
    }

    /** 管理端菜单列表：单店量小不分页，平铺返回（parentId 表达层级），sort 升序、id 兜底 */
    public List<WxMenuEntity> listMenus(Long accountId) {
        if (accountId == null) {
            return menuRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "accountId", "sort", "id"));
        }
        return menuRepository.findAllByAccountIdOrderBySortAscIdAsc(accountId);
    }

    public void deleteMenu(Long id) {
        WxMenuEntity menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在"));
        if (!menuRepository.findAllByParentId(id).isEmpty()) {
            throw new IllegalArgumentException("该菜单下还有二级菜单，请先删除子菜单");
        }
        menuRepository.deleteById(id);
    }

    /** 菜单名权重：非 ASCII（汉字等）记 2 分、ASCII 记 1 分——对应微信「N 汉字或 2N 字母」口径 */
    private int menuNameWeight(String name) {
        return name.codePoints().map(cp -> cp > 127 ? 2 : 1).sum();
    }

    /** 菜单 URL 必填 + http(s) 前缀校验（view 与 view_miniprogram 备用网页共用） */
    private String requireMenuUrl(String raw, String requiredMessage) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException(requiredMessage);
        }
        String url = raw.trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("菜单地址必须以 http:// 或 https:// 开头");
        }
        return url;
    }

    /**
     * 菜单推送微信（设计稿 §4.3「推送」半边；出网走可配基址 wechat-api-base-url，测试指本地桩——D-OA4 先例）：
     * 取 token（cgi-bin/token, client_credential）→ POST cgi-bin/menu/create；只推 enabled=1（草稿不推送），
     * 父菜单带子菜单时只带 name+sub_button（微信忽略父级动作），无子菜单带自身类型字段。
     * 错误不静默：缺 AppSecret/无启用菜单/微信 errcode 非 0 一律拒绝并带 errcode 透传给管理端。
     * 已代决：不做 token 缓存——管理端手工触发、单店低频，先把出网与错误映射做对。
     */
    public long pushMenus(long accountId) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        if (isBlank(account.getAppSecret())) {
            throw new IllegalArgumentException("该账号未配置 AppSecret，无法推送微信");
        }
        List<Map<String, Object>> buttons = new ArrayList<>();
        for (WxMenuEntity top : menuRepository.findAllByAccountIdAndParentIdIsNullOrderBySortAscIdAsc(accountId)) {
            if (top.getEnabled() == null || top.getEnabled() != 1) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", top.getName());
            List<WxMenuEntity> children = menuRepository
                    .findAllByParentIdAndAccountIdOrderBySortAscIdAsc(top.getId(), accountId).stream()
                    .filter(child -> child.getEnabled() != null && child.getEnabled() == 1)
                    .toList();
            if (children.isEmpty()) {
                putMenuActionFields(node, top);
            } else {
                List<Map<String, Object>> subButtons = new ArrayList<>();
                for (WxMenuEntity child : children) {
                    Map<String, Object> sub = new LinkedHashMap<>();
                    sub.put("name", child.getName());
                    putMenuActionFields(sub, child);
                    subButtons.add(sub);
                }
                node.put("sub_button", subButtons);
            }
            buttons.add(node);
        }
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("没有启用的一级菜单可推送（草稿不推送微信）");
        }

        String tokenUrl = wechatApiBaseUrl + "/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + urlEncode(account.getAppId())
                + "&secret=" + urlEncode(account.getAppSecret());
        AccessTokenGrant grant = wechatRestTemplate.getForObject(URI.create(tokenUrl), AccessTokenGrant.class);
        if (grant == null || isBlank(grant.getAccessToken())) {
            throw new IllegalArgumentException("微信 access_token 获取失败（errcode=" + (grant == null ? null : grant.getErrcode())
                    + " errmsg=" + (grant == null ? null : grant.getErrmsg()) + "）");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String createUrl = wechatApiBaseUrl + "/cgi-bin/menu/create?access_token=" + urlEncode(grant.getAccessToken());
        Map<String, Object> resp = wechatRestTemplate
                .postForEntity(URI.create(createUrl), new HttpEntity<>(Map.of("button", buttons), headers), Map.class)
                .getBody();
        Object errcode = resp == null ? null : resp.get("errcode");
        if (!(errcode instanceof Number code) || code.intValue() != 0) {
            throw new IllegalArgumentException("微信菜单推送失败（errcode=" + errcode
                    + " errmsg=" + (resp == null ? null : resp.get("errmsg")) + "）");
        }
        return buttons.size();
    }

    /** 菜单动作字段：view 系带 url（小程序加 appid/pagepath），其余带 key */
    private void putMenuActionFields(Map<String, Object> node, WxMenuEntity menu) {
        node.put("type", menu.getMenuType());
        if ("view".equals(menu.getMenuType())) {
            node.put("url", menu.getUrl());
        } else if ("view_miniprogram".equals(menu.getMenuType())) {
            node.put("url", menu.getUrl());
            node.put("appid", menu.getMiniprogramAppid());
            node.put("pagepath", menu.getMiniprogramPagepath());
        } else {
            node.put("key", menu.getEventKey());
        }
    }

    /** 微信 cgi-bin/token 响应（client_credential 换取；errcode 非 0 时无 token） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AccessTokenGrant {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        private String accessToken;
        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        private Integer expiresIn;
        private Integer errcode;
        private String errmsg;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
