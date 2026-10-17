package com.savemon.identity.infrastructure.persistence;

import com.savemon.identity.application.SubjectStatusQuery;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSubjectStatusQuery implements SubjectStatusQuery {
    private final JdbcTemplate jdbc;

    public JdbcSubjectStatusQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActive(UUID subject) {
        Boolean active = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE id = ? AND status = 'ACTIVE')",
                Boolean.class, subject);
        return Boolean.TRUE.equals(active);
    }
}
