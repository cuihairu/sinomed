package com.sinomed.service.impl;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.service.WxCallbackService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公众号账号管理端（设计稿 §4.1「配置入口」/§6.1「能力矩阵」/§6.3「账号配置 ADMIN」/§7 凭据加密落库）：
 * 账号列表（密钥列绝不读回）、能力矩阵探测（account_type+verified 驱动）、账号保存（凭据加密落库、
 * 更新留空=保持既有）+ 配置变更后失效 SDK/路由缓存。
 */
@Component
public class WxAccountAdminService {

    private final WxAccountRepository accountRepository;
    private final WxCallbackService callbackService;

    /** 账号类型合法值（设计稿 §4.1：service 服务号 / subscription 订阅号） */
    private static final Set<String> ACCOUNT_TYPES = Set.of("service", "subscription");
    /** 消息加密模式合法值（设计稿 §4.1：plaintext/compatible/safe） */
    private static final Set<String> ENCRYPT_MODES = Set.of("plaintext", "compatible", "safe");

    public WxAccountAdminService(WxAccountRepository accountRepository,
                                 WxCallbackService callbackService) {
        this.accountRepository = accountRepository;
        this.callbackService = callbackService;
    }

    /** 管理端账号列表（规则/粉丝页选账号用，单店量小不分页，id 升序）；密钥列绝不读回（见 WxAccountView） */
    public List<WxAccountEntity> listAccounts() {
        return accountRepository.findAll(Sort.by("id"));
    }

    /** 能力矩阵（设计稿 §6.1/§6.2）：account_type + verified 两字段驱动，不写死枚举分支 */
    public Map<String, Object> capabilities(long accountId) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        boolean service = "service".equals(account.getAccountType());
        boolean verified = account.getVerified() != null && account.getVerified() == 1;
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("accountId", account.getId());
        caps.put("accountName", account.getAccountName());
        caps.put("accountType", account.getAccountType());
        caps.put("verified", verified);
        caps.put("callback", true);          // 三档账号都可回调（矩阵 §6.1）
        caps.put("materials", true);          // 素材上传三档都可
        caps.put("webAuth", true);            // 网页授权三档都可
        caps.put("menuManagement", service || verified);
        caps.put("customerService", service || verified);
        caps.put("massMessaging", service || verified);
        caps.put("templateMessage", service); // 模板消息仅服务号
        return caps;
    }

    /**
     * 管理端账号保存（设计稿 §4.1「配置入口」/§6.3「账号配置 ADMIN」/§7 凭据加密落库）：
     * 创建/更新共用；appSecret/token/aesKey 落库加密（token 列无 converter，属设计稿 §7 env 折中，
     * 写入即明文列——凭据类仍走加密列），更新时留空=保持既有（不回显、不覆盖）。校验枚举，
     * appId 必填且同 appId 唯一；凭据写入前需 WX_OA_CREDENTIAL_KEY 已配（WxCredentialCipher 拒绝）。
     * 配置变更后失效 SDK/路由缓存（下次按新实体重建）。
     */
    public WxAccountEntity saveAccount(WxAccountEntity incoming) {
        WxAccountEntity entity;
        boolean isCreate = incoming.getId() == null;
        if (isCreate) {
            entity = new WxAccountEntity();
        } else {
            entity = accountRepository.findById(incoming.getId())
                    .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        }
        if (isBlank(incoming.getAppId())) {
            throw new IllegalArgumentException("公众号 AppID 不能为空");
        }
        String appId = incoming.getAppId().trim();
        accountRepository.findAll().stream()
                .filter(other -> appId.equals(other.getAppId()))
                .filter(other -> !other.getId().equals(entity.getId()))
                .findAny()
                .ifPresent(other -> {
                    throw new IllegalArgumentException("AppID 已存在：" + appId);
                });
        String accountType = isBlank(incoming.getAccountType()) ? "service" : incoming.getAccountType().trim();
        if (!ACCOUNT_TYPES.contains(accountType)) {
            throw new IllegalArgumentException("accountType 必须是 service/subscription");
        }
        String encryptMode = isBlank(incoming.getEncryptMode()) ? "plaintext" : incoming.getEncryptMode().trim();
        if (!ENCRYPT_MODES.contains(encryptMode)) {
            throw new IllegalArgumentException("encryptMode 必须是 plaintext/compatible/safe");
        }
        entity.setAppId(appId);
        entity.setAccountName(isBlank(incoming.getAccountName()) ? appId : incoming.getAccountName().trim());
        entity.setAccountType(accountType);
        entity.setVerified(incoming.getVerified() == null ? 0 : incoming.getVerified());
        Integer enabled = incoming.getEnabled() == null ? 1 : incoming.getEnabled();
        if (enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("enabled 必须是 0/1（停用/启用）");
        }
        entity.setEnabled(enabled);
        entity.setEncryptMode(encryptMode);
        entity.setRemark(isBlank(incoming.getRemark()) ? null : incoming.getRemark().trim());
        // 凭据：更新留空=保持既有（不回显掩码，绝不清除）；创建显式给（可空=不配）。
        // app_secret/aes_key 走 AES 加密列；token 为设计稿 §7 env 折中列（写入明文列，凭据类仍加密）。
        if (incoming.getAppSecret() != null && !incoming.getAppSecret().isBlank()) {
            entity.setAppSecret(incoming.getAppSecret().trim());
        }
        if (incoming.getAesKey() != null && !incoming.getAesKey().isBlank()) {
            entity.setAesKey(incoming.getAesKey().trim());
        }
        if (incoming.getToken() != null && !incoming.getToken().isBlank()) {
            entity.setToken(incoming.getToken().trim());
        }
        WxAccountEntity saved = accountRepository.save(entity);
        // 凭据/开关/类型变更后清路由与服务缓存（设计稿 §5.1 实例约束）
        callbackService.evictAccount(saved.getId());
        return saved;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
