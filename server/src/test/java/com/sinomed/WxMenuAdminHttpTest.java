package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 管理端菜单编辑 HTTP 回归（设计稿 §4.3「编辑/草稿」本地半边；推送微信半边压凭据）：
 * 两级结构 + 微信协议校验（一级仅 view/click、一二级名字数按「4/8 汉字或 2 倍字母」加权口径、
 * 每号 ≤3 一级、每级 ≤5 二级、view/click/view_miniprogram 必填字段）+ 未登录 401。
 * 与规则/流水同口径：只读+编辑不依赖模块开关，先建后激活。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-menu-admin-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true"
})
class WxMenuAdminHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String token;

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxMenuRepository menuRepository;

    private Long accountId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @BeforeEach
    void seed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        token = (String) resp.getBody().get("token");

        accountRepository.deleteAll();
        menuRepository.deleteAll();
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxMENU1");
        account.setAccountName("菜单测试号");
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();
    }

    private Map<String, Object> post(Map<String, Object> body) {
        HttpHeaders headers = auth();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url("/api/v1/wx/menus"), HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class).getBody();
    }

    private void postExpect400(Map<String, Object> body) {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, () -> post(body),
                "应拒绝：" + body);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "应 400：" + body);
    }

    private Map<String, Object> topView(String name, int sort, String url) {
        return Map.of("accountId", accountId, "name", name, "menuType", "view",
                "url", url, "sort", sort);
    }

    private Map<String, Object> childClick(String name, long parentId, int sort) {
        return Map.of("accountId", accountId, "name", name, "menuType", "click",
                "parentId", parentId, "eventKey", "OPEN_" + name, "sort", sort);
    }

    @Test
    void menuCrudRoundTripListsInSortOrder() {
        Map<String, Object> top = post(topView("服务", 0, "https://clinic.example.com/svc"));
        Long topId = ((Number) top.get("id")).longValue();
        assertEquals("view", top.get("menuType"));
        assertEquals("https://clinic.example.com/svc", top.get("url"));
        assertEquals(0, ((Number) top.get("sort")).intValue());
        assertEquals(1, ((Number) top.get("enabled")).intValue(), "缺省应为启用（推送口径由二期定）");
        assertEquals(accountId, ((Number) top.get("accountId")).longValue(), "应落当前账号");
        assertNull(top.get("parentId"), "顶级菜单无父 id");

        Map<String, Object> child = post(childClick("症状自测", topId, 1));
        Long childId = ((Number) child.get("id")).longValue();
        assertEquals(topId, ((Number) child.get("parentId")).longValue());
        assertEquals("OPEN_症状自测", child.get("eventKey"));

        // 更新（改顺序 + 停用=草稿）
        Map<String, Object> upd = Map.of("id", childId, "accountId", accountId, "name", "自测",
                "menuType", "click", "parentId", topId, "eventKey", "OPEN_自测", "sort", 0, "enabled", 0);
        HttpHeaders headers = auth();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> updated = rest.exchange(url("/api/v1/wx/menus"), HttpMethod.PUT,
                new HttpEntity<>(upd, headers), Map.class).getBody();
        assertEquals(0, ((Number) updated.get("enabled")).intValue(), "enabled=0 表示草稿");
        assertEquals("OPEN_自测", updated.get("eventKey"));

        // 列表：同账号按 sort 升序、id 兜底，顶级在前
        HttpHeaders listHeaders = auth();
        List<Map<String, Object>> list = rest.exchange(
                url("/api/v1/wx/menus?accountId=" + accountId), HttpMethod.GET,
                new HttpEntity<>(listHeaders), List.class).getBody();
        assertEquals(2, list.size(), "应列出 2 条菜单：" + list);
        assertEquals(topId, ((Number) list.get(0).get("id")).longValue(), "sort 同值时按 id 兜底");

        // 删除（父菜单存在时应级联拒绝或级联删除——按设计稿草稿态约束取「父菜单未删不得删子」更严）
        assertThrows(HttpClientErrorException.class, () -> rest.exchange(
                url("/api/v1/wx/menus/" + topId), HttpMethod.DELETE, new HttpEntity<>(listHeaders), Map.class),
                "有子菜单时删除父菜单必须拒绝");
        rest.exchange(url("/api/v1/wx/menus/" + childId), HttpMethod.DELETE, new HttpEntity<>(listHeaders), Map.class);
        rest.exchange(url("/api/v1/wx/menus/" + topId), HttpMethod.DELETE, new HttpEntity<>(listHeaders), Map.class);
        List<Map<String, Object>> after = rest.exchange(
                url("/api/v1/wx/menus?accountId=" + accountId), HttpMethod.GET,
                new HttpEntity<>(listHeaders), List.class).getBody();
        assertEquals(0, after.size(), "删除后应为空：" + after);
    }

    @Test
    void rejectsInvalidEnumNameLengthAndStructure() {
        // 枚举值越界
        postExpect400(Map.of("accountId", accountId, "name", "菜单", "menuType", "unknown_type"));
        // 一级仅 view/click（扫码/媒体类只能二级）
        postExpect400(Map.of("accountId", accountId, "name", "拍照", "menuType", "pic_sysphoto"));
        // 一级菜单名 ≤4 汉字（8 个字母等效）；二级 ≤8 汉字（16 字母等效）
        postExpect400(topView("一二三四五", 0, "https://x.cn/"));
        postExpect400(topView("ABCDEFGHI", 0, "https://x.cn/"));
        Map<String, Object> okTop = post(topView("ABCDEFGH", 0, "https://x.cn/a"));
        Long okTopId = ((Number) okTop.get("id")).longValue();
        postExpect400(childClick("一二三四五六七八九", okTopId, 1));

        // 结构：父菜单不存在 / 父菜单跨账号 / 父菜单本身是二级
        postExpect400(Map.of("accountId", accountId, "name", "无父", "menuType", "click",
                "parentId", 999999L, "eventKey", "X", "sort", 1));
        Map<String, Object> child = post(childClick("子A", okTopId, 1));
        Long childId = ((Number) child.get("id")).longValue();
        postExpect400(Map.of("accountId", accountId, "name", "孙子", "menuType", "click",
                "parentId", childId, "eventKey", "X2", "sort", 1));

        // 容量：每号 ≤3 一级；每级 ≤5 二级（第二级容量测试用干净的一级菜单，避免与前面的子菜单计数纠缠）
        post(topView("ABC", 1, "https://x.cn/b"));
        Map<String, Object> capTop = post(topView("DEF", 2, "https://x.cn/c"));
        postExpect400(topView("GHI", 3, "https://x.cn/d"));
        long capTopId = ((Number) capTop.get("id")).longValue();
        for (int i = 1; i <= 5; i++) {
            post(childClick("二级" + i, capTopId, i));
        }
        postExpect400(childClick("第二级六", capTopId, 6));
    }

    @Test
    void typeSpecificRequiredFieldsAreEnforcedAndCleared() {
        // 缺菜单名（顶级 view，避开类型必填干扰）
        postExpect400(Map.of("accountId", accountId, "menuType", "view", "url", "https://x.cn/"));
        postExpect400(Map.of("accountId", accountId, "name", "无地址", "menuType", "view"));
        postExpect400(Map.of("accountId", accountId, "name", "坏地址", "menuType", "view",
                "url", "ftp://nope.cn"));

        // 一级仅 view/click，故 click/view_miniprogram 的必填校验放在二级位上验证
        Map<String, Object> parent = post(topView("菜单", 0, "https://x.cn/p"));
        long parentId = ((Number) parent.get("id")).longValue();
        postExpect400(Map.of("accountId", accountId, "name", "无key", "menuType", "click",
                "parentId", parentId));
        postExpect400(Map.of("accountId", accountId, "name", "缺appid", "menuType", "view_miniprogram",
                "parentId", parentId, "url", "https://x.cn/mp", "miniprogramPagepath", "pages/index/index"));
        postExpect400(Map.of("accountId", accountId, "name", "缺page", "menuType", "view_miniprogram",
                "parentId", parentId, "url", "https://x.cn/mp", "miniprogramAppid", "wx123"));
        postExpect400(Map.of("accountId", accountId, "name", "缺备用页", "menuType", "view_miniprogram",
                "parentId", parentId, "miniprogramAppid", "wx123", "miniprogramPagepath", "pages/index/index"));

        // 扫码/媒体/位置类同样需要事件 key（微信协议：除 view 系外都要 key），缺失拒绝、补齐放行
        postExpect400(Map.of("accountId", accountId, "name", "无key2", "menuType", "scancode_push",
                "parentId", parentId));
        Map<String, Object> scan = post(Map.of("accountId", accountId, "name", "扫码", "menuType", "scancode_push",
                "parentId", parentId, "eventKey", "SCAN_1"));
        assertEquals("SCAN_1", scan.get("eventKey"));

        // 视图类型之外的 url 不生效（微信忽略），保存时强制清空避免误导
        Map<String, Object> click = Map.of("accountId", accountId, "name", "活动", "menuType", "click",
                "parentId", parentId, "eventKey", "OPEN_EVENT",
                "url", "https://x.cn/should-be-cleared");
        HttpHeaders headers = auth();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> saved = rest.exchange(url("/api/v1/wx/menus"), HttpMethod.POST,
                new HttpEntity<>(click, headers), Map.class).getBody();
        assertNull(saved.get("url"), "非 view 类型应清空 url");
        assertEquals("OPEN_EVENT", saved.get("eventKey"));
    }

    @Test
    void unauthenticatedIsRejected() {
        for (String path : new String[]{"/api/v1/wx/menus?accountId=" + accountId,
                "/api/v1/wx/menus/1"}) {
            HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                    () -> rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class),
                    "未登录应拒绝：" + path);
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode(), "应 401：" + path);
        }
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/menus"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("name", "无账号"), new HttpHeaders()), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }
}
