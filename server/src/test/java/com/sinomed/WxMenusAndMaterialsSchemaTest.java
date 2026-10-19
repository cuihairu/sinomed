package com.sinomed;

import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.entity.WxMenuEntity;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.repository.WxMenuRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公众号第一期建表回归（设计稿 §4.3 wx_menus、§4.5 wx_materials「第一期建表不建 UI」）：
 * 上一轮只建了 wx_accounts/wx_fans/wx_auto_replies/wx_message_logs（+§4.8 wx_binding_logs），
 * 这两张属第一期缺口——本测试钉住表名、列名、索引。
 * 同时钉住多列唯一：Hibernate 6.6 + SQLite 方言会静默丢弃多列表级 unique
 * （列定义上无内联、也无独立约束），靠 DbUniqueIndexInitializer 启动期补建——
 * 见 uniqueIndexesAreCreatedAtStartup。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WxMenusAndMaterialsSchemaTest {

    /** @DynamicPropertySource 固定一个库路径：每次解析都新值会导致不同注入点拿到不同库 */
    private static final String DB_URL = "jdbc:sqlite:target/wx-menu-material-schema-test-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
    }

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private WxMenuRepository menuRepository;
    @Autowired
    private WxMaterialRepository materialRepository;

    @BeforeEach
    void cleanup() {
        materialRepository.deleteAll();
        menuRepository.deleteAll();
    }

    private List<String> tableColumns(String table) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("PRAGMA table_info(" + table + ")").getResultList();
        return rows.stream().map(r -> (String) r[1]).collect(Collectors.toList());
    }

    /** SQLite 方言下唯一冲突被包多层且不走 DataIntegrityViolation 翻译，沿 cause 链找 UNIQUE 字样 */
    private static boolean mentionsUnique(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).toUpperCase().contains("UNIQUE")) {
                return true;
            }
        }
        return false;
    }

    private List<String> indexColumns(String indexName) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("PRAGMA index_info(" + indexName + ")").getResultList();
        return rows.stream().map(r -> (String) r[2]).collect(Collectors.toList());
    }

    @Test
    void menuTableHasDesignColumnsAndHierarchyIndex() {
        List<String> columns = tableColumns("wx_menus");
        for (String expected : List.of("id", "account_id", "parent_id", "name", "menu_type", "url", "media_id",
                "miniprogram_appid", "miniprogram_pagepath", "event_key", "sort", "enabled",
                "create_time", "update_time")) {
            assertTrue(columns.contains(expected), "wx_menus 缺列 " + expected + "：" + columns);
        }
        assertEquals(List.of("account_id", "parent_id"), indexColumns("idx_wx_menu_parent"),
                "设计稿 §4.3 要求索引 (account_id, parent_id)");
    }

    @Test
    void materialTableHasDesignColumns() {
        List<String> columns = tableColumns("wx_materials");
        for (String expected : List.of("id", "account_id", "media_id", "material_type", "name", "url",
                "local_path", "size", "mime_type", "create_time", "update_time")) {
            assertTrue(columns.contains(expected), "wx_materials 缺列 " + expected + "：" + columns);
        }
    }

    @Test
    void uniqueIndexesAreCreatedAtStartup() {
        // Hibernate 内联不出的多列唯一，由启动期补建器建成真索引——缺一张即声明与实现脱节
        String[][] expected = {
                {"uk_wx_material_account_media", "account_id, media_id"},
                {"uk_wx_fan_account_openid", "account_id, openid"},
                {"uk_customer_tag_customer_tag", "customer_id, tag"},
                {"uk_commission_rule_item_title", "item_id, title"}
        };
        for (String[] pair : expected) {
            @SuppressWarnings("unchecked")
            List<Object> rows = entityManager.createNativeQuery(
                    "SELECT sql FROM sqlite_master WHERE type='index' AND name=?1")
                    .setParameter(1, pair[0]).getResultList();
            assertEquals(1, rows.size(), "唯一索引 " + pair[0] + " 未创建");
            // 单列标量查询：每行即返回列值，非 Object[]（多列才给 Object[]）
            String ddl = (String) rows.get(0);
            assertTrue(ddl.contains("UNIQUE INDEX"), "索引 " + pair[0] + " 不是唯一索引：" + ddl);
            assertEquals(List.of(pair[1].split(", ")), indexColumns(pair[0]),
                    "索引 " + pair[0] + " 列清单不符（期望 " + pair[1] + "）：" + ddl);
        }
    }

    @Test
    void materialUniqueAccountAndMediaIdEnforced() {
        WxMaterialEntity material = new WxMaterialEntity();
        material.setAccountId(1L);
        material.setMediaId("MEDIA-1");
        material.setMaterialType("image");
        material.setName("头图");
        materialRepository.saveAndFlush(material);

        WxMaterialEntity duplicate = new WxMaterialEntity();
        duplicate.setAccountId(1L);
        duplicate.setMediaId("MEDIA-1");
        duplicate.setMaterialType("image");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> materialRepository.saveAndFlush(duplicate),
                "同账号同 media_id 应被唯一约束拒绝");
        assertTrue(mentionsUnique(ex), "应报 UNIQUE 冲突（SQLite 方言下异常翻译层级不同，按 cause 链找）：" + ex);
        // 不同账号可复用同一 media_id（跨账号隔离）
        WxMaterialEntity otherAccount = new WxMaterialEntity();
        otherAccount.setAccountId(2L);
        otherAccount.setMediaId("MEDIA-1");
        otherAccount.setMaterialType("temp");
        WxMaterialEntity saved = materialRepository.saveAndFlush(otherAccount);
        assertEquals("MEDIA-1", saved.getMediaId());
    }

    @Test
    void menuFieldsRoundTripIncludingDraftState() {
        WxMenuEntity top = new WxMenuEntity();
        top.setAccountId(1L);
        top.setName("预约");
        top.setMenuType("view");
        top.setUrl("https://example.com/book");
        top.setSort(1);
        top.setEnabled(0);  // 草稿：第一期建表不推送微信
        WxMenuEntity savedTop = menuRepository.saveAndFlush(top);

        WxMenuEntity child = new WxMenuEntity();
        child.setAccountId(1L);
        child.setParentId(savedTop.getId());
        child.setName("门诊预约");
        child.setMenuType("view_miniprogram");
        child.setMiniprogramAppid("wxapp123");
        child.setMiniprogramPagepath("pages/booking/index");
        child.setSort(1);
        child.setEnabled(1);
        WxMenuEntity savedChild = menuRepository.saveAndFlush(child);

        WxMenuEntity eventMenu = new WxMenuEntity();
        eventMenu.setAccountId(1L);
        eventMenu.setMenuType("click");
        eventMenu.setName("绑定");
        eventMenu.setEventKey("BIND_CUSTOMER");
        eventMenu.setSort(2);
        eventMenu.setEnabled(1);
        WxMenuEntity savedEvent = menuRepository.saveAndFlush(eventMenu);

        assertEquals("https://example.com/book", savedTop.getUrl(), "view 类型 URL 应落库");
        assertEquals(0, savedTop.getEnabled(), "草稿态 enabled=0 应落库（不推送微信）");
        assertNotNull(savedTop.getCreateTime(), "审计字段应自动写入");
        assertEquals(savedTop.getId(), savedChild.getParentId(), "二级菜单应挂父 id");
        assertEquals("wxapp123", savedChild.getMiniprogramAppid());
        assertEquals("pages/booking/index", savedChild.getMiniprogramPagepath());
        assertEquals("BIND_CUSTOMER", savedEvent.getEventKey());

        List<String> names = menuRepository.findAllByAccountIdOrderBySortAsc(1L).stream()
                .map(WxMenuEntity::getName).collect(Collectors.toList());
        assertEquals(List.of("预约", "门诊预约", "绑定"), names, "按 sort 升序应稳定");
    }
}
