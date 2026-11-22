package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.receipt.ReceiptService;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 6. Unit test: plain Java, no Spring, no threads. It reads the class with reflection. */
@Tag("task6")
class ReceiptServiceTest {

    @Test
    @DisplayName("Receipt: ReceiptService is stateless: every field is final, so no request can leave data behind")
    void keepsNoRequestDataInFields() {
        List<String> notFinal = Arrays.stream(ReceiptService.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isFinal(field.getModifiers()))
                .map(Field::getName)
                .toList();

        assertThat(notFinal)
                .withFailMessage("ReceiptService has fields that every request thread can write: %s. "
                        + "Data that belongs to one request must live in local variables and parameters.", notFinal)
                .isEmpty();
    }
}
