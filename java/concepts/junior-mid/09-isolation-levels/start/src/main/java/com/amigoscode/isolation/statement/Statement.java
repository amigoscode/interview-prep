package com.amigoscode.isolation.statement;

import java.util.List;

/** What the customer is shown: one line per account, then how many accounts and the total. */
public record Statement(String owner, List<Line> lines, long accountCount, long total) {

    public record Line(String account, long balance) {
    }

    public long sumOfLines() {
        return lines.stream().mapToLong(Line::balance).sum();
    }

    /** A statement a customer can trust: the lines are all the accounts, and they add up to the total. */
    public boolean addsUp() {
        return lines.size() == accountCount && sumOfLines() == total;
    }
}
