package com.sinomed.service;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.entity.WxMenuEntity;
import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.service.impl.WxAccountAdminService;
import com.sinomed.service.impl.WxAutoReplyAdminService;
import com.sinomed.service.impl.WxMaterialAdminService;
import com.sinomed.service.impl.WxMenuAdminService;
import com.sinomed.service.impl.WxMessageLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 公众号接入门面（设计稿 docs/design/wechat-official-account.md §5/§6，LAY-001 拆分后）：
 * 按 关注域 拆到子服务——回调接入 {@link WxCallbackService}、粉丝绑定 {@link WxFanBindService}、
 * 网页授权 {@link WxOauthService}、消息流水 {@code WxMessageLogService}、自动回复规则
 * {@code WxAutoReplyAdminService}、菜单 {@code WxMenuAdminService}、素材 {@code WxMaterialAdminService}、
 * 账号管理 {@code WxAccountAdminService}（共享取 token 的 {@link WxAccountTokenResolver}）。
 * 本类只保留 Controller 用的公开签名做委托，行为不变；新增公众号能力直接挂对应子服务。
 */
@Component
public class WxOfficialAccountService {

    private final WxCallbackService callbackService;
    private final WxFanBindService fanBindService;
    private final WxOauthService oauthService;
    private final WxMessageLogService messageLogService;
    private final WxAutoReplyAdminService autoReplyAdminService;
    private final WxMenuAdminService menuAdminService;
    private final WxMaterialAdminService materialAdminService;
    private final WxAccountAdminService accountAdminService;

    public WxOfficialAccountService(WxCallbackService callbackService,
                                    WxFanBindService fanBindService,
                                    WxOauthService oauthService,
                                    WxMessageLogService messageLogService,
                                    WxAutoReplyAdminService autoReplyAdminService,
                                    WxMenuAdminService menuAdminService,
                                    WxMaterialAdminService materialAdminService,
                                    WxAccountAdminService accountAdminService) {
        this.callbackService = callbackService;
        this.fanBindService = fanBindService;
        this.oauthService = oauthService;
        this.messageLogService = messageLogService;
        this.autoReplyAdminService = autoReplyAdminService;
        this.menuAdminService = menuAdminService;
        this.materialAdminService = materialAdminService;
        this.accountAdminService = accountAdminService;
    }

    /** GET URL 验证（回调域）：通过原样返回 echostr */
    public String verifyUrl(long accountId, String signature, String timestamp, String nonce, String echostr) {
        return callbackService.verifyUrl(accountId, signature, timestamp, nonce, echostr);
    }

    /** POST 消息推送（回调域）：验签/解密/排重/落流水/路由，回被动回复或 "success" */
    public String onMessage(long accountId, String signature, String timestamp, String nonce,
                            String msgSignature, String xmlBody) {
        return callbackService.onMessage(accountId, signature, timestamp, nonce, msgSignature, xmlBody);
    }

    /** 能力矩阵（账号域）：account_type + verified 驱动 */
    public Map<String, Object> capabilities(long accountId) {
        return accountAdminService.capabilities(accountId);
    }

    /** 绑定取码（粉丝域）：sms_codes 一次性 5 分钟验证码桩 */
    public String issueBindCode(long accountId, String phone) {
        return fanBindService.issueBindCode(accountId, phone);
    }

    /** 粉丝↔顾客绑定（粉丝域）：手机号+验证码双因子，每次尝试落审计 */
    public String bind(long accountId, String openid, String phone, String smsCode) {
        return fanBindService.bind(accountId, openid, phone, smsCode);
    }

    /** 管理端解绑（粉丝域）：清 customer_id + 审计 */
    public String unbind(long accountId, String openid) {
        return fanBindService.unbind(accountId, openid);
    }

    /** 流水顾客姓名解析（粉丝域）：已绑定粉丝回顾客姓名 */
    public String resolveCustomerName(Long accountId, String fromUser) {
        return fanBindService.resolveCustomerName(accountId, fromUser);
    }

    /** 网页授权发起（OAuth 域）：state 无状态 HMAC 防伪 */
    public String oauthAuthorizeUrl(long accountId) {
        return oauthService.oauthAuthorizeUrl(accountId);
    }

    /** 网页授权回调（OAuth 域）：state 验签 → code 换 openid → 一次性 ticket */
    public String oauthCallbackRedirectUrl(long accountId, String code, String state) {
        return oauthService.oauthCallbackRedirectUrl(accountId, code, state);
    }

    /** H5 ticket 兑换 openid（OAuth 域，一次性） */
    public String redeemTicket(long accountId, String ticket) {
        return oauthService.redeemTicket(accountId, ticket);
    }

    /** 管理端只读流水分页（流水域） */
    public Page<WxMessageLogEntity> pageLogs(Long accountId, String fromUser, String direction, String msgType,
                                             String result, String startTime, String endTime, Pageable pageable) {
        return messageLogService.pageLogs(accountId, fromUser, direction, msgType, result, startTime, endTime, pageable);
    }

    /** 管理端规则保存（规则域） */
    public WxAutoReplyEntity saveAutoReply(WxAutoReplyEntity incoming) {
        return autoReplyAdminService.saveAutoReply(incoming);
    }

    /** 管理端规则分页（规则域） */
    public Page<WxAutoReplyEntity> pageAutoReplies(Long accountId, String ruleType, Integer enabled,
                                                   Pageable pageable) {
        return autoReplyAdminService.pageAutoReplies(accountId, ruleType, enabled, pageable);
    }

    public void deleteAutoReply(Long id) {
        autoReplyAdminService.deleteAutoReply(id);
    }

    /** 管理端菜单保存（菜单域）：两级校验/容量/加权名长 */
    public WxMenuEntity saveMenu(WxMenuEntity incoming) {
        return menuAdminService.saveMenu(incoming);
    }

    /** 管理端菜单列表（菜单域） */
    public List<WxMenuEntity> listMenus(Long accountId) {
        return menuAdminService.listMenus(accountId);
    }

    public void deleteMenu(Long id) {
        menuAdminService.deleteMenu(id);
    }

    /** 菜单推送微信（菜单域出网半边）：cgi-bin/token → cgi-bin/menu/create */
    public long pushMenus(long accountId) {
        return menuAdminService.pushMenus(accountId);
    }

    /** 管理端素材台账分页（素材域） */
    public Page<WxMaterialEntity> pageMaterials(Long accountId, String materialType,
                                                String startTime, String endTime, Pageable pageable) {
        return materialAdminService.pageMaterials(accountId, materialType, startTime, endTime, pageable);
    }

    /** 管理端取素材本地文件（素材域）：类型随子服务（WxMaterialAdminService.MaterialFile） */
    public WxMaterialAdminService.MaterialFile loadMaterialFile(long id) {
        return materialAdminService.loadMaterialFile(id);
    }

    /** 本地素材上传（素材域）：永久台账+本仓存储 */
    public WxMaterialEntity uploadLocalMaterial(long accountId, MultipartFile file) {
        return materialAdminService.uploadLocalMaterial(accountId, file);
    }

    /** 删除本地治理素材（素材域） */
    public void deleteLocalMaterial(long id) {
        materialAdminService.deleteLocalMaterial(id);
    }

    /** 远端上传永久素材（素材域出网半边）：WxJava SDK add_material */
    public WxMaterialEntity uploadRemoteMaterial(long accountId, MultipartFile file,
                                                 String videoTitle, String videoIntroduction) {
        return materialAdminService.uploadRemoteMaterial(accountId, file, videoTitle, videoIntroduction);
    }

    /** 管理端账号列表（账号域） */
    public List<WxAccountEntity> listAccounts() {
        return accountAdminService.listAccounts();
    }

    /** 管理端账号保存（账号域）：凭据加密落库，变更后失效 SDK/路由缓存 */
    public WxAccountEntity saveAccount(WxAccountEntity incoming) {
        return accountAdminService.saveAccount(incoming);
    }
}
