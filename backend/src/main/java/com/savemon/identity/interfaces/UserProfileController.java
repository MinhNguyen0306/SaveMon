package com.savemon.identity.interfaces;

import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.identity.application.GetCurrentUser;
import com.savemon.identity.application.UserRepository;
import com.savemon.identity.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnBean(EntityManagerFactory.class)
@RequestMapping("/api/v1")
public class UserProfileController {
    private final UserRepository users;
    private final CurrentUserProvider currentUser;
    private final GetCurrentUser getCurrentUser;

    public UserProfileController(UserRepository users, CurrentUserProvider currentUser) {
        this.users = users;
        this.currentUser = currentUser;
        this.getCurrentUser = new GetCurrentUser(users, currentUser);
    }

    @GetMapping("/me")
    public ProfileResponse getCurrentProfile() {
        try {
            User user = getCurrentUser.execute();
            return new ProfileResponse(user.id(), user.email().value(), user.displayName());
        } catch (GetCurrentUser.UserNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found.");
        } catch (GetCurrentUser.UnauthenticatedException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
    }

    @PatchMapping("/me")
    public ProfileResponse updateCurrentProfile(@Valid @RequestBody UpdateProfileRequest request) {
        User user = getCurrentUser.execute();
        user.updateDisplayName(request.displayName(), Instant.now());
        User savedUser = users.save(user);
        return new ProfileResponse(savedUser.id(), savedUser.email().value(), savedUser.displayName());
    }

    public record ProfileResponse(UUID id, String email, String displayName) { }

    public record UpdateProfileRequest(
            @NotBlank(message = "displayName must not be blank")
            @Size(max = 100, message = "displayName must be 100 characters or fewer")
            String displayName) { }
}
