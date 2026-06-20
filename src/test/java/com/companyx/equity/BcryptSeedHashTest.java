package com.companyx.equity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BcryptSeedHashTest {

    private static final String SEED_HASH =
            "$2a$10$qeZITzScAMsR06CSyZD4cuNZJIGlD0OBuQJypRztFbPc2iupuzENG";

    @Test
    void seedHashMatchesDocumentedPassword() {
        assertTrue(new BCryptPasswordEncoder().matches("password", SEED_HASH));
    }
}
