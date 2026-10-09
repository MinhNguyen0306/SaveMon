package com.savemon.identity.infrastructure.persistence;

import com.savemon.identity.application.UserRepository;
import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.persistence.EntityManagerFactory;

@Repository
@ConditionalOnBean(EntityManagerFactory.class)
public class JpaUserRepositoryAdapter implements UserRepository {
    private final SpringDataUserRepository repository;
    public JpaUserRepositoryAdapter(SpringDataUserRepository repository) { this.repository = repository; }
    @Override public Optional<User> findById(UUID id) { return repository.findById(id).map(UserJpaEntity::toDomain); }
    @Override public Optional<User> findByEmail(EmailAddress email) { return repository.findByEmail(email.value()).map(UserJpaEntity::toDomain); }
    @Override public User save(User user) { return repository.save(new UserJpaEntity(user)).toDomain(); }
}
