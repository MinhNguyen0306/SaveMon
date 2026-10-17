package com.savemonidentity.autoconfigure;

import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.identity.application.LoginUser;
import com.savemon.identity.application.RefreshSessionRepository;
import com.savemon.identity.application.RefreshSessions;
import com.savemon.identity.application.RegisterUser;
import com.savemon.identity.application.TokenIssuer;
import com.savemon.identity.application.UserRepository;
import com.savemon.identity.interfaces.AuthController;
import com.savemon.identity.infrastructure.persistence.JpaUserRepositoryAdapter;
import com.savemon.identity.infrastructure.persistence.JdbcRefreshSessionRepository;
import com.savemon.identity.infrastructure.persistence.SpringDataUserRepository;
import com.savemon.identity.interfaces.UserProfileController;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/** Registers DB-backed identity components after JPA rather than relying on component-scan bean ordering. */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManagerFactory.class)
public class IdentityPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserRepository.class)
    UserRepository userRepository(SpringDataUserRepository repository) {
        return new JpaUserRepositoryAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(RefreshSessionRepository.class)
    RefreshSessionRepository refreshSessionRepository(JdbcTemplate jdbc) {
        return new JdbcRefreshSessionRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(RegisterUser.class)
    RegisterUser registerUser(UserRepository users, PasswordEncoder passwords, RefreshSessions sessions,
            PlatformTransactionManager transactions) {
        return new RegisterUser(users, passwords, sessions, transactions);
    }

    @Bean
    @ConditionalOnMissingBean(LoginUser.class)
    LoginUser loginUser(UserRepository users, PasswordEncoder passwords, RefreshSessions sessions) {
        return new LoginUser(users, passwords, sessions);
    }

    @Bean
    @ConditionalOnMissingBean(RefreshSessions.class)
    RefreshSessions refreshSessions(RefreshSessionRepository sessions, UserRepository users, TokenIssuer issuer,
            PlatformTransactionManager transactions) {
        return new RefreshSessions(sessions, users, issuer, transactions);
    }

    @Bean
    @ConditionalOnMissingBean(AuthController.class)
    AuthController authController(RegisterUser registerUser, LoginUser loginUser, RefreshSessions sessions) {
        return new AuthController(registerUser, loginUser, sessions);
    }

    @Bean
    @ConditionalOnMissingBean(UserProfileController.class)
    UserProfileController userProfileController(UserRepository users, CurrentUserProvider currentUser) {
        return new UserProfileController(users, currentUser);
    }
}

