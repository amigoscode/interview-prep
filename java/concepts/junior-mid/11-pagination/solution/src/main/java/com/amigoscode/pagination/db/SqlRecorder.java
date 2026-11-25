package com.amigoscode.pagination.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Records the SQL the application really sends to Postgres, whoever wrote it: Hibernate, Spring
 * Data or a native query. ./demo, ./inspect and the tests use it to answer "what did this request
 * run?" and then ask Postgres to EXPLAIN exactly that.
 *
 *     SqlRecorder.start();
 *     ... call the endpoint ...
 *     List<SqlStatement> statements = SqlRecorder.stop();
 *
 * It works by wrapping the DataSource: every PreparedStatement is watched, and when it is executed
 * its SQL and its bound values are written down. When nobody is recording, nothing is kept.
 */
public final class SqlRecorder {

    private static final Object LOCK = new Object();
    private static final Set<String> EXECUTE = Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate");
    private static List<SqlStatement> recording;

    public static void start() {
        synchronized (LOCK) {
            recording = new ArrayList<>();
        }
    }

    /** Stops recording and returns the statements executed since start(), in order. */
    public static List<SqlStatement> stop() {
        synchronized (LOCK) {
            List<SqlStatement> statements = recording == null ? List.of() : List.copyOf(recording);
            recording = null;
            return statements;
        }
    }

    private static void record(String sql, Map<Integer, SqlStatement.Binding> bindings) {
        synchronized (LOCK) {
            if (recording != null) {
                recording.add(new SqlStatement(sql, List.copyOf(bindings.values())));
            }
        }
    }

    /** Wraps every DataSource bean of the application. */
    @Configuration
    static class Wiring {

        @Bean
        static BeanPostProcessor recordSqlOfEveryDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource dataSource ? new RecordingDataSource(dataSource) : bean;
                }
            };
        }
    }

    private static final class RecordingDataSource extends DelegatingDataSource {

        RecordingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return watch(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return watch(super.getConnection(username, password));
        }

        private static Connection watch(Connection connection) {
            return proxy(Connection.class, (proxy, method, args) -> {
                Object result = call(connection, method, args);
                if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement) {
                    return watch(statement, (String) args[0]);
                }
                return result;
            });
        }

        private static PreparedStatement watch(PreparedStatement statement, String sql) {
            Map<Integer, SqlStatement.Binding> bindings = new TreeMap<>();
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                    bindings.put(index, new SqlStatement.Binding(method, args.clone()));
                } else if (name.equals("clearParameters")) {
                    bindings.clear();
                } else if (EXECUTE.contains(name) && args == null) {
                    record(sql, bindings);
                }
                return call(statement, method, args);
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
        }

        private static Object call(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private SqlRecorder() {
    }
}
