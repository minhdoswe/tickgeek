package minhdoswe.tickgeek.module.auth.limit;

import java.util.Optional;

public interface CodeRequestLimit {

    Optional<LimitViolation> check(CodeRequestContext context);
}
