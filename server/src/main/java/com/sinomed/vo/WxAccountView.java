package com.sinomed.vo;

import com.sinomed.entity.WxAccountEntity;
import lombok.Data;

import java.util.Date;

/**
 * 管理端公众号账号视图：**绝不下发密钥列**（appSecret/token/aesKey 在实体侧为加密或明文敏感值，
 * 管理端只需 appId 等识别信息；要改密钥走部署配置/SQL，不提供读回口）。
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
    /** plaintext / 安全模式（第二期 AES） */
    public String encryptMode;
    public String remark;
    public Date createTime;

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
        return view;
    }
}
