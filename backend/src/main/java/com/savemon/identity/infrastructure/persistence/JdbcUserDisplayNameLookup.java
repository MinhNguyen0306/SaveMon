package com.savemon.identity.infrastructure.persistence;

import com.savemon.identity.application.UserDisplayNameLookup;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Identity-owned JDBC implementation of the cross-module display-name query port. */
@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcUserDisplayNameLookup implements UserDisplayNameLookup {
    private final JdbcTemplate jdbc;

    public JdbcUserDisplayNameLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID,String> displayNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        Map<UUID,String> names = new LinkedHashMap<>();
        jdbc.query("SELECT id,display_name FROM users WHERE id IN (" + placeholders + ")",
                (rs, row) -> names.put(rs.getObject("id", UUID.class), rs.getString("display_name")),
                userIds.toArray());
        return Map.copyOf(names);
    }
}
