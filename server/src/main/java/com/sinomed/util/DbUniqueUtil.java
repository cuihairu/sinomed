package com.sinomed.util;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * 数据库唯一约束冲突判定（方言无关）。
 *
 * <p>Spring 的 {@link DataIntegrityViolationException} 是各方言/驱动（SQLite、MySQL、H2 等）把
 * 唯一键违例统一包装成的异常类型，按类型判定是主路径；个别翻译器/方言可能只给出原始 SQLException
 * 消息，故保留消息扫描作兜底。消息匹配同时覆盖 MySQL（"Duplicate entry '...' for key '...'"，
 * 不含 "constraint"/"unique"，原实现漏判）与 SQLite（"constraint failed: ... UNIQUE ..."）。
 */
public final class DbUniqueUtil {

    private DbUniqueUtil() {
    }

    /** 沿异常链判定是否唯一约束冲突；{@code null} 安全。 */
    public static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DataIntegrityViolationException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("constraint") || lower.contains("unique")
                        || lower.contains("duplicate entry") || lower.contains("for key")) {
                    return true;
                }
            }
        }
        return false;
    }
}
