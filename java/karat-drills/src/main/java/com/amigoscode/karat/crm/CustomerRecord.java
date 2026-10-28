package com.amigoscode.karat.crm;

import java.util.Objects;

/** Data about a single customer entry in the CRM. The signup day is a day number. */
public class CustomerRecord {
    public final int recordId;
    public final String name;
    public final String email;
    public final String phone;
    public final String city;
    public final int signupDay;

    public CustomerRecord(int recordId, String name, String email, String phone, String city, int signupDay) {
        this.recordId = recordId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.city = city;
        this.signupDay = signupDay;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CustomerRecord r)) {
            return false;
        }
        return recordId == r.recordId
                && Objects.equals(name, r.name)
                && Objects.equals(email, r.email)
                && Objects.equals(phone, r.phone)
                && Objects.equals(city, r.city)
                && signupDay == r.signupDay;
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, name, email, phone, city, signupDay);
    }

    @Override
    public String toString() {
        return "CustomerRecord " + recordId + " (name=" + name + ", email=" + email + ", phone=" + phone
                + ", city=" + city + ", signupDay=" + signupDay + ")";
    }
}
