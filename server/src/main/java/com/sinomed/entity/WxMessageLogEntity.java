package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 公众号消息全量流水（wx_message_logs，设计稿 §5.4 第 4/6 步）：direction=in/out 全量收发，
 * 含拒绝审计（direction=reject，result 标因：disabled/account_missing/bad_signature/duplicate）。
 * dedupe_key 唯一——微信 5s 超时重发 3 次，按 MsgId（消息）或 FromUserName+CreateTime+Event（事件）排重。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_message_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_wx_log_dedupe", columnNames = "dedupe_key"),
        indexes = {
                @Index(name = "idx_wx_log_account_direction", columnList = "account_id, direction"),
                @Index(name = "idx_wx_log_from", columnList = "from_user")
        })
@EntityListeners(AuditingEntityListener.class)
public class WxMessageLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    /** in 入站 / out 出站回复 / reject 拒绝审计（验签失败/模块关/账号停用/重复） */
    @Column(name = "direction", nullable = false)
    private String direction;

    /** text/event/image/…（reject 行记原始 MsgType 或 none） */
    @Column(name = "msg_type")
    private String msgType;

    @Column(name = "from_user")
    private String fromUser;

    @Column(name = "to_user")
    private String toUser;

    /** 文本内容或事件名（截断留痕） */
    @Column(name = "content")
    private String content;

    /** 微信侧 MsgId（事件无） */
    @Column(name = "msg_id")
    private Long msgId;

    /** 排重键：消息=MsgId，事件=FromUserName:CreateTime:Event */
    @Column(name = "dedupe_key", unique = true)
    private String dedupeKey;

    /** ok / disabled / account_missing / bad_signature / duplicate */
    @Column(name = "result", columnDefinition = "varchar(32) default 'ok'")
    private String result;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
}
