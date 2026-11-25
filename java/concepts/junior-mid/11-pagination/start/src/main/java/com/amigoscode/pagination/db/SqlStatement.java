package com.amigoscode.pagination.db;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One SQL statement the application sent to the database, with the values it bound to the
 * ? placeholders. Recorded by SqlRecorder.
 */
public record SqlStatement(String sql, List<Binding> bindings) {

    /** One call like setLong(2, 20) on the PreparedStatement. */
    public record Binding(Method setter, Object[] arguments) {

        Object value() {
            return arguments[1];
        }
    }

    private static final Pattern COUNT = Pattern.compile("^\\s*select\\s+count\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_LIST = Pattern.compile("^(select) (.{40,}?) (from .*)$", Pattern.CASE_INSENSITIVE);

    /** True for SELECT COUNT(...): the query that counts every matching row. */
    public boolean isCount() {
        return COUNT.matcher(sql).find();
    }

    /** The SQL in lower case on one line: handy for "does it contain ..." checks. */
    public String normalized() {
        return sql.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    /** The SQL on one line, with a long list of selected columns shortened to "...". */
    public String shortSql() {
        String oneLine = sql.replaceAll("\\s+", " ").strip();
        return SELECT_LIST.matcher(oneLine).replaceFirst("$1 ... $3");
    }

    /** The bound values in placeholder order, for example [99980, 20]. */
    public List<Object> parameters() {
        return bindings.stream().map(Binding::value).toList();
    }

    /** Binds the same values to another PreparedStatement, so the statement can be run again. */
    public void bindTo(PreparedStatement statement) throws SQLException {
        for (Binding binding : bindings) {
            try {
                binding.setter().invoke(statement, binding.arguments());
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SQLException sql) {
                    throw sql;
                }
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public String toString() {
        return shortSql() + (bindings.isEmpty() ? "" : " " + parameters());
    }
}
