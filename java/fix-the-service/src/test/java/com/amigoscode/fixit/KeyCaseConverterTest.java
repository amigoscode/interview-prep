package com.amigoscode.fixit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyCaseConverterTest {

    @Test
    void convertsTopLevelKeys() {
        Map<String, Object> input = Map.of("order_id", "42", "delivery_address", "Carrer de Balmes 1");
        assertThat(KeyCaseConverter.toCamelCase(input))
                .isEqualTo(Map.of("orderId", "42", "deliveryAddress", "Carrer de Balmes 1"));
    }

    @Test
    void convertsNestedMapsAtEveryLevel() {
        Map<String, Object> input = Map.of(
                "order_id", "42",
                "customer_info", Map.of(
                        "first_name", "Ana",
                        "home_address", Map.of("street_name", "Balmes", "post_code", "08007")));

        assertThat(KeyCaseConverter.toCamelCase(input)).isEqualTo(Map.of(
                "orderId", "42",
                "customerInfo", Map.of(
                        "firstName", "Ana",
                        "homeAddress", Map.of("streetName", "Balmes", "postCode", "08007"))));
    }

    @ParameterizedTest
    @CsvSource({
            "order_id,          orderId",
            "_order_id,         orderId",
            "order_id_,         orderId",
            "order__id,         orderId",
            "__order___id__,    orderId",
            "orderId,           orderId",
            "order,             order",
            "total_price_eur,   totalPriceEur",
            "user_ID,           userID",
            "line_1,            line1",
    })
    void convertsSingleKeys(String snake, String camel) {
        assertThat(KeyCaseConverter.toCamelCase(snake)).isEqualTo(camel);
    }

    @Test
    void keyWithNoLettersIsRejected() {
        assertThatThrownBy(() -> KeyCaseConverter.toCamelCase(Map.of("__", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyCaseConverter.toCamelCase(Map.of("", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void collisionAfterConversionThrowsAndNamesBothKeys() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("order_id", "1");
        input.put("orderId", "2");
        assertThatThrownBy(() -> KeyCaseConverter.toCamelCase(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order_id")
                .hasMessageContaining("orderId");
    }

    @Test
    void collisionInsideANestedMapAlsoThrows() {
        Map<String, Object> input = Map.of("customer", Map.of("first_name", "a", "first__name", "b"));
        assertThatThrownBy(() -> KeyCaseConverter.toCamelCase(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotMutateTheInput() {
        Map<String, Object> nested = new HashMap<>(Map.of("street_name", "Balmes"));
        Map<String, Object> input = new HashMap<>(Map.of("order_id", "42", "home_address", nested));

        KeyCaseConverter.toCamelCase(input);

        assertThat(input).containsOnlyKeys("order_id", "home_address");
        assertThat(nested).containsOnlyKeys("street_name");
    }

    @Test
    void resultIsUnmodifiableAndKeepsKeyOrder() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("z_key", "1");
        input.put("a_key", "2");
        input.put("m_key", "3");

        Map<String, Object> result = KeyCaseConverter.toCamelCase(input);

        assertThat(result.keySet()).containsExactly("zKey", "aKey", "mKey");
        assertThatThrownBy(() -> result.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyMapGivesEmptyMap() {
        assertThat(KeyCaseConverter.toCamelCase(Map.of())).isEmpty();
    }

    @Test
    void nullValuesAndOtherTypesPassThrough() {
        Map<String, Object> input = new HashMap<>();
        input.put("item_count", 3);
        input.put("promo_code", null);
        assertThat(KeyCaseConverter.toCamelCase(input))
                .containsEntry("itemCount", 3)
                .containsEntry("promoCode", null);
    }

    @Test
    void nullInputOrNullKeyIsRejected() {
        assertThatThrownBy(() -> KeyCaseConverter.toCamelCase((Map<String, Object>) null))
                .isInstanceOf(NullPointerException.class);
        Map<String, Object> withNullKey = new HashMap<>();
        withNullKey.put(null, "x");
        assertThatThrownBy(() -> KeyCaseConverter.toCamelCase(withNullKey)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stretchListsOfMapsAreConvertedToo() {
        Map<String, Object> input = Map.of(
                "order_lines", List.of(
                        Map.of("product_name", "Pizza", "unit_price", "9.50"),
                        Map.of("product_name", "Cola", "extra_info", Map.of("is_cold", "yes")),
                        "free_text_stays_as_is"));

        assertThat(KeyCaseConverter.toCamelCase(input)).isEqualTo(Map.of(
                "orderLines", List.of(
                        Map.of("productName", "Pizza", "unitPrice", "9.50"),
                        Map.of("productName", "Cola", "extraInfo", Map.of("isCold", "yes")),
                        "free_text_stays_as_is")));
    }

    @Test
    void valuesAreNeverConvertedOnlyKeys() {
        assertThat(KeyCaseConverter.toCamelCase(Map.of("status_code", "out_for_delivery")))
                .isEqualTo(Map.of("statusCode", "out_for_delivery"));
    }
}
