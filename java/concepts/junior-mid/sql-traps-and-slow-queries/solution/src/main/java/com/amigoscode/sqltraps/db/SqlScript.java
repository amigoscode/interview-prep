package com.amigoscode.sqltraps.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads a .sql file from the classpath and splits it into statements. */
public final class SqlScript {

    /** The file's text. */
    public static String read(String resource) {
        try (InputStream in = SqlScript.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from src/main/resources");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The statements in a script: "--" comments are removed, statements end with a semicolon. */
    public static List<String> statements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.lines().toList()) {
            int comment = line.indexOf("--");
            withoutComments.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String statement : withoutComments.toString().split(";")) {
            String trimmed = statement.strip().replaceAll("\\s+", " ");
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    private SqlScript() {
    }
}
