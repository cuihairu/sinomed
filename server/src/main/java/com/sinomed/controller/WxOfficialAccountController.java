package com.sinomed.controller;

import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.service.WxOfficialAccountService;
import com.sinomed.vo.PageResp;
import com.sinomed.vo.WxMessageLogView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "消息推送回调", description = "微信服务器的用户消息/事件推送（明文模式）：验签、按 MsgId 排重、"
            + "全量落流水、subscribe/unsubscribe 维护粉丝、关键词/欢迎语/兜底被动回复；无回复回 success（微信 5s 约束）")
    @PostMapping(value = "/callback/{accountId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String onMessage(@PathVariable("accountId") long accountId,
                            @RequestParam(value = "signature", required = false) String signature,
                            @RequestParam(value = "timestamp", required = false) String timestamp,
                            @RequestParam(value = "nonce", required = false) String nonce,
                            @RequestBody(required = false) byte[] xmlBody) {
        // 微信推送 Content-Type 常不带 charset，String 反序列化默认 ISO-8859-1 会毁中文——
        // 收原始字节后按微信协议固定 UTF-8 解码（明文模式报文即 UTF-8）
        String xml = xmlBody == null ? null : new String(xmlBody, StandardCharsets.UTF_8);
        return wxOfficialAccountService.onMessage(accountId, signature, timestamp, nonce, xml);
    }

    @Operation(summary = "账号能力探测", description = "按 account_type + verified 返回能力矩阵（菜单/客服/群发/模板消息/素材/回调/网页授权），"
            + "管理端据此渲染降级入口（未认证订阅号隐藏菜单与群发）；需登录")
    @GetMapping("/capabilities/{accountId}")
    public Map<String, Object> capabilities(@PathVariable("accountId") long accountId) {
        return wxOfficialAccountService.capabilities(accountId);
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
            data.add(WxMessageLogView.from(row));
        }
        PageResp<WxMessageLogView> resp = new PageResp<>();
        resp.data = data;
        resp.pages = logs.getTotalPages();
        resp.total = logs.getTotalElements();
        resp.success = true;
        return resp;
    }
}
