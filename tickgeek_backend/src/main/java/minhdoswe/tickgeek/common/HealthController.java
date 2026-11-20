package minhdoswe.tickgeek.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;

/**
 * Health check, used by the host's health probe and by the frontend to wake a sleeping instance.
 * It runs one trivial query, so it reports unhealthy when the database is unreachable, and the
 * first call also wakes a sleeping database before a real request needs it.
 */
@RestController
public class HealthController {

    private final EntityManager entityManager;

    public HealthController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        entityManager.createNativeQuery("SELECT 1").getSingleResult();
        return Map.of("status", "UP");
    }
}
