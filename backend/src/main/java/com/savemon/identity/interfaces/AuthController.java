package com.savemon.identity.interfaces;

import com.savemon.identity.application.LoginUser;
import com.savemon.identity.application.RegisterUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@RestController
@ConditionalOnBean({RegisterUser.class, LoginUser.class})
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegisterUser registerUser;
    private final LoginUser loginUser;

    public AuthController(RegisterUser registerUser, LoginUser loginUser) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUser.AuthenticationSuccessResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterUser.AuthenticationSuccessResponse response = registerUser.execute(
                request.email(), request.password(), request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginUser.AuthenticationSuccessResponse> login(
            @Valid @RequestBody LoginRequest request) {
        LoginUser.AuthenticationSuccessResponse response = loginUser.execute(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    public record RegisterRequest(
            @NotBlank(message = "email must not be blank") String email,
            @NotBlank(message = "password must not be blank") @Size(min = 8, max = 128, message = "password length must be between 8 and 128 characters") String password,
            @NotBlank(message = "displayName must not be blank") @Size(max = 100, message = "displayName must be 100 characters or fewer") String displayName) { }

    public record LoginRequest(
            @NotBlank(message = "email must not be blank") String email,
            @NotBlank(message = "password must not be blank") @Size(min = 8, max = 128, message = "password length must be between 8 and 128 characters") String password) { }
}
