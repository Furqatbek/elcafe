package com.elcafe.modules.push.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.push.entity.PushConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PushConfigRepositoryTest {

    @Autowired
    private PushConfigRepository repo;

    @Autowired
    private EntityManager em;

    private PushConfig createConfig(String key, String value, String description) {
        PushConfig config = new PushConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        em.persist(config);
        return config;
    }

    @Test
    void findByConfigKey_existingKey_returnsConfig() {
        createConfig("vapid.public.key", "BPubKeyValue123", "VAPID public key");
        createConfig("vapid.private.key", "PrivKeyValue456", "VAPID private key");
        em.flush();
        em.clear();

        Optional<PushConfig> result = repo.findByConfigKey("vapid.public.key");

        assertTrue(result.isPresent());
        assertEquals("vapid.public.key", result.get().getConfigKey());
        assertEquals("BPubKeyValue123", result.get().getConfigValue());
        assertEquals("VAPID public key", result.get().getDescription());
    }

    @Test
    void findByConfigKey_nonExistingKey_returnsEmpty() {
        createConfig("vapid.public.key", "BPubKeyValue123", "VAPID public key");
        em.flush();
        em.clear();

        Optional<PushConfig> result = repo.findByConfigKey("nonexistent.key");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByConfigKey_returnsCorrectConfigAmongMultiple() {
        createConfig("config.a", "valueA", "Description A");
        createConfig("config.b", "valueB", "Description B");
        em.flush();
        em.clear();

        Optional<PushConfig> resultA = repo.findByConfigKey("config.a");
        Optional<PushConfig> resultB = repo.findByConfigKey("config.b");

        assertTrue(resultA.isPresent());
        assertEquals("valueA", resultA.get().getConfigValue());

        assertTrue(resultB.isPresent());
        assertEquals("valueB", resultB.get().getConfigValue());
    }
}
