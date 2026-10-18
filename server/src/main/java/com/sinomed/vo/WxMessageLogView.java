package com.sinomed.vo;

import com.sinomed.entity.WxMessageLogEntity;
import lombok.Data;

import java.util.Date;

/**
 * 管理端消息流水只读视图（设计稿第一期交付物 4）：不含 dedupeKey（服务端排重内部字段，管理端无价值）。
 */
@Data
public class WxMessageLogView {
    public Long id;
    public Long accountId;
    /** in 入站 / out 出站回复 / reject 拒绝审计 */
    public String direction;
    public String msgType;
    public String fromUser;
    public String toUser;
    public String content;
    public Long msgId;
    /** ok / disabled / account_missing / bad_signature / duplicate */
    public String result;
    public Date createTime;

    public static WxMessageLogView from(WxMessageLogEntity entity) {
        WxMessageLogView view = new WxMessageLogView();
        view.id = entity.getId();
        view.accountId = entity.getAccountId();
        view.direction = entity.getDirection();
        view.msgType = entity.getMsgType();
        view.fromUser = entity.getFromUser();
        view.toUser = entity.getToUser();
        view.content = entity.getContent();
        view.msgId = entity.getMsgId();
        view.result = entity.getResult();
        view.createTime = entity.getCreateTime();
        return view;
    }
}
