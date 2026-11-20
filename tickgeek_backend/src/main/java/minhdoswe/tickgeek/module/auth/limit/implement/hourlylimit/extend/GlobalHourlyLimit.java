package minhdoswe.tickgeek.module.auth.limit.implement.hourlylimit.extend;

import java.time.Instant;
import java.util.Optional;

import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;
import minhdoswe.tickgeek.module.auth.limit.implement.hourlylimit.HourlyLimit;
import org.springframework.stereotype.Component;

import minhdoswe.tickgeek.config.AuthProperties;
import minhdoswe.tickgeek.module.auth.code.VerificationCodeRepository;

@Component
public class GlobalHourlyLimit extends HourlyLimit {

    public static final String NAME = "GLOBAL_HOURLY";

    private final VerificationCodeRepository codes;
    private final int maximum;

    public GlobalHourlyLimit(VerificationCodeRepository codes, AuthProperties properties) {
        super(properties.limits().window());
        this.codes = codes;
        this.maximum = properties.limits().globalPerHour();
    }

    @Override
    protected String name() {
        return NAME;
    }

    @Override
    protected int maximum() {
        return maximum;
    }

    @Override
    protected long countSince(CodeRequestContext context, Instant since) {
        return codes.countByCreatedAtAfter(since);
    }

    @Override
    protected Optional<Instant> oldestSince(CodeRequestContext context, Instant since) {
        return codes.findOldestSince(since);
    }
}
