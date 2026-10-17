package com.savemon.identity.application;

import java.util.UUID;

/** Identity-owned query used at protected request entry. Missing subjects are inactive. */
public interface SubjectStatusQuery {
    boolean isActive(UUID subject);
}
