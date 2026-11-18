package minhdoswe.tickgeek.module.auth.limit;

import java.util.Optional;

/** One rule that can refuse a code request. Every implementation is applied to every request. */
public interface CodeRequestLimit {

    Optional<LimitViolation> check(CodeRequestContext context);
}
