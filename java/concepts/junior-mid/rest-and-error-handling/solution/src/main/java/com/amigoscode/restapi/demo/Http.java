package com.amigoscode.restapi.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.springframework.http.HttpStatus;

/** A small HTTP client for the demo and inspect tools. It can print each request and response. */
final class Http {

    /** The response headers worth printing. Everything else (Date, Connection...) is noise here. */
    private static final List<String> HEADERS_THAT_MATTER = List.of("Content-Type", "Location", "Allow");

    record Reply(int status, HttpResponse<String> raw) {

        String body() {
            return raw.body() == null ? "" : raw.body();
        }

        String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }

        String contentType() {
            String value = header("Content-Type");
            return value == null ? "" : value.replaceAll(";.*", "");
        }

        /** "200 OK", "404 Not Found"... */
        String statusLine() {
            HttpStatus known = HttpStatus.resolve(status);
            return status + (known == null ? "" : " " + known.getReasonPhrase());
        }
    }

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String baseUrl;

    Http(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Sends a request without printing anything. body may be null. */
    Reply send(String method, String path, String body) throws Exception {
        return send(method, path, body, "application/json");
    }

    Reply send(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response);
    }

    /** Sends a request and prints it, with the full response. */
    Reply show(String method, String path, String body) throws Exception {
        printRequest(method, path, body);
        Reply reply = send(method, path, body);
        printReply(reply, false);
        return reply;
    }

    /** Sends a request and prints it, with the response body cut down to one line. */
    Reply showShort(String method, String path, String body) throws Exception {
        printRequest(method, path, body);
        Reply reply = send(method, path, body);
        printReply(reply, true);
        return reply;
    }

    static void printRequest(String method, String path, String body) {
        System.out.println("  -> " + method + " " + path);
        if (body != null) {
            body.strip().lines().forEach(line -> System.out.println("     " + line));
        }
    }

    static void printReply(Reply reply, boolean oneLine) {
        System.out.println("  <- " + reply.statusLine());
        for (String name : HEADERS_THAT_MATTER) {
            String value = reply.header(name);
            if (value != null) {
                System.out.println("     " + name + ": " + value);
            }
        }
        if (reply.body().isBlank()) {
            System.out.println("     (no body)");
        } else if (oneLine) {
            String body = reply.body().strip().replaceAll("\\s+", " ");
            System.out.println("     " + (body.length() > 110 ? body.substring(0, 110) + "..." : body));
        } else {
            Json.pretty(reply.body()).lines().forEach(line -> System.out.println("     " + line));
        }
        System.out.println();
    }
}
