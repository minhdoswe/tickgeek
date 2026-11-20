package minhdoswe.tickgeek.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import minhdoswe.tickgeek.module.auth.code.CodeRequestLockRepository;
import minhdoswe.tickgeek.module.auth.code.TooManyRequestsException;
import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-email limits hold under parallel requests because of the per-email advisory lock, and
 * a held lock blocks only the requests that share its email. The per-source count runs without a
 * lock, so a burst can pass that cap by at most one request per parallel thread
 * (specs/001-request-code/research.md R5, R14).
 */
class CodeRequestConcurrencyIT extends AbstractIntegrationTest {

    static final int THREADS = 8;
    /** tickgeek.auth.limits.per-source-per-hour in application.yaml. */
    private static final int PER_SOURCE_CAP = 20;
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private CodeRequestLockRepository lock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Proves two requests for one email at the same moment leave only one live code, so a
     * double-click or a race can't hand out two valid codes at once.
     * <p>
     * Cases: 2 parallel requests for ana@x.com → one 202, one 429 (cooldown), one live code. The
     * email lock makes them run one after the other (specs/001-request-code/research.md R5, R14;
     * specs/001-request-code/spec.md FR-006).
     */
    @Test
    void parallelRequestsForOneEmailLeaveOneLiveCode() throws Exception {
        // When: two requests for the same email run in parallel
        List<Integer> statuses = runInParallel(2, i -> requestStatus("ana@x.com", DEFAULT_SOURCE));

        // Then: one 202 and one 429, and exactly one live code in the database
        assertThat(statuses).containsExactlyInAnyOrder(202, 429);
        assertThat(count("SELECT count(*) FROM verification_codes WHERE status IN ('PENDING','ACTIVE')")).isEqualTo(1);
    }

    /**
     * Proves a burst from one IP can pass the per-IP cap only by a small, known amount, so one
     * machine firing requests in parallel still can't get many more than 20 codes an hour.
     * <p>
     * Cases: 30 parallel requests for different emails from one IP, cap 20 → between 20 and 28
     * (cap + one per thread) accepted, the rest 429, and exactly one stored code per accepted
     * request. The overshoot comes from the unlocked per-source count
     * (specs/001-request-code/research.md R5, R14; specs/001-request-code/spec.md FR-020).
     */
    @Test
    void oneSourceExceedsItsCapByAtMostTheParallelThreads() throws Exception {
        // When: 30 requests for different emails from the same IP run in parallel
        List<Integer> statuses = runInParallel(30, i -> requestStatus("user" + i + "@x.com", DEFAULT_SOURCE));

        // Then: every request was accepted or refused by a limit
        assertThat(statuses).allMatch(status -> status == 202 || status == 429);
        // Then: at least the cap, and at most the cap plus one per thread, were accepted
        long accepted = statuses.stream().filter(status -> status == 202).count();
        assertThat(accepted).isBetween((long) PER_SOURCE_CAP, (long) PER_SOURCE_CAP + THREADS);
        // Then: every accepted request stored exactly one code
        assertThat(count("SELECT count(*) FROM verification_codes")).isEqualTo((int) accepted);
    }

    /**
     * Proves a held lock blocks only requests for the same email, so one user's slow request
     * never makes unrelated users wait.
     * <p>
     * Cases: lock held for ana@x.com → another ana request waits, bob@x.com from another IP →
     * 202 at once; after release, ana's request → 202
     * (specs/001-request-code/research.md R5, R14).
     */
    @Test
    void aHeldLockBlocksOnlyRequestsForTheSameEmail() throws Exception {
        Latches latches = new Latches(new CountDownLatch(1), new CountDownLatch(1));
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            try {
                // Given: another thread holds the lock for ana@x.com in an open transaction
                pool.submit(() -> holdLock("ana@x.com", "198.51.100.1", latches));
                assertThat(latches.held().await(LOCK_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
                // Given: a second request for ana@x.com, from another IP, is waiting on that lock
                Future<Integer> waiting = pool.submit(() -> requestStatus("ana@x.com", "198.51.100.2"));
                await().atMost(LOCK_WAIT_TIMEOUT).until(() -> sessionsWaitingOnAdvisoryLocks() == 1);

                // When / Then: bob@x.com from a third IP is accepted while ana's request still waits
                assertThat(requestStatus("bob@x.com", "198.51.100.3")).isEqualTo(202);
                assertThat(waiting.isDone()).isFalse();
                // When / Then: once the lock is released, ana's waiting request is accepted
                latches.release().countDown();
                assertThat(waiting.get()).isEqualTo(202);
            } finally {
                latches.release().countDown();
            }
        }
    }

    /**
     * Proves a request whose email lock is held too long is refused instead of waiting, so one
     * stuck transaction can't make every retry for that email hold a pooled connection until the
     * pool is empty and all code requests fail.
     * <p>
     * Cases: lock for ana@x.com held past the 2 s lock_timeout (application.yaml) → another ana
     * request gets 429 TOO_MANY_REQUESTS with the generic message and no Retry-After; no code is
     * stored (specs/001-request-code/research.md R5;
     * specs/001-request-code/contracts/auth-api.md 429).
     */
    @Test
    void aRequestThatCannotGetTheLockInTimeIsRefused() throws Exception {
        Latches latches = new Latches(new CountDownLatch(1), new CountDownLatch(1));
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            try {
                // Given: another thread holds the lock for ana@x.com and doesn't let go
                pool.submit(() -> holdLock("ana@x.com", "198.51.100.1", latches));
                assertThat(latches.held().await(LOCK_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

                // When / Then: a second request for ana@x.com gives up after lock_timeout with the generic 429
                postJson("/api/auth/code-requests", "{\"email\":\"ana@x.com\"}", "198.51.100.2")
                        .andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.error").value(TooManyRequestsException.CODE))
                        .andExpect(jsonPath("$.message").value(TooManyRequestsException.GENERIC_MESSAGE))
                        .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
            } finally {
                latches.release().countDown();
            }
        }
        // Then: the refused request stored no code
        assertThat(count("SELECT count(*) FROM verification_codes")).isZero();
    }

    /** Holds the lock for {@code email} in an open transaction until released. */
    private Void holdLock(String email, String source, Latches latches) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            lock.acquire(email);
            latches.held().countDown();
            awaitQuietly(latches.release());
        });
        return null;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private int sessionsWaitingOnAdvisoryLocks() {
        return count("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock' AND wait_event = 'advisory'
                """);
    }

    int requestStatus(String email, String source) throws Exception {
        return postJson("/api/auth/code-requests", "{\"email\":\"%s\"}".formatted(email), source)
                .andReturn().getResponse().getStatus();
    }

    static List<Integer> runInParallel(int count, IndexedRequest request) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Callable<Integer>> tasks = IntStream.range(0, count)
                    .<Callable<Integer>>mapToObj(i -> () -> request.run(i))
                    .toList();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(tasks)) {
                statuses.add(result.get());
            }
            return statuses;
        }
    }

    private record Latches(CountDownLatch held, CountDownLatch release) {
    }

    @FunctionalInterface
    interface IndexedRequest {
        int run(int index) throws Exception;
    }
}
