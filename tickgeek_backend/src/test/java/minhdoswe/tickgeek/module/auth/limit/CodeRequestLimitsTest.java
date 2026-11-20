package minhdoswe.tickgeek.module.auth.limit;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import minhdoswe.tickgeek.module.auth.limit.implement.hourlylimit.extend.GlobalHourlyLimit;
import minhdoswe.tickgeek.module.auth.limit.implement.hourlylimit.extend.PerEmailHourlyLimit;
import minhdoswe.tickgeek.module.auth.limit.implement.hourlylimit.extend.PerSourceHourlyLimit;
import minhdoswe.tickgeek.module.auth.limit.implement.ResendCooldownLimit;
import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.module.auth.code.VerificationCodeRepository;
import minhdoswe.tickgeek.support.TestProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeRequestLimitsTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant WINDOW_START = NOW.minus(Duration.ofHours(1));
    private static final CodeRequestContext CONTEXT = new CodeRequestContext("ana@x.com", "1.2.3.4", NOW);

    private final VerificationCodeRepository codes = mock(VerificationCodeRepository.class);

    /**
     * Proves the resend cooldown lets one email get a new code at most once per 60 seconds, so
     * nobody can flood someone's inbox or run up email costs, and a real user can retry after a
     * minute.
     * <p>
     * Cases: 59 s → blocked, 1 s left, wait shown (the only limit allowed to show it);
     * 60 s → allowed; first request → allowed.
     */
    @Test
    void cooldownBlocksAt59SecondsAndAllowsAt60() {
        // Given: a real cooldown limit with the default 60 seconds
        ResendCooldownLimit limit = new ResendCooldownLimit(codes, TestProperties.auth());

        // Given: the last request was 59 s ago
        when(codes.findLatestCreatedAt("ana@x.com")).thenReturn(Optional.of(NOW.minusSeconds(59)));
        // When / Then: blocked with 1 s left, and the wait may be shown
        assertThat(limit.check(CONTEXT)).contains(new LimitViolation(ResendCooldownLimit.NAME, 1, true));

        // Given: the last request was exactly 60 s ago
        when(codes.findLatestCreatedAt("ana@x.com")).thenReturn(Optional.of(NOW.minusSeconds(60)));
        // When / Then: allowed
        assertThat(limit.check(CONTEXT)).isEmpty();

        // Given: no earlier request for this email
        when(codes.findLatestCreatedAt("ana@x.com")).thenReturn(Optional.empty());
        // When / Then: allowed
        assertThat(limit.check(CONTEXT)).isEmpty();
    }

    /**
     * Proves one email can get at most 5 codes per rolling hour, so someone who waits out the
     * cooldown each time still can't keep sending codes to the same inbox all day.
     * <p>
     * Cases: 5th request → allowed; 6th → refused, reopens in 600 s, wait hidden so the reply
     * reveals nothing about earlier requests (specs/001-request-code/spec.md FR-008).
     */
    @Test
    void perEmailBlocksTheSixthRequestInAnHour() {
        // Given: a real per-email limit, and the oldest request in the window was 3000 s ago
        PerEmailHourlyLimit limit = new PerEmailHourlyLimit(codes, TestProperties.auth());
        when(codes.findOldestByEmailSince("ana@x.com", WINDOW_START)).thenReturn(Optional.of(NOW.minusSeconds(3000)));

        // Given: 4 earlier requests this hour
        when(codes.countByEmailAndCreatedAtAfter("ana@x.com", WINDOW_START)).thenReturn(4L);
        // When / Then: the 5th is allowed
        assertThat(limit.check(CONTEXT)).isEmpty();

        // Given: 5 earlier requests this hour
        when(codes.countByEmailAndCreatedAtAfter("ana@x.com", WINDOW_START)).thenReturn(5L);
        // When / Then: the 6th is refused; it reopens in 600 s, but the wait may not be shown
        assertThat(limit.check(CONTEXT)).contains(new LimitViolation(PerEmailHourlyLimit.NAME, 600, false));
    }

    /**
     * Proves one IP address can request at most 20 codes per rolling hour across all emails, so a
     * single machine can't spray codes at many different inboxes.
     * <p>
     * Cases: 20th request → allowed; 21st → refused, reopens in 3540 s, wait hidden.
     */
    @Test
    void perSourceBlocksTheTwentyFirstRequest() {
        // Given: a real per-source limit, and the oldest request from this IP was 60 s ago
        PerSourceHourlyLimit limit = new PerSourceHourlyLimit(codes, TestProperties.auth());
        when(codes.findOldestBySourceSince("1.2.3.4", WINDOW_START)).thenReturn(Optional.of(NOW.minusSeconds(60)));

        // Given: 19 earlier requests from this IP this hour
        when(codes.countByRequestSourceAndCreatedAtAfter("1.2.3.4", WINDOW_START)).thenReturn(19L);
        // When / Then: the 20th is allowed
        assertThat(limit.check(CONTEXT)).isEmpty();

        // Given: 20 earlier requests from this IP this hour
        when(codes.countByRequestSourceAndCreatedAtAfter("1.2.3.4", WINDOW_START)).thenReturn(20L);
        // When / Then: the 21st is refused; it reopens in 3540 s, but the wait may not be shown
        assertThat(limit.check(CONTEXT)).contains(new LimitViolation(PerSourceHourlyLimit.NAME, 3540, false));
    }

    /**
     * Proves the whole system sends at most 1000 codes per rolling hour, so even an attack spread
     * over many IPs and emails can't push the email bill past a fixed ceiling.
     * <p>
     * Cases: 1000th request → allowed; 1001st → refused, wait hidden; a wait under 1 s rounds up
     * to 1 s, so the client is never told to retry after 0 s.
     */
    @Test
    void globalBlocksTheThousandAndFirstRequest() {
        // Given: a real global limit, and the oldest request leaves the window in 0.5 s
        GlobalHourlyLimit limit = new GlobalHourlyLimit(codes, TestProperties.auth());
        when(codes.findOldestSince(any())).thenReturn(Optional.of(NOW.minusMillis(3_599_500)));

        // Given: 999 earlier requests this hour
        when(codes.countByCreatedAtAfter(WINDOW_START)).thenReturn(999L);
        // When / Then: the 1000th is allowed
        assertThat(limit.check(CONTEXT)).isEmpty();

        // Given: 1000 earlier requests this hour
        when(codes.countByCreatedAtAfter(WINDOW_START)).thenReturn(1000L);
        // When / Then: the 1001st is refused, with the wait rounded up to 1 s and kept secret
        assertThat(limit.check(CONTEXT)).contains(new LimitViolation(GlobalHourlyLimit.NAME, 1, false));
    }
}
