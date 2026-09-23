package com.amigoscode.interview.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Drives the real server over HTTP, exactly as curl would, so the whole filter chain is exercised.
 * The test credential is supplied here and is worthless: the build needs no real secret.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // bcrypt hash of "test-password", cost 4 to keep the tests fast
        properties = "app.admin.password-hash=$2y$04$/al83hyGe4DISaEbpHhB2OTwN8xbo9Fv0qi6Ax.TY0USM88VKiwzy")
class AuthenticationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void noCredentialsIsChallenged() throws Exception {
        HttpResponse<String> response = get("/", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                value -> assertThat(value).startsWith("Basic"));
    }

    @Test
    void correctCredentialsAreAccepted() throws Exception {
        HttpResponse<String> response = get("/", basic("admin:test-password"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hello, welcome to the secret storage case!");
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        assertThat(get("/", basic("admin:wrong")).statusCode()).isEqualTo(401);
    }

    @Test
    void wrongUsernameIsRejected() throws Exception {
        assertThat(get("/", basic("root:test-password")).statusCode()).isEqualTo(401);
    }

    @Test
    void theHashItselfIsNotAPassword() throws Exception {
        assertThat(get("/", basic("admin:$2y$04$/al83hyGe4DISaEbpHhB2OTwN8xbo9Fv0qi6Ax.TY0USM88VKiwzy")).statusCode()).isEqualTo(401);
    }

    @Test
    void malformedBase64IsRejectedNotAServerError() throws Exception {
        assertThat(get("/", "Basic %%%%").statusCode()).isEqualTo(401);
    }

    @Test
    void credentialsWithoutAColonAreRejectedNotAServerError() throws Exception {
        assertThat(get("/", basic("admin")).statusCode()).isEqualTo(401);
    }

    @Test
    void schemeIsCaseInsensitive() throws Exception {
        String lowercase = "basic " + encode("admin:test-password");

        assertThat(get("/", lowercase).statusCode()).isEqualTo(200);
    }

    @Test
    void nonAsciiPasswordIsDecodedAsUtf8() throws Exception {
        // Wrong password, but it must be decoded and rejected cleanly whatever the platform charset.
        assertThat(get("/", basic("admin:pässwörd")).statusCode()).isEqualTo(401);
    }

    @Test
    void anyOtherPathIsPrivateByDefault() throws Exception {
        assertThat(get("/anything", null).statusCode()).isEqualTo(401);
    }

    @Test
    void noSessionCookieIsIssued() throws Exception {
        HttpResponse<String> response = get("/", basic("admin:test-password"));

        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String credentials) {
        return "Basic " + encode(credentials);
    }

    private static String encode(String credentials) {
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

}
