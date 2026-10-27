package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.service.WxMpServiceProvider;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.config.WxMpConfigStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * WxJava SDK 薄封装单元回归（选型 a，设计稿 docs/design/wechat-official-account.md §2/§5.1）：
 * 账号实体 → WxMpConfigStorage 配置映射、WxMpService 按 accountId 构建并缓存、evict 逐出重建。
 * 纯本地：不触网、不依赖线上号凭证，仅验证 SDK 配置装配层。
 */
class WxMpServiceProviderTest {

    private static WxAccountEntity account(long id, String appId) {
        WxAccountEntity account = new WxAccountEntity();
        account.setId(id);
        account.setAppId(appId);
        account.setAppSecret("SECRET_" + appId);
        account.setToken("TOKEN_" + appId);
        account.setAesKey("AESKEY_" + appId);
        return account;
    }

    @Test
    void configStorageMapsAccountCredentialsAndDecoded() {
        WxAccountEntity account = account(1L, "wxAPPID1");
        WxMpConfigStorage config = WxMpServiceProvider.configStorage(account);
        assertEquals("wxAPPID1", config.getAppId());
        assertEquals("SECRET_wxAPPID1", config.getSecret());
        assertEquals("TOKEN_wxAPPID1", config.getToken());
        assertEquals("AESKEY_wxAPPID1", config.getAesKey());
    }

    @Test
    void forAccountCachesSameInstancePerAccountId() {
        WxMpServiceProvider provider = new WxMpServiceProvider();
        WxMpService first = provider.forAccount(account(1L, "wxAPPID1"));
        assertSame(first, provider.forAccount(account(1L, "wxAPPID1")), "同 accountId 应命中缓存");
        assertEquals(1, provider.cachedCount());
    }

    @Test
    void forAccountDoesNotShareAcrossAccountIds() {
        WxMpServiceProvider provider = new WxMpServiceProvider();
        WxMpService a = provider.forAccount(account(1L, "wxAPPID1"));
        WxMpService b = provider.forAccount(account(2L, "wxAPPID2"));
        assertNotSame(a, b, "不同 accountId 必须独立实例（设计稿 §5.1 实例约束）");
        assertEquals(2, provider.cachedCount());
    }

    @Test
    void evictDropsCacheAndNextCallRebuilds() {
        WxMpServiceProvider provider = new WxMpServiceProvider();
        WxAccountEntity first = account(1L, "wxAPPID1");
        WxMpService original = provider.forAccount(first);
        provider.evict(1L);
        assertEquals(0, provider.cachedCount(), "evict 应清出缓存");
        assertNotSame(original, provider.forAccount(first), "evict 后应重建实例");
    }
}
