package com.amigoscode.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecretStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecretStorageApplication.class, args);
    }

}
