package minhdoswe.tickgeek.module.auth.code;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class CodeGenerator {

    private static final int CODE_BOUND = 1_000_000;
    private static final String SIX_DIGITS = "%06d";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        return SIX_DIGITS.formatted(random.nextInt(CODE_BOUND));
    }
}
