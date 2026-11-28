package minhdoswe.tickgeek.module.email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    /**
     * Loads the job and locks it until the transaction ends. Empty when it does not exist or
     * another transaction holds it (specs/002-sending-code/research.md R1).
     */
    @Query(value = "SELECT * FROM outboxes WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Outbox> lockById(UUID id);

    /** Up to {@code limit} PENDING jobs due by {@code now}, oldest first; no lock is taken. */
    @Query(value = """
            SELECT id FROM outboxes
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit""", nativeQuery = true)
    List<UUID> findDueIds(Instant now, int limit);
}
