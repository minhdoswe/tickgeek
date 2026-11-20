package minhdoswe.tickgeek.module.auth.code;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.TestProperties;

import static org.assertj.core.api.Assertions.assertThat;

class CodeHasherTest {

    private final CodeHasher hasher = new CodeHasher(TestProperties.auth());

    /**
     * Proves codes are stored only as a keyed hash, so someone reading the database can't see
     * anyone's code, while sign-in can still check a typed code by hashing it again.
     * <p>
     * Cases: the hash is 64 hex characters, does not contain "042317", and is the same on a
     * second run (specs/001-request-code/research.md R3).
     */
    @Test
    void hashIsDeterministicAndHidesTheCode() {
        // When: a code is hashed
        String hash = hasher.hash("ana@x.com", "042317");

        // Then: fixed length, no plain code inside, and the same result when hashed again
        assertThat(hash).hasSize(64).doesNotContain("042317").isEqualTo(hasher.hash("ana@x.com", "042317"));
    }

    /**
     * Proves every generated code is exactly 6 digits, so the user always gets a code in the form
     * the sign-in screen expects, leading zeros included.
     * <p>
     * Cases: 100 codes → each matches \d{6}; 100 runs make a broken format very likely to show
     * up (specs/001-request-code/spec.md FR-003; specs/001-request-code/research.md R3).
     */
    @Test
    void generatorMakesSixDigitCodes() {
        // Given: a real generator
        CodeGenerator generator = new CodeGenerator();

        // When / Then: each of 100 codes is exactly 6 digits
        for (int i = 0; i < 100; i++) {
            assertThat(generator.next()).matches("\\d{6}");
        }
    }
}
