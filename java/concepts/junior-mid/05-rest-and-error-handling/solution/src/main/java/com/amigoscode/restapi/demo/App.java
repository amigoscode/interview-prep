package com.amigoscode.restapi.demo;

import com.amigoscode.restapi.RestApiApplication;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The real application, started quietly on a free port and reset to the six orders of seed.sql. */
final class App implements AutoCloseable {

    final ConfigurableApplicationContext context;
    final JdbcTemplate jdbc;
    final Http http;

    private App(ConfigurableApplicationContext context) {
        this.context = context;
        this.jdbc = context.getBean(JdbcTemplate.class);
        this.http = new Http("http://localhost:" + context.getEnvironment().getProperty("local.server.port"));
    }

    static App start() {
        SpringApplication app = new SpringApplication(RestApiApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        App started = new App(app.run(
                "--server.port=0",
                "--logging.level.root=ERROR",
                // An exception nobody handles is shown through the HTTP response, not as a stack trace in the log.
                "--logging.level.org.apache.catalina.core.ContainerBase=OFF",
                "--logging.level.com.amigoscode.restapi.ApiExceptionHandler=OFF",
                "--logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF"));
        started.reset();
        return started;
    }

    void reset() {
        Db.reset(context.getBean(DataSource.class));
    }

    Map<Long, Map<String, String>> snapshot() {
        return Db.snapshot(jdbc);
    }

    Map<RequestMappingInfo, HandlerMethod> endpoints() {
        return context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class).getHandlerMethods();
    }

    /** How this build of the application cancels an order: "GET" before Task 2, "POST" after it. */
    String cancelMethod() {
        for (RequestMappingInfo info : endpoints().keySet()) {
            boolean isCancel = info.getPathPatternsCondition() != null
                    && info.getPathPatternsCondition().getPatternValues().contains("/api/orders/{id}/cancel");
            if (isCancel && !info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
                return info.getMethodsCondition().getMethods().iterator().next().name();
            }
        }
        return "GET";
    }

    @Override
    public void close() {
        context.close();
    }
}
