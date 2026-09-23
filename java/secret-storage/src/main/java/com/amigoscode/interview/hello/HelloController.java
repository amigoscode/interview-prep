package com.amigoscode.interview.hello;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @Value("${app.admin-password}")
    private String adminPassword;

    @GetMapping("/")
    public ResponseEntity<String> hello(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"secret-storage\"")
                    .body("Unauthorized");
        }

        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        String[] parts = decoded.split(":", 2);

        if (parts.length == 2 && "admin".equals(parts[0]) && adminPassword.equals(parts[1])) {
            return ResponseEntity.ok("Hello, welcome to the secret storage case!");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Basic realm=\"secret-storage\"")
                .body("Invalid credentials");
    }

}
