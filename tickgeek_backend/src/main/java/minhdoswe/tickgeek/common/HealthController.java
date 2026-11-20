package minhdoswe.tickgeek.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;

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
