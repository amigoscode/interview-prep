package com.amigoscode.sqltraps.demo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** A query result held as text, and printed as a plain ASCII table. */
record TextTable(List<String> columns, List<List<String>> rows) {

    /** Reads at most maxRows rows of a result. NULL is shown as the word NULL. */
    static TextTable read(PreparedStatement statement, int maxRows) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            ResultSetMetaData meta = result.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
                columns.add(meta.getColumnLabel(c));
            }
            List<List<String>> rows = new ArrayList<>();
            while (rows.size() < maxRows && result.next()) {
                List<String> row = new ArrayList<>();
                for (int c = 1; c <= columns.size(); c++) {
                    String value = result.getString(c);
                    row.add(value == null ? "NULL" : shorten(value));
                }
                rows.add(row);
            }
            return new TextTable(columns, rows);
        }
    }

    private static String shorten(String value) {
        String oneLine = value.replaceAll("\\s+", " ");
        return oneLine.length() <= 40 ? oneLine : oneLine.substring(0, 37) + "...";
    }

    void print() {
        if (rows.isEmpty()) {
            System.out.println("  (no rows)");
            return;
        }
        int[] widths = new int[columns.size()];
        for (int c = 0; c < widths.length; c++) {
            widths[c] = columns.get(c).length();
            for (List<String> row : rows) {
                widths[c] = Math.max(widths[c], row.get(c).length());
            }
        }
        printRow(columns, widths);
        StringBuilder rule = new StringBuilder("  ");
        for (int width : widths) {
            rule.append("-".repeat(width)).append("  ");
        }
        System.out.println(rule.toString().stripTrailing());
        for (List<String> row : rows) {
            printRow(row, widths);
        }
    }

    private static void printRow(List<String> row, int[] widths) {
        StringBuilder line = new StringBuilder("  ");
        for (int c = 0; c < widths.length; c++) {
            line.append(String.format("%-" + widths[c] + "s", row.get(c))).append("  ");
        }
        System.out.println(line.toString().stripTrailing());
    }
}
