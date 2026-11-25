package com.amigoscode.isolation.demo;

import java.io.PrintStream;

/** Prints what each session does, one step per line. Session B is indented so the two are easy to tell apart. */
final class Timeline {

    private final PrintStream out;

    /** @param out where to print, or null to print nothing (the tests) */
    Timeline(PrintStream out) {
        this.out = out;
    }

    /** Something a session does. */
    void step(String session, String text) {
        print(indent(session) + session + " | " + text);
    }

    /** What came back. */
    void result(String session, String text) {
        print(indent(session) + session + " |   -> " + text);
    }

    /** What the application does with a value, outside the database. */
    void comment(String session, String text) {
        print(indent(session) + session + " |   # " + text);
    }

    void note(String text) {
        print("");
        print("  " + text);
        print("");
    }

    void print(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    private static String indent(String session) {
        return session.equals("A") ? "  " : "          ";
    }
}
