package com.sinomed;

import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.service.impl.WxMessageLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消息流水原子插入「处理权」语义回归（Codex 深审第 2 条）：
 * logMessage 返回本请求是否取得该消息的业务处理权——首次插入成功 true；
 * 并发/重放撞 dedupe_key 唯一索引 false（只落 duplicate 拒绝审计，不向上抛）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-log-ownership-test-${random.uuid}.db"
})
class WxMessageLogOwnershipTest {

    @Autowired
    private WxMessageLogService messageLogService;

    @Autowired
    private WxMessageLogRepository messageLogRepository;

    @BeforeEach
    void cleanLogs() {
        // 同类两个用例共享一库：逐例清表，断言只数本例落下的行
        messageLogRepository.deleteAll();
    }

    @Test
    void firstInsertOwnsProcessingAndConflictReturnsFalse() {
        assertTrue(messageLogService.logMessage(1L, "in", "text", "oOWNER", "gh_test", "你好", "123", "M:123"),
                "首次落库（无同键行）应取得处理权返回 true");

        // 同 dedupe_key 二次写入：saveAndFlush 直接撞唯一索引（服务层不做 exists 预查，
        // 强制走并发双投递输家的冲突分支），应吞异常、落 duplicate 审计并返回 false
        assertFalse(messageLogService.logMessage(1L, "in", "text", "oOWNER", "gh_test", "你好", "123", "M:123"),
                "同 dedupe_key 冲突应判定未取得处理权返回 false");

        var inLogs = messageLogRepository.findAll().stream()
                .filter(l -> "in".equals(l.getDirection())).toList();
        assertEquals(1, inLogs.size(), "入站流水只落一条（赢家的）");
        var rejects = messageLogRepository.findAll().stream()
                .filter(l -> "reject".equals(l.getDirection())).toList();
        assertEquals(1, rejects.size(), "冲突应落一条拒绝审计");
        assertEquals("duplicate", rejects.get(0).getResult(), "拒绝原因应为 duplicate");
    }

    @Test
    void differentKeysEachOwnTheirMessage() {
        assertTrue(messageLogService.logMessage(1L, "in", "text", "oA", "gh_test", "你好", "111", "M:111"));
        assertTrue(messageLogService.logMessage(1L, "in", "text", "oB", "gh_test", "你好", "222", "M:222"),
                "不同 dedupe_key 各自取得处理权");
        assertEquals(2, messageLogRepository.findAll().stream()
                .filter(l -> "in".equals(l.getDirection())).count());
    }
}
