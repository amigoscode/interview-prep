package com.amigoscode.constraints.demo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Shows what the database enforces right now and whether the data already breaks a rule: for each
 * table its columns, constraints and indexes, then every duplicate group and every bad row. Run it
 * with ./inspect (or .\inspect on Windows), usually right after ./demo.
 */
public final class Inspect {

    private static final List<String> TABLES = List.of("users", "subscriptions", "user_settings");

    private static final String COLUMNS = """
            select column_name as column,
                   data_type as type,
                   case is_nullable when 'YES' then 'yes' else 'NOT NULL' end as null_allowed
              from information_schema.columns
             where table_schema = 'public' and table_name = '%s'
             order by ordinal_position
            """;

    private static final String CONSTRAINTS = """
            select c.conname as name,
                   case c.contype when 'p' then 'PRIMARY KEY' when 'u' then 'UNIQUE' when 'c' then 'CHECK'
                                  when 'f' then 'FOREIGN KEY' else c.contype::text end as kind,
                   pg_get_constraintdef(c.oid) as definition
              from pg_constraint c
             where c.conrelid = 'public.%s'::regclass
             order by case c.contype when 'p' then 1 when 'u' then 2 when 'f' then 3 else 4 end, c.conname
            """;

    private static final String INDEXES = """
            select indexname as name,
                   case when indexdef like 'CREATE UNIQUE%%' then 'yes' else 'no' end as unique,
                   regexp_replace(indexdef, '^.* USING ', '') as definition
              from pg_indexes
             where schemaname = 'public' and tablename = '%s'
             order by indexname
            """;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.out.println("Usage: ./inspect     (Windows: .\\inspect)");
            System.out.println();
            System.out.println("Prints the columns, constraints and indexes of every table, then the duplicates");
            System.out.println("and bad rows that are in the data right now. Run ./demo first to have some data.");
            return;
        }
        if (!Db.isUp()) {
            return;
        }
        try (Connection connection = Db.connect()) {
            if (Db.count(connection, "select count(*) from pg_tables where schemaname = 'public' and tablename = 'users'") == 0) {
                System.out.println();
                System.out.println("The tables do not exist yet. Run ./demo once: it creates them from schema.sql.");
                System.out.println();
                return;
            }
            for (String table : TABLES) {
                table(connection, table);
            }
            data(connection);
        }
    }

    private static void table(Connection connection, String table) throws SQLException {
        System.out.println();
        int rows = Db.count(connection, "select count(*) from " + table);
        System.out.println("==== TABLE " + table + " (" + rows + (rows == 1 ? " row" : " rows") + ") ====");
        System.out.println();
        System.out.println("COLUMNS");
        Db.printTable(connection, COLUMNS.formatted(table));
        System.out.println();
        System.out.println("CONSTRAINTS (pg_constraint)");
        if (Db.printTable(connection, CONSTRAINTS.formatted(table)) == 0) {
            System.out.println("  none");
        }
        System.out.println();
        System.out.println("INDEXES (pg_indexes)");
        if (Db.printTable(connection, INDEXES.formatted(table)) == 0) {
            System.out.println("  none");
        }
    }

    private static void data(Connection connection) throws SQLException {
        System.out.println();
        System.out.println("==== WHAT IS IN THE DATA RIGHT NOW ====");

        section(connection, "SAME EMAIL, MORE THAN ONE ROW (compared without case)", """
                select lower(email) as email_lowercase,
                       count(*) as rows,
                       string_agg(id::text, ', ' order by id) as user_ids,
                       string_agg(email, ', ' order by id) as stored_as
                  from users
                 where email is not null
                 group by lower(email)
                having count(*) > 1
                 order by 1
                """);
        section(connection, "MORE THAN ONE ACTIVE SUBSCRIPTION FOR ONE USER", """
                select user_id,
                       count(*) as active_rows,
                       string_agg(id::text, ', ' order by id) as subscription_ids
                  from subscriptions
                 where status = 'ACTIVE'
                 group by user_id
                having count(*) > 1
                 order by 1
                """);
        section(connection, "BAD ROWS", BAD_ROWS);
        System.out.println();
    }

    /** Every row that breaks a rule the solution enforces with NOT NULL, CHECK or FOREIGN KEY. */
    static final String BAD_ROWS = """
            select 'users' as table, id, 'email is NULL' as problem from users where email is null
            union all
            select 'subscriptions', id, 'seats = ' || seats from subscriptions where seats <= 0
            union all
            select 'subscriptions', id, 'price_cents = ' || price_cents from subscriptions where price_cents < 0
            union all
            select 'subscriptions', s.id, 'user ' || s.user_id || ' does not exist'
              from subscriptions s
             where not exists (select 1 from users u where u.id = s.user_id)
             order by 1 desc, 2, 3
            """;

    private static void section(Connection connection, String title, String sql) throws SQLException {
        System.out.println();
        System.out.println(title);
        if (Db.printTable(connection, sql) == 0) {
            System.out.println("  none");
        }
    }

    private Inspect() {
    }
}
