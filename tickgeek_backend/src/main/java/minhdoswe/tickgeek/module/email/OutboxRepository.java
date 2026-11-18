package minhdoswe.tickgeek.module.email;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
}
