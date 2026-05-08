package com.paiagent.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;
import org.hibernate.type.SqlTypes;

/**
 * SQLite dialect for Hibernate 6.x (Spring Boot 3.4.x).
 * Based on the official Hibernate community dialect.
 */
public class SQLiteDialect extends Dialect {

    public SQLiteDialect() {
        // No registerColumnType calls needed — columnType() override handles mappings
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        return switch (sqlTypeCode) {
            case SqlTypes.FLOAT, SqlTypes.REAL -> "float";
            case SqlTypes.TIMESTAMP, SqlTypes.TIMESTAMP_WITH_TIMEZONE -> "timestamp";
            case SqlTypes.TIME_WITH_TIMEZONE -> "time";
            case SqlTypes.BINARY, SqlTypes.VARBINARY, SqlTypes.LONGVARBINARY -> "blob";
            case SqlTypes.BLOB -> "blob";
            default -> super.columnType(sqlTypeCode);
        };
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new SQLiteIdentityColumnSupport();
    }

    @Override
    public boolean supportsUnionAll() {
        return true;
    }

    @Override
    public boolean supportsCurrentTimestampSelection() {
        return true;
    }

    @Override
    public boolean isCurrentTimestampSelectStringCallable() {
        return false;
    }

    @Override
    public String getCurrentTimestampSelectString() {
        return "select current_timestamp";
    }

    public static class SQLiteIdentityColumnSupport extends IdentityColumnSupportImpl {
        @Override
        public boolean supportsIdentityColumns() {
            return true;
        }

        @Override
        public String getIdentitySelectString(String table, String column, int type) {
            return "select last_insert_rowid()";
        }

        @Override
        public String getIdentityColumnString(int type) {
            return "integer";
        }
    }
}
