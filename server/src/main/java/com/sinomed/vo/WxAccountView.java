package com.sinomed.vo;

import com.sinomed.entity.WxAccountEntity;
import lombok.Data;

import java.util.Date;

/**
 * 管理端公众号账号视图：兼作创建/更新入参与查询出参。
 * **绝不下发密钥列明文**——appSecret/token/aesKey 属敏感值，查询只回显掩码（'****'）；
 * 更新时留空 = 保持既有（设计稿 §6.3「empty on update = keep」）。
 */
@Data
public class WxAccountView {
    public Long id;
    public String appId;
    public String accountName;
    /** service 服务号 / subscription 订阅号 */
    public String accountType;
    /** 1 已认证 / 0 未认证（能力矩阵驱动） */
    public Integer verified;
    /** 1 启用 / 0 停用 */
    public Integer enabled;
    /** plaintext / compatible / safe */
    public String encryptMode;
    public String remark;
    public Date createTime;

    /** 创建/更新入参：AppSecret（写入加密落库，绝不回显） */
    public String appSecret;
    /** 创建/更新入参：消息推送 Token（设计稿 §7 env 折中——token 列无 converter、写入即明文列，env 仍作单店覆盖；绝不回显） */
    public String token;
    /** 创建/更新入参：EncodingAESKey（写入加密落库，绝不回显） */
    public String aesKey;

    /** 密钥掩码（设计稿 §6.3「AppSecret never echoes back」，查询回显固定值） */
    private static final String SECRET_MASK = "****";

    public static WxAccountView from(WxAccountEntity entity) {
        WxAccountView view = new WxAccountView();
        view.id = entity.getId();
        view.appId = entity.getAppId();
        view.accountName = entity.getAccountName();
        view.accountType = entity.getAccountType();
        view.verified = entity.getVerified();
        view.enabled = entity.getEnabled();
        view.encryptMode = entity.getEncryptMode();
        view.remark = entity.getRemark();
        view.createTime = entity.getCreateTime();
        // 密钥绝不回显明文：有存量则给掩码，无存量留空（前端据此提示「未配置/保持」）
        view.appSecret = hasSecret(entity.getAppSecret()) ? SECRET_MASK : null;
        view.token = hasSecret(entity.getToken()) ? SECRET_MASK : null;
        view.aesKey = hasSecret(entity.getAesKey()) ? SECRET_MASK : null;
        return view;
    }

    private static boolean hasSecret(String value) {
        return value != null && !value.isBlank();
    }

    /** 入参转实体（校验与归一化在服务层，不在这里兜底默认值） */
    public WxAccountEntity toEntity() {
        WxAccountEntity entity = new WxAccountEntity();
        entity.setId(id);
        entity.setAppId(appId);
        entity.setAccountName(accountName);
        entity.setAccountType(accountType);
        entity.setVerified(verified);
        entity.setEnabled(enabled);
        entity.setEncryptMode(encryptMode);
        entity.setRemark(remark);
        entity.setAppSecret(appSecret);
        entity.setToken(token);
        entity.setAesKey(aesKey);
        return entity;
    }
}
