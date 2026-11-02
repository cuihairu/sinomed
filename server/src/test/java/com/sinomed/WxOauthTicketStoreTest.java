package com.sinomed;

import com.sinomed.service.WxOauthTicketStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网页授权一次性票据存储：签发→兑换一次性；过期票据在下次签发时被清理（不永久滞留）。
 * 单实例内存态（D-OA1），不引 Redis——本单测直接驱动 store 实例。
 */
class WxOauthTicketStoreTest {

    private final WxOauthTicketStore store = new WxOauthTicketStore();

    @Test
    void redeemIsSingleUseAndWrongAccountRejected() {
        String ticket = store.issue(7L, "oOPEN1");
        assertEquals(Optional.of("oOPEN1"), store.redeem(7L, ticket), "正确账号应兑换到 openid");
        assertTrue(store.redeem(7L, ticket).isEmpty(), "同一票据重放应空（一次性）");

        String ticket2 = store.issue(7L, "oOPEN2");
        assertTrue(store.redeem(8L, ticket2).isEmpty(), "错账号兑换应空");
    }
}
