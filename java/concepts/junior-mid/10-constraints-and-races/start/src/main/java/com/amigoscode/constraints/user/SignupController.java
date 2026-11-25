package com.amigoscode.constraints.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signups")
public class SignupController {

    public record SignupRequest(String email, String displayName) {
    }

    public record SignupResponse(long id, String email) {
    }

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    /** No validation here, on purpose: Task 7 shows what the database does when the application forgets. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signUp(@RequestBody SignupRequest request) {
        User user = signupService.signUp(request.email(), request.displayName());
        return new SignupResponse(user.getId(), user.getEmail());
    }
}
