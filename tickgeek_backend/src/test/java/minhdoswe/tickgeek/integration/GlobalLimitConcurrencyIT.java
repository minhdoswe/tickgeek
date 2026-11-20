package minhdoswe.tickgeek.integration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The system-wide count runs without a lock, so a burst can pass the cap by about the number of
 * requests in flight, here at most one per parallel thread (specs/001-request-code/research.md R5).
 */
@TestPropertySource(properties = "tickgeek.auth.limits.global-per-hour=" + GlobalLimitConcurrencyIT.CAP)
class GlobalLimitConcurrencyIT extends AbstractIntegrationTest {

    static final int CAP = 10;

    /**
     * Proves a burst of parallel requests can pass the system-wide cap only by a small, known
     * amount, so the email bill stays bounded even under attack.
     * <p>
     * Cases: 30 parallel requests from 30 IPs, cap 10 → between 10 and 18 (cap + one per thread)
     * accepted, and exactly one stored code per accepted request. The overshoot comes from the
     * unlocked count (specs/001-request-code/research.md R5, R14).
     */
    @Test
    void systemWideCapIsExceededByAtMostTheParallelThreads() throws Exception {
        // When: 30 requests for different emails from different IPs run in parallel
        List<Integer> statuses = CodeRequestConcurrencyIT.runInParallel(30, i ->
                postJson("/api/auth/code-requests", "{\"email\":\"user%d@x.com\"}".formatted(i), "10.0.0." + i)
                        .andReturn().getResponse().getStatus());

        // Then: at least the cap, and at most the cap plus one per thread, were accepted
        long accepted = statuses.stream().filter(status -> status == 202).count();
        assertThat(accepted).isBetween((long) CAP, (long) CAP + CodeRequestConcurrencyIT.THREADS);
        // Then: every accepted request stored exactly one code
        assertThat(count("SELECT count(*) FROM verification_codes")).isEqualTo((int) accepted);
    }
}
