package com.amigoscode.fixit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeyCaseConverterTest {

    @Test
    void projectIsWiredUp() {
        assertThat(KeyCaseConverter.class).isNotNull();
    }

    @Test
    @Disabled("task 3: remove this line to begin")
    void convertsTopLevelKeys() {
        Map<String, Object> input = Map.of("order_id", "42", "delivery_address", "Carrer de Balmes 1");
        assertThat(KeyCaseConverter.toCamelCase(input))
                .isEqualTo(Map.of("orderId", "42", "deliveryAddress", "Carrer de Balmes 1"));
    }
}
