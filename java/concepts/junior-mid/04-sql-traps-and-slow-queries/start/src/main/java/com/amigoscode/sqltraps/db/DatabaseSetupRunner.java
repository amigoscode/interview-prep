package com.amigoscode.sqltraps.db;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs DatabaseSetup when the application starts: the tables, then the rows of the chosen shop. */
@Component
public class DatabaseSetupRunner implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSetupRunner.class);

    private final DataSource dataSource;
    private final DatabaseSetup.Shop shop;

    public DatabaseSetupRunner(DataSource dataSource, @Value("${shop.seed}") String shop) {
        this.dataSource = dataSource;
        this.shop = DatabaseSetup.Shop.from(shop);
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseSetup.ensure(connection, shop, log::info);
        }
    }
}
