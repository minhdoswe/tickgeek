package minhdoswe.tickgeek.integration;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only the code request endpoint is public; everything else is denied
 * (specs/001-request-code/research.md R17).
 */
class SecurityMatchersIT extends AbstractIntegrationTest {

    /**
     * Proves the code request endpoint works without a sign-in token, so a person who isn't
     * signed in yet can start signing in.
     * <p>
     * Cases: POST /api/auth/code-requests with no Authorization header → 202
     * (specs/001-request-code/research.md R17).
     */
    @Test
    void codeRequestNeedsNoToken() throws Exception {
        // When / Then: a request with no Authorization header is accepted
        requestCode("ana@x.com").andExpect(status().isAccepted());
    }

    /**
     * Proves every endpoint not listed in SecurityConfig.PUBLIC_ENDPOINTS is refused, so a new
     * endpoint stays closed until someone opens it on purpose.
     * <p>
     * Cases: GET on the public path, POST /api/auth/code-verifications, /api/events, /actuator
     * and / → 403 (specs/001-request-code/research.md R17).
     */
    @Test
    void everythingElseIsDenied() throws Exception {
        // When / Then: the public path with the wrong method (GET, not POST) is denied
        mockMvc.perform(get("/api/auth/code-requests")).andExpect(status().isForbidden());
        // When / Then: an auth endpoint that is not public on this branch is denied
        postJson("/api/auth/code-verifications", "{}").andExpect(status().isForbidden());
        // When / Then: other API paths, actuator and the root are denied
        mockMvc.perform(get("/api/events")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator")).andExpect(status().isForbidden());
        mockMvc.perform(get("/")).andExpect(status().isForbidden());
    }
}
