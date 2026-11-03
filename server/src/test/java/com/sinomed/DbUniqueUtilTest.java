package com.sinomed;

import com.sinomed.util.DbUniqueUtil;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 唯一约束冲突判定方言无关回归：默认 MySQL 方言的重复键消息为
 * "Duplicate entry '...' for key '...'"，不含 "constraint"/"unique"，
 * 原实现按消息子串匹配会漏判——并发双落被误升 500 而非 400。此处锁定修复。
 */
class DbUniqueUtilTest {

    @Test
    void mysqlDuplicateEntryMessageDetected() {
        // MySQL 驱动：constraint violation 包成 DataIntegrityViolationException，消息为 "Duplicate entry ... for key ..."
        SQLException cause = new SQLException(
                "Duplicate entry '1|1760000000000' for key 'uk_appointment_slot_key'", "23000", 1062);
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement", cause);
        assertTrue(DbUniqueUtil.isUniqueViolation(e), "MySQL Duplicate entry 消息应判为唯一冲突");
    }

    @Test
    void sqliteConstraintMessageDetected() {
        // SQLite 方言既有路径
        SQLException cause = new SQLException("constraint failed: UNIQUE constraint failed: appointment.slot_key");
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement", cause);
        assertTrue(DbUniqueUtil.isUniqueViolation(e), "SQLite constraint 消息应判为唯一冲突");
    }

    @Test
    void nonConstraintViolationNotMisclassified() {
        // 死锁不是唯一约束冲突：真实场景由 CannotAcquireLockException 包装（非 DataIntegrityViolationException），
        // 不应被判为唯一冲突（否则会把 500 死锁误降为 400「该时段已约满」）。
        SQLException cause = new SQLException("Deadlock found when trying to get lock; try restarting transaction", "40001");
        DataAccessException e = new DataAccessResourceFailureException("could not execute statement", cause);
        assertFalse(DbUniqueUtil.isUniqueViolation(e), "死锁/普通错误不应误判为唯一冲突");
    }

    @Test
    void notNullViolationWrappedAsDataIntegrityViolationNotMisclassified() {
        // MySQL 1048 / SQLSTATE 23000：NOT NULL 违例同样被 Spring 包装成 DataIntegrityViolationException，
        // 但消息 "Column cannot be null" 不含 "duplicate entry"/"unique constraint"，应判 false（如实上抛）。
        SQLException cause = new SQLException("Column 'name' cannot be null", "23000", 1048);
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement", cause);
        assertFalse(DbUniqueUtil.isUniqueViolation(e), "NOT NULL 违例不应误判为唯一冲突");
    }

    @Test
    void foreignKeyViolationWrappedAsDataIntegrityViolationNotMisclassified() {
        // 外键违例：MySQL 1452 消息含 "constraint"，但为 FK 完整性错误而非唯一键，应判 false。
        SQLException cause = new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452);
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement", cause);
        assertFalse(DbUniqueUtil.isUniqueViolation(e), "外键违例不应误判为唯一冲突");
    }

    @Test
    void dataTooLongWrappedAsDataIntegrityViolationNotMisclassified() {
        // data-too-long：MySQL 1406，非唯一键，应判 false。
        SQLException cause = new SQLException("Data too long for column 'phone' at row 1", "22001", 1406);
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement", cause);
        assertFalse(DbUniqueUtil.isUniqueViolation(e), "超长违例不应误判为唯一冲突");
    }
}
