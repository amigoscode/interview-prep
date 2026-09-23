package com.amigoscode.interview;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.admin.password-hash=$2y$04$/al83hyGe4DISaEbpHhB2OTwN8xbo9Fv0qi6Ax.TY0USM88VKiwzy")
class SecretStorageApplicationTests {

    @Test
    void contextLoads() {
    }

}
