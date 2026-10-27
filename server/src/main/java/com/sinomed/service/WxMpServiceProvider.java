package com.sinomed.service;

import com.sinomed.entity.WxAccountEntity;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceHttpClientImpl;
import me.chanjar.weixin.mp.config.WxMpConfigStorage;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WxJava SDK 薄封装（选型 a，设计稿 docs/design/wechat-official-account.md §2/§5.4）：
 * 按 wx_accounts 单账号配置（含实体解密后的 app_secret/aes_key）构建并缓存 WxMpService。
 * 设计稿 §5.1「实例约束」：WxMpService 必须按 accountId 实例化并缓存，单例不能服务多账号配置。
 * 只做「SDK 配置装配 + 缓存」这一层，不掺业务；出网/回调路由/审计仍由本仓自研（§2 结论 3）。
 */
@Component
public class WxMpServiceProvider {

    private final Map<Long, WxMpService> services = new ConcurrentHashMap<>();

    /** 取账号 SDK 服务：按账号 id 缓存；账号停用/开关变化后由调用侧显式失效 */
    public WxMpService forAccount(WxAccountEntity account) {
        return services.computeIfAbsent(account.getId(), id -> build(account));
    }

    /** 业务侧配置变更（如账号启用/凭据更换）后清缓存，下次按新实体重建 */
    public void evict(long accountId) {
        services.remove(accountId);
    }

    /** 缓存的账号数（测试断言用；生产不经此做过账） */
    public int cachedCount() {
        return services.size();
    }

    private WxMpService build(WxAccountEntity account) {
        WxMpService service = new WxMpServiceHttpClientImpl();
        service.setWxMpConfigStorage(configStorage(account));
        return service;
    }

    /** 账号实体 → WxJava 配置存储：应用名/密钥/token/aesKey/加密模式（实体列已由 JPA 转换器解密为明文） */
    public static WxMpConfigStorage configStorage(WxAccountEntity account) {
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(account.getAppId());
        config.setSecret(account.getAppSecret());
        config.setToken(account.getToken());
        config.setAesKey(account.getAesKey());
        return config;
    }
}
