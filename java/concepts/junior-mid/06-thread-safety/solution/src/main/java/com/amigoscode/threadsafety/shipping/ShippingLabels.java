package com.amigoscode.threadsafety.shipping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Prints the ship date on a label: "Ships on 05 Mar 2026". */
@Service
public class ShippingLabels {

    // DateTimeFormatter is immutable: it keeps nothing between calls, so one instance can serve every thread.
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public String labelFor(LocalDate shipDate) {
        return "Ships on " + FORMAT.format(shipDate);
    }
}
