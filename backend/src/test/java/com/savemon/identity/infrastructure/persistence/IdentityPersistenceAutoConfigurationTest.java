package com.savemon.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.savemon.identity.application.LoginUser;
import com.savemonidentity.autoconfigure.IdentityPersistenceAutoConfiguration;
import com.savemon.identity.application.RefreshSessionRepository;
import com.savemon.identity.application.RefreshSessions;
import com.savemon.identity.application.RegisterUser;
import com.savemon.identity.application.UserRepository;
import com.savemon.identity.interfaces.AuthController;
import com.savemon.identity.interfaces.UserProfileController;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IdentityPersistenceAutoConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityPersistenceAutoConfiguration.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(RefreshSessionRepository.class, () -> mock(RefreshSessionRepository.class))
            .withBean(RegisterUser.class, () -> mock(RegisterUser.class))
            .withBean(LoginUser.class, () -> mock(LoginUser.class))
            .withBean(RefreshSessions.class, () -> mock(RefreshSessions.class))
            .withBean(UserProfileController.class, () -> mock(UserProfileController.class));

    @Test
    void registersAuthControllerWhenJpaIsPresent() {
        context.withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(app -> assertThat(app).hasSingleBean(AuthController.class));
    }

    @Test
    void doesNotRegisterAuthControllerWithoutJpa() {
        context.run(app -> assertThat(app).doesNotHaveBean(AuthController.class));
    }
}

