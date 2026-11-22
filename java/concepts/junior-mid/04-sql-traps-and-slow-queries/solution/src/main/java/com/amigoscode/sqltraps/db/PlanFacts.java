package com.amigoscode.sqltraps.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulls the facts that matter out of a plan and words them as plain lines. Used by ./demo and
 * ./inspect, so both tools describe a plan the same way.
 */
public final class PlanFacts {

    /** An estimate this many times off is worth pointing out. */
    private static final double FAR_OFF = 10;

    public static List<String> lines(Plan plan) {
        List<String> lines = new ArrayList<>();
        long returned = plan.rowsReturned();
        long read = plan.rowsRead();

        lines.add(label("Rows returned") + number(returned));
        lines.add(label("Rows read from tables") + number(read)
                + (returned > 0 && read >= 1000 && read >= 10 * returned ? "   (" + number(read / returned) + " read for every row returned)" : ""));

        lines.add("Table reads:");
        for (Plan.Node scan : plan.scans()) {
            StringBuilder line = new StringBuilder("    " + scan + ": ");
            if (scan.loops() == 0) {
                line.append("never ran");
            } else {
                if (scan.loops() > 1) {
                    line.append("ran ").append(number(scan.loops())).append(" times, ");
                }
                line.append("read ").append(number(scan.rowsRead())).append(scan.rowsRead() == 1 ? " row" : " rows");
                if (scan.removedByFilter() > 0) {
                    line.append(", threw away ").append(number(scan.removedByFilter())).append(" (Rows Removed by Filter)");
                }
            }
            lines.add(line.toString());
        }

        List<Plan.Node> joins = plan.nodes().stream().filter(Plan.Node::isJoin).toList();
        for (Plan.Node join : joins) {
            lines.add(label("Join") + join.type() + ", join type " + join.joinType() + ": " + meaning(join.joinType()));
        }
        if (!plan.subPlans().isEmpty()) {
            lines.add(label("Subqueries run per row") + String.join(", ", plan.subPlans())
                    + " (look at loops= on the steps below them)");
        }

        Plan.Node worst = plan.worstEstimate();
        if (worst.estimateFactor() >= FAR_OFF) {
            lines.add(label("Estimate vs actual") + "FAR OFF. " + worst + ": the planner expected " + rows(worst.estimatedRows())
                    + ", got " + number(worst.actualRows()) + " (" + factor(worst.estimateFactor()) + " times off)");
        } else if (worst.estimateFactor() < 1.5) {
            lines.add(label("Estimate vs actual") + "close. Every step returned about as many rows as the planner expected");
        } else {
            lines.add(label("Estimate vs actual") + "close. The worst step is " + worst + ": expected "
                    + rows(worst.estimatedRows()) + ", got " + number(worst.actualRows()));
        }

        for (Plan.Node sort : plan.sorts()) {
            lines.add(label("Sort") + sort.sortMethod() + (sort.sortOnDisk()
                    ? ", SPILLED TO DISK (" + number(sort.sortKilobytes()) + " kB of temporary files)"
                    : ", in memory (" + number(sort.sortKilobytes()) + " kB)"));
        }

        lines.add(label("Result size (estimate)") + number(returned) + " rows x " + plan.rowWidth() + " bytes = "
                + bytes(returned * plan.rowWidth()));
        lines.add(label("Pages touched (8 kB each)") + number(plan.pages()));
        Plan.Node slowest = plan.slowest();
        lines.add(plan.executionMillis() < 1
                ? label("Time in the database") + millis(plan.executionMillis())
                : label("Where the time went") + slowest + ": " + millis(slowest.selfMillis()) + " of "
                        + millis(plan.executionMillis()));
        return lines;
    }

    private static String meaning(String joinType) {
        return switch (joinType) {
            case "Inner" -> "a row needs a match on both sides, rows without one are dropped";
            case "Left", "Right" -> "an outer join, rows without a match are kept and filled with NULL";
            case "Full" -> "an outer join that keeps unmatched rows of both sides";
            case "Semi" -> "keeps a row if at least one match exists (EXISTS, IN)";
            case "Anti", "Right Anti" -> "keeps a row only if no match exists (NOT EXISTS)";
            default -> joinType;
        };
    }

    private static String label(String text) {
        return String.format("%-27s", text + ":");
    }

    private static String rows(long rows) {
        return number(rows) + (rows == 1 ? " row" : " rows");
    }

    private static String factor(double factor) {
        return factor >= 100 ? number(Math.round(factor)) : String.format(Locale.US, "%.0f", factor);
    }

    public static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static String millis(double millis) {
        if (millis >= 100) {
            return number(Math.round(millis)) + " ms";
        }
        return String.format(Locale.US, millis >= 1 ? "%.1f ms" : "%.2f ms", millis);
    }

    public static String bytes(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024) {
            return String.format(Locale.US, "%.1f kB", bytes / 1024.0);
        }
        return bytes + " bytes";
    }

    private PlanFacts() {
    }
}
