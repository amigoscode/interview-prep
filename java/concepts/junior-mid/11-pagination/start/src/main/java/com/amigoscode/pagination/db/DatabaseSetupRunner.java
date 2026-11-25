package com.amigoscode.pagination.db;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs DatabaseSetup when the application starts: the table, the rows, then the indexes from indexes.sql. */
@Component
public class DatabaseSetupRunner implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSetupRunner.class);

    private final DataSource dataSource;
    private final long orders;

    public DatabaseSetupRunner(DataSource dataSource, @Value("${shop.seed.orders}") long orders) {
        this.dataSource = dataSource;
        this.orders = orders;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseSetup.run(connection, orders, log::info);
        }
    }
}
