package com.sinomed;

import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.api.impl.WxMpServiceHttpClientImpl;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WxJava SDK 消息参与契约测试（设计稿 §5.4/§5.5）：本仓只用 SDK 做「XML 解析 + 消息路由」，
 * 路由规则必须同步（微信 5s 窗口）、SDK 内置排重必须 no-op（排重由 wx_message_logs.dedupe_key DB
 * 承担）；出站用 SDK builder 产出 XML，密封只识别 &lt;xml&gt; 明文。纯本地、不触网、凭据无关。
 */
class WxSdkContractTest {

    private WxMpServiceHttpClientImpl service;

    @BeforeEach
    void setUp() {
        service = new WxMpServiceHttpClientImpl();
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId("wxCONTRACT1");
        config.setSecret("s");
        config.setToken("t");
        service.setWxMpConfigStorage(config);
    }

    @Test
    void fromXmlParsesTheFieldsTheCallbackFlowReliesOn() {
        WxMpXmlMessage msg = WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>1760000001</CreateTime><MsgType><![CDATA[event]]></MsgType>"
                        + "<Event><![CDATA[subscribe]]></Event>"
                        + "<EventKey><![CDATA[qr_1]]></EventKey></xml>");
        assertEquals("event", msg.getMsgType());
        assertEquals("oFAN1", msg.getFromUser());
        assertEquals("gh_test", msg.getToUser());
        assertEquals("subscribe", msg.getEvent());
        assertEquals("qr_1", msg.getEventKey());
        assertEquals(1760000001L, msg.getCreateTime());
        assertNull(msg.getMsgId(), "事件无 MsgId");
    }

    @Test
    void fromXmlParsesTextAndImageWithCdataAndLongFields() {
        WxMpXmlMessage text = WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA[营业时间]]></Content><MsgId>1234567890123456</MsgId></xml>");
        assertEquals("text", text.getMsgType());
        assertEquals("营业时间", text.getContent());
        assertEquals(1234567890123456L, text.getMsgId());

        WxMpXmlMessage image = WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>1760000002</CreateTime><MsgType><![CDATA[image]]></MsgType>"
                        + "<MediaId><![CDATA[media_1]]></MediaId>"
                        + "<PicUrl><![CDATA[https://mmbiz.qpic.cn/a.jpg]]></PicUrl></xml>");
        assertEquals("image", image.getMsgType());
        assertEquals("media_1", image.getMediaId());
        assertEquals("https://mmbiz.qpic.cn/a.jpg", image.getPicUrl());
    }

    @Test
    void outTextToXmlProducesPlainXmlEnvelopeForSealReply() {
        WxMpXmlOutMessage out = WxMpXmlOutMessage.TEXT()
                .toUser("oFAN1").fromUser("gh_test").content("收到，稍后回复您").build();
        assertNotNull(out.getCreateTime(), "builder 应填充服务端时间戳");
        String xml = out.toXml();
        assertTrue(xml.startsWith("<xml>"), "出站应为明文 xml 信封（sealReply 依赖该前缀）：" + xml);
        assertTrue(xml.contains("<Content><![CDATA[收到，稍后回复您]]></Content>"), "正文走 CDATA：" + xml);
    }

    @Test
    void syncRuleExecutesOnCallerThreadForFiveSecondWindow() {
        WxMpMessageRouter router = new WxMpMessageRouter(service);
        Thread[] ranOn = new Thread[1];
        router.rule().async(false).msgType("text").handler((message, ctx, svc, session) -> {
            ranOn[0] = Thread.currentThread();
            return WxMpXmlOutMessage.TEXT().toUser(message.getFromUser())
                    .fromUser(message.getToUser()).content("回复").build();
        }).end();

        WxMpXmlOutMessage out = router.route(WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA[x]]></Content><MsgId>1</MsgId></xml>"));
        assertNotNull(out, "同步规则应返回出站消息");
        assertSame(Thread.currentThread(), ranOn[0], "async(false) 必须复用回调线程（5s 窗口约束）");
    }

    @Test
    void noOpDuplicateCheckerDoesNotSuppressRouterDedupeDoubleBooking() {
        WxMpMessageRouter router = new WxMpMessageRouter(service);
        router.setMessageDuplicateChecker(id -> false);
        router.rule().async(false).msgType("text").handler((message, ctx, svc, session) ->
                WxMpXmlOutMessage.TEXT().toUser(message.getFromUser())
                        .fromUser(message.getToUser()).content("回复").build()).end();

        WxMpXmlMessage msg = WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA[x]]></Content><MsgId>42</MsgId></xml>");
        assertNotNull(router.route(msg), "同一消息首次应路由");
        assertNotNull(router.route(msg), "no-op 排重器不得二次抑制（DB 排重是唯一权威）");
    }

    @Test
    void rulesMatchFirstWinsSoSpecificBeforeCatchAll() {
        WxMpMessageRouter router = new WxMpMessageRouter(service);
        router.rule().async(false).msgType("text").content("营业时间").handler((message, ctx, svc, session) ->
                WxMpXmlOutMessage.TEXT().toUser(message.getFromUser())
                        .fromUser(message.getToUser()).content("9:00-18:00").build()).end();
        router.rule().async(false).msgType("text").handler((message, ctx, svc, session) ->
                WxMpXmlOutMessage.TEXT().toUser(message.getFromUser())
                        .fromUser(message.getToUser()).content("兜底").build()).end();

        WxMpXmlMessage hit = WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>1</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA[营业时间]]></Content><MsgId>1</MsgId></xml>");
        assertTrue(router.route(hit).toXml().contains("9:00-18:00"), "细规则优先于兜底");

        WxMpXmlMessage miss = WxMpXmlMessage.fromXml(
                "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                        + "<FromUserName><![CDATA[oFAN1]]></FromUserName>"
                        + "<CreateTime>2</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA[别的]]></Content><MsgId>2</MsgId></xml>");
        assertTrue(router.route(miss).toXml().contains("兜底"), "未命中关键词应落兜底");
    }
}
