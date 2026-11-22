package com.amigoscode.restapi.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A plain HTTP client for the integration tests: real requests to the real application. */
public final class Api {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record Reply(int status, HttpResponse<String> raw) {

        public String body() {
            return raw.body() == null ? "" : raw.body();
        }

        /** The body parsed as JSON. */
        public JsonNode json() {
            return JSON.readTree(body());
        }

        /** A header value, or null. */
        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }

        /** The Content-Type without parameters, for example "application/problem+json". Empty when absent. */
        public String contentType() {
            String value = header("Content-Type");
            return value == null ? "" : value.replaceAll(";.*", "").trim();
        }

        /** The names of the fields listed under "errors", in the order they were sent. */
        public List<String> invalidFields() {
            List<String> fields = new ArrayList<>();
            for (JsonNode error : json().path("errors")) {
                fields.add(error.path("field").asString(""));
            }
            return fields;
        }
    }

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String baseUrl;

    public Api(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Reply get(String path) {
        return send("GET", path, null);
    }

    public Reply post(String path, String json) {
        return send("POST", path, json);
    }

    public Reply put(String path, String json) {
        return send("PUT", path, json);
    }

    public Reply patch(String path, String json) {
        return send("PATCH", path, json);
    }

    public Reply delete(String path) {
        return send("DELETE", path, null);
    }

    /** body may be null. A body is always sent as application/json. */
    public Reply send(String method, String path, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response);
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
