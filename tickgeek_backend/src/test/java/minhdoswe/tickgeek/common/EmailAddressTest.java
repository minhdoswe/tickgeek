package minhdoswe.tickgeek.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressTest {

    /**
     * Proves an email is trimmed and lowercased before use, so " Ana@Example.COM " and
     * "ana@example.com" count as one person and share one set of codes and limits.
     * <p>
     * Cases: "  Ana@Example.COM " → "ana@example.com" (specs/001-request-code/spec.md FR-002;
     * specs/001-request-code/research.md R11).
     */
    @Test
    void trimsAndLowercases() {
        // When / Then: surrounding spaces and capital letters are removed
        assertThat(EmailAddress.normalize("  Ana@Example.COM ")).isEqualTo("ana@example.com");
    }

    /**
     * Proves a missing email passes through as null instead of crashing, so validation can still
     * show the user the friendly "Enter your email address." error.
     * <p>
     * Cases: null → null. CodeRequestBody normalizes before @NotBlank runs
     * (specs/001-request-code/research.md R11).
     */
    @Test
    void keepsNull() {
        // When / Then: null passes through unchanged
        assertThat(EmailAddress.normalize(null)).isNull();
    }
}
