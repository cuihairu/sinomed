package com.sinomed.controller;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.service.WxOfficialAccountService;
import com.sinomed.vo.MessageView;
import com.sinomed.vo.PageResp;
import com.sinomed.vo.WxAccountView;
import com.sinomed.vo.WxAutoReplyView;
import com.sinomed.vo.WxBindRequest;
import com.sinomed.vo.WxMessageLogView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 公众号接入端点（第一期骨架，设计稿 docs/design/wechat-official-account.md §5/§6）。
 * 回调端点免登录（微信服务器无 JWT），来源可信靠签名验证（SecurityConfig permitAll）；
 * 能力探测属管理端，走常规 JWT。
 */
@RestController
@RequestMapping("/api/v1/wx")
@RequiredArgsConstructor
@Tag(name = "公众号", description = "微信公众号消息接入与能力探测（第一期）")
public class WxOfficialAccountController {

    private final WxOfficialAccountService wxOfficialAccountService;

    @Operation(summary = "回调 URL 验证", description = "公众号后台保存服务器配置时的 GET 验签：通过原样返回 echostr，"
            + "否则拒处理（回 success/空）；模块关闭或账号停用也回 success 不静默（落审计）")
    @GetMapping(value = "/callback/{accountId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String verify(@PathVariable("accountId") long accountId,
                         @RequestParam(value = "signature", required = false) String signature,
                         @RequestParam(value = "timestamp", required = false) String timestamp,
                         @RequestParam(value = "nonce", required = false) String nonce,
                         @RequestParam(value = "echostr", required = false) String echostr) {
        return wxOfficialAccountService.verifyUrl(accountId, signature, timestamp, nonce, echostr);
    }

    @Operation(summary = "消息推送回调", description = "微信服务器的用户消息/事件推送（明文/兼容/安全模式）："
            + "明文走三段 signature 验签；含 Encrypt 报文走 msg_signature 四段验签 + AES 解密（设计稿 §5.3），"
            + "安全模式回复包加密信封。按 MsgId 排重、全量落流水、subscribe/unsubscribe 维护粉丝、"
            + "关键词/欢迎语/兜底被动回复；无回复回 success（微信 5s 约束）")
    @PostMapping(value = "/callback/{accountId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String onMessage(@PathVariable("accountId") long accountId,
                            @RequestParam(value = "signature", required = false) String signature,
                            @RequestParam(value = "timestamp", required = false) String timestamp,
                            @RequestParam(value = "nonce", required = false) String nonce,
                            @RequestParam(value = "msg_signature", required = false) String msgSignature,
                            @RequestBody(required = false) byte[] xmlBody) {
        // 微信推送 Content-Type 常不带 charset，String 反序列化默认 ISO-8859-1 会毁中文——
        // 收原始字节后按微信协议固定 UTF-8 解码（明文模式报文即 UTF-8）
        String xml = xmlBody == null ? null : new String(xmlBody, StandardCharsets.UTF_8);
        return wxOfficialAccountService.onMessage(accountId, signature, timestamp, nonce, msgSignature, xml);
    }

    @Operation(summary = "账号能力探测", description = "按 account_type + verified 返回能力矩阵（菜单/客服/群发/模板消息/素材/回调/网页授权），"
            + "管理端据此渲染降级入口（未认证订阅号隐藏菜单与群发）；需登录")
    @GetMapping("/capabilities/{accountId}")
    public Map<String, Object> capabilities(@PathVariable("accountId") long accountId) {
        return wxOfficialAccountService.capabilities(accountId);
    }

    @Operation(summary = "粉丝绑定取码", description = "粉丝 H5 侧发码：复用 Kiosk 短信通道桩（sms_codes，一次性、5 分钟有效），"
            + "无真实短信网关、发码只落库与日志；模块关/账号停用拒绝并审计；免登录（粉丝侧，靠手机号+验证码双因子自证）")
    @PostMapping(value = "/bind/{accountId}/code", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageView issueBindCode(@PathVariable("accountId") long accountId,
                                     @RequestBody WxBindRequest request) {
        return new MessageView(wxOfficialAccountService.issueBindCode(accountId, request.getPhone()));
    }

    @Operation(summary = "网页授权发起", description = "生成 snsapi_base 授权地址并 302 到 open.weixin.qq.com："
            + "state 为 HMAC-SHA1 无状态防伪（10 分钟），redirect_uri 取 oauth-redirect-base 配置。"
            + "模块关/账号停用/未配回跳基址明确 400 并落审计；免登录（粉丝侧）")
    @GetMapping("/oauth/{accountId}/start")
    public ResponseEntity<Void> oauthStart(@PathVariable("accountId") long accountId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, wxOfficialAccountService.oauthAuthorizeUrl(accountId))
                .build();
    }

    @Operation(summary = "网页授权回调", description = "state 验签 → code 换 openid（出网可配基址）→ 发一次性 ticket →"
            + "302 回 H5 绑定页（#ticket=...，openid 不下发前端）。state 伪造/换取失败明确 400 并落审计；免登录")
    @GetMapping("/oauth/{accountId}/callback")
    public ResponseEntity<Void> oauthCallback(@PathVariable("accountId") long accountId,
                                              @RequestParam(value = "code", required = false) String code,
                                              @RequestParam(value = "state", required = false) String state) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION,
                        wxOfficialAccountService.oauthCallbackRedirectUrl(accountId, code, state))
                .build();
    }

    @Operation(summary = "粉丝绑定顾客档案", description = "H5 授权拿 openid + 手机号 + 短信验证码双因子（复用 Kiosk /openid-bind 口径）："
            + "openid 也可由网页授权一次性 ticket 兑换代替（§5.6）；校验通过写 wx_fans.customer_id，"
            + "每次尝试（含拒绝）落 wx_binding_logs；同档案重复绑定幂等回执；免登录（粉丝侧）")
    @PostMapping(value = "/bind/{accountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageView bind(@PathVariable("accountId") long accountId,
                            @RequestBody WxBindRequest request) {
        String openid = request.getOpenid();
        if ((openid == null || openid.isBlank()) && request.getTicket() != null && !request.getTicket().isBlank()) {
            openid = wxOfficialAccountService.redeemTicket(accountId, request.getTicket());
        }
        return new MessageView(wxOfficialAccountService.bind(
                accountId, openid, request.getPhone(), request.getSmsCode()));
    }

    @Operation(summary = "管理端解绑粉丝", description = "清 wx_fans.customer_id 并落审计（设计稿 §4.7）；"
            + "顾客取关不自动解绑，换人关注属线下场景走管理端；需登录")
    @PostMapping("/fans/{accountId}/unbind")
    public MessageView unbind(@PathVariable("accountId") long accountId,
                              @RequestBody WxBindRequest request) {
        return new MessageView(wxOfficialAccountService.unbind(accountId, request.getOpenid()));
    }

    @Operation(summary = "消息流水只读列表", description = "管理端按粉丝/方向/类型/结果/时间窗过滤收发全量流水（含 reject 审计），"
            + "默认最新在前；只读不做对话式 UI，不依赖模块开关；需登录")
    @GetMapping("/message-logs")
    public PageResp<WxMessageLogView> messageLogs(@RequestParam int current,
                                                  @RequestParam int pageSize,
                                                  @RequestParam(required = false) Long accountId,
                                                  @RequestParam(required = false) String fromUser,
                                                  @RequestParam(required = false) String direction,
                                                  @RequestParam(required = false) String msgType,
                                                  @RequestParam(required = false) String result,
                                                  @RequestParam(required = false) String startTime,
                                                  @RequestParam(required = false) String endTime) {
        // 最新在前：createTime 倒序为主，id 倒序兜底同刻并列（审计毫秒可能同值）
        PageRequest page = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 1,
                Sort.by(Sort.Direction.DESC, "createTime", "id"));
        Page<WxMessageLogEntity> logs = wxOfficialAccountService.pageLogs(
                accountId, fromUser, direction, msgType, result, startTime, endTime, page);
        List<WxMessageLogView> data = new ArrayList<>();
        for (WxMessageLogEntity row : logs) {
            WxMessageLogView view = WxMessageLogView.from(row);
            view.customerName = wxOfficialAccountService.resolveCustomerName(row.getAccountId(), row.getFromUser());
            data.add(view);
        }
        PageResp<WxMessageLogView> resp = new PageResp<>();
        resp.data = data;
        resp.pages = logs.getTotalPages();
        resp.total = logs.getTotalElements();
        resp.success = true;
        return resp;
    }

    // ---------- 管理端规则 CRUD（设计稿第二期「管理端规则编辑」服务端半边；需登录，不依赖模块开关） ----------

    @Operation(summary = "账号列表", description = "管理端选账号用（规则/粉丝页），单店量小不分页 id 升序；"
            + "绝不下发密钥列（appSecret/token/aesKey），要改密钥走部署配置；需登录")
    @GetMapping("/accounts")
    public List<WxAccountView> accounts() {
        List<WxAccountView> data = new ArrayList<>();
        for (WxAccountEntity account : wxOfficialAccountService.listAccounts()) {
            data.add(WxAccountView.from(account));
        }
        return data;
    }

    @Operation(summary = "自动回复规则分页", description = "按账号/类型/启用态过滤，默认 priority 升序（小者先试）；需登录")
    @GetMapping("/auto-replies/page")
    public PageResp<WxAutoReplyView> pageAutoReplies(@RequestParam int current,
                                                     @RequestParam int pageSize,
                                                     @RequestParam(required = false) Long accountId,
                                                     @RequestParam(required = false) String ruleType,
                                                     @RequestParam(required = false) Integer enabled) {
        PageRequest page = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 1,
                Sort.by(Sort.Direction.ASC, "priority", "id"));
        Page<WxAutoReplyEntity> rules = wxOfficialAccountService.pageAutoReplies(accountId, ruleType, enabled, page);
        PageResp<WxAutoReplyView> resp = new PageResp<>();
        resp.data = new ArrayList<>();
        for (WxAutoReplyEntity row : rules) {
            resp.data.add(WxAutoReplyView.from(row));
        }
        resp.pages = rules.getTotalPages();
        resp.total = rules.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "创建自动回复规则", description = "ruleType=subscribe/keyword/fallback；keyword 型必填关键词，"
            + "其它型关键词会被清空；subscribe/fallback 同账号同时最多一条启用；需登录")
    @PostMapping("/auto-replies")
    public WxAutoReplyView createAutoReply(@RequestBody WxAutoReplyView view) {
        view.setId(null);
        return WxAutoReplyView.from(wxOfficialAccountService.saveAutoReply(view.toEntity()));
    }

    @Operation(summary = "更新自动回复规则", description = "按 id 全量更新；需登录")
    @PutMapping("/auto-replies")
    public WxAutoReplyView updateAutoReply(@RequestBody WxAutoReplyView view) {
        if (view.getId() == null) {
            throw new IllegalArgumentException("规则Id不能为空");
        }
        return WxAutoReplyView.from(wxOfficialAccountService.saveAutoReply(view.toEntity()));
    }

    @Operation(summary = "删除自动回复规则", description = "按 id 删除；需登录")
    @DeleteMapping("/auto-replies/{id}")
    public MessageView deleteAutoReply(@PathVariable Long id) {
        wxOfficialAccountService.deleteAutoReply(id);
        return new MessageView("删除成功");
    }
}
