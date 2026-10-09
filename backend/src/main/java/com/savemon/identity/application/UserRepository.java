package com.savemon.identity.application;

import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(EmailAddress email);

    User save(User user);
}
