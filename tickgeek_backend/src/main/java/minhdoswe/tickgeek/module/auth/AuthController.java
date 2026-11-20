package minhdoswe.tickgeek.module.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import minhdoswe.tickgeek.common.RequestSourceResolver;
import minhdoswe.tickgeek.module.auth.code.CodeRequestService;
import minhdoswe.tickgeek.module.auth.dto.CodeRequestBody;
import minhdoswe.tickgeek.module.auth.dto.CodeRequestResponse;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CodeRequestService codeRequests;
    private final RequestSourceResolver sources;

    public AuthController(CodeRequestService codeRequests, RequestSourceResolver sources) {
        this.codeRequests = codeRequests;
        this.sources = sources;
    }

    @PostMapping("/code-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CodeRequestResponse requestCode(@Valid @RequestBody CodeRequestBody body, HttpServletRequest request) {
        return codeRequests.request(body.email(), sources.resolve(request));
    }
}
