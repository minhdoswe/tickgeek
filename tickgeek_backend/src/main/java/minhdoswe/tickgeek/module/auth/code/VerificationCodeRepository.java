package minhdoswe.tickgeek.module.auth.code;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE verification_codes SET status = 'INVALIDATED'
            WHERE email = :email AND status IN ('PENDING', 'ACTIVE')
            """, nativeQuery = true)
    int invalidateLive(String email);

    Optional<VerificationCode> findByOutboxId(UUID outboxId);

    /**
     * Makes this one code live, only if it is still PENDING. The status check is in the UPDATE, so
     * a code replaced at the same moment is left alone and 0 is returned
     * (specs/002-sending-code/research.md R3).
     */
    @Modifying
    @Query(value = """
            UPDATE verification_codes SET status = 'ACTIVE', sent_at = :sentAt, expires_at = :expiresAt
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int activatePending(Long id, Instant sentAt, Instant expiresAt);

    /** Invalidates this one code, only if it is still PENDING (specs/002-sending-code/research.md R3). */
    @Modifying
    @Query(value = """
            UPDATE verification_codes SET status = 'INVALIDATED'
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int invalidatePending(Long id);

    @Query("select max(c.createdAt) from VerificationCode c where c.email = :email")
    Optional<Instant> findLatestCreatedAt(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant since);

    long countByRequestSourceAndCreatedAtAfter(String requestSource, Instant since);

    long countByCreatedAtAfter(Instant since);

    @Query("select min(c.createdAt) from VerificationCode c where c.email = :email and c.createdAt > :since")
    Optional<Instant> findOldestByEmailSince(String email, Instant since);

    @Query("select min(c.createdAt) from VerificationCode c where c.requestSource = :source and c.createdAt > :since")
    Optional<Instant> findOldestBySourceSince(String source, Instant since);

    @Query("select min(c.createdAt) from VerificationCode c where c.createdAt > :since")
    Optional<Instant> findOldestSince(Instant since);
}
