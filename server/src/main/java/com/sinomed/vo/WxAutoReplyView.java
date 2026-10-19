package com.sinomed.vo;

import com.sinomed.entity.WxAutoReplyEntity;
import lombok.Data;

import java.util.Date;

/**
 * 管理端自动回复规则视图（设计稿第二期「管理端规则编辑」服务端半边）：
 * 兼作创建/更新入参与查询出参；校验与 normalize（keyword 清空、matchType/replyType 缺省）在服务层。
 */
@Data
public class WxAutoReplyView {
    public Long id;
    public Long accountId;
    /** subscribe（欢迎语）/ keyword（关键词）/ fallback（兜底） */
    public String ruleType;
    /** keyword 型必填；其它型服务层强制置空，不污染匹配 */
    public String keyword;
    /** exact 全等 / contains 包含，缺省 exact */
    public String matchType;
    /** 第一期仅 text */
    public String replyType;
    public String content;
    /** 匹配优先级，小者先试，缺省 0 */
    public Integer priority;
    /** 1 启用 / 0 停用，缺省 1 */
    public Integer enabled;
    public Date createTime;
    public Date updateTime;

    public static WxAutoReplyView from(WxAutoReplyEntity entity) {
        WxAutoReplyView view = new WxAutoReplyView();
        view.id = entity.getId();
        view.accountId = entity.getAccountId();
        view.ruleType = entity.getRuleType();
        view.keyword = entity.getKeyword();
        view.matchType = entity.getMatchType();
        view.replyType = entity.getReplyType();
        view.content = entity.getContent();
        view.priority = entity.getPriority();
        view.enabled = entity.getEnabled();
        view.createTime = entity.getCreateTime();
        view.updateTime = entity.getUpdateTime();
        return view;
    }

    /** 入参转实体（校验与 normalize 在服务层，不在这里兜底默认值） */
    public WxAutoReplyEntity toEntity() {
        WxAutoReplyEntity entity = new WxAutoReplyEntity();
        entity.setId(id);
        entity.setAccountId(accountId);
        entity.setRuleType(ruleType);
        entity.setKeyword(keyword);
        entity.setMatchType(matchType);
        entity.setReplyType(replyType);
        entity.setContent(content);
        entity.setPriority(priority);
        entity.setEnabled(enabled);
        return entity;
    }
}
