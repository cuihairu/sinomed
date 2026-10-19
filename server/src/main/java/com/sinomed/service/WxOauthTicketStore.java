package com.sinomed.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页授权 openid 一次性票据（设计稿 §5.6，D-OA1：单实例内存态，5 分钟单次有效，不引 Redis）。
 * callback 换到 openid 后不直接下发给 H5（openid 不进前端 URL/存储），改发不透明 ticket，
 * H5 绑定时凭 ticket 兑换 openid；兑换成功即销毁，重放/错账号/过期一律空。
 */
@Component
public class WxOauthTicketStore {

    private static final long TICKET_TTL_SECONDS = 300;

    private record Entry(long accountId, String openid, long expiresAtEpochSeconds) {
    }

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    public String issue(long accountId, String openid) {
        String ticket = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(accountId, openid, System.currentTimeMillis() / 1000 + TICKET_TTL_SECONDS));
        return ticket;
    }

    public Optional<String> redeem(long accountId, String ticket) {
        Entry entry = ticket == null ? null : tickets.remove(ticket);
        if (entry == null || entry.accountId != accountId
                || System.currentTimeMillis() / 1000 > entry.expiresAtEpochSeconds) {
            return Optional.empty();
        }
        return Optional.of(entry.openid);
    }
}
