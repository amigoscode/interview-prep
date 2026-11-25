package com.amigoscode.pagination.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * One index on the orders table, as the database catalog describes it.
 *
 * @param definition the CREATE INDEX statement Postgres would use to rebuild it
 * @param secondary  false for the index that backs the PRIMARY KEY
 * @param scans      how many times a query used it since it was created
 */
public record IndexInfo(String name, String definition, long sizeBytes, long scans, boolean secondary) {

    private static final String SQL = """
            select c.relname as index_name,
                   pg_get_indexdef(i.indexrelid) as definition,
                   pg_relation_size(i.indexrelid) as size_bytes,
                   coalesce(s.idx_scan, 0) as scans,
                   not exists (select 1 from pg_constraint k where k.conindid = i.indexrelid) as secondary
              from pg_index i
              join pg_class c on c.oid = i.indexrelid
              join pg_class t on t.oid = i.indrelid
              left join pg_stat_user_indexes s on s.indexrelid = i.indexrelid
             where t.relname = 'orders'
               and t.relnamespace = 'public'::regnamespace
             order by secondary, c.relname
            """;

    /** Every index on orders. */
    public static List<IndexInfo> all(Connection connection) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(SQL)) {
            while (row.next()) {
                indexes.add(new IndexInfo(row.getString("index_name"), row.getString("definition"),
                        row.getLong("size_bytes"), row.getLong("scans"), row.getBoolean("secondary")));
            }
        }
        return indexes;
    }

    /** The part of the definition after the table name, for example "(created_at, id)". */
    public String columns() {
        int at = definition.indexOf(" USING btree ");
        return at < 0 ? definition : definition.substring(at + " USING btree ".length());
    }
}
