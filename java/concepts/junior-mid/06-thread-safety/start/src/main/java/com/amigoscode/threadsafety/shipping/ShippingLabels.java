package com.amigoscode.threadsafety.shipping;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.springframework.stereotype.Service;

/** Prints the ship date on a label: "Ships on 05 Mar 2026". */
@Service
public class ShippingLabels {

    // TODO Task 6: SimpleDateFormat keeps the date it is working on in its own fields. It is not
    // thread-safe, and this one instance is used by every request thread.
    private final SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

    public ShippingLabels() {
        format.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    public String labelFor(LocalDate shipDate) {
        Date date = Date.from(shipDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        return "Ships on " + format.format(date);
    }
}
