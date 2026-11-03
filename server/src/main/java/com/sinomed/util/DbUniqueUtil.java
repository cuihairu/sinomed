package com.sinomed.util;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Locale;

/**
 * 数据库唯一约束冲突判定（方言无关）。
 *
 * <p>不同数据库/驱动对唯一键违例的消息并不一致，因此沿异常链识别数据库错误码与消息。
 * 不能只按 {@link DataIntegrityViolationException} 类型判定：NOT NULL、外键、超长等完整性错误
 * 也会被 Spring 包装成该类型。消息匹配覆盖 MySQL（"Duplicate entry '...' for key '...'"）与
 * SQLite（"constraint failed: ... UNIQUE ..."）。
 */
public final class DbUniqueUtil {

    private DbUniqueUtil() {
    }

    /** 沿异常链判定是否唯一约束冲突；{@code null} 安全。 */
    public static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("duplicate entry")
                        || (lower.contains("unique") && (lower.contains("constraint") || lower.contains("failed")))
                        || lower.contains("unique constraint")
                        || lower.contains("duplicate key")
                        || lower.contains("duplicate key value")
                        || lower.contains("unique index")
                        || lower.contains("unique constraint")) {
                    return true;
                }
                if (isUniqueSqlCode(t)) {
                    return true;
                }
            } else if (isUniqueSqlCode(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUniqueSqlCode(Throwable t) {
        if (!(t instanceof SQLException sqlException)) {
            return false;
        }
        int errorCode = sqlException.getErrorCode();
        return errorCode == 1062
                || errorCode == 19
                || errorCode == 23505
                || errorCode == -803
                || errorCode == 2601
                || errorCode == 2627;
    }
}
