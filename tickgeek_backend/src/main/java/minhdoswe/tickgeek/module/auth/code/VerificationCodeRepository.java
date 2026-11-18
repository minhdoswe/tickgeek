package minhdoswe.tickgeek.module.auth.code;

import java.time.Instant;
import java.util.Optional;

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
