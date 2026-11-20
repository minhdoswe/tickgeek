package minhdoswe.tickgeek.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import minhdoswe.tickgeek.common.EmailAddress;

public record CodeRequestBody(
        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address, like name@example.com.")
        @Size(max = 254, message = "Email addresses can have at most 254 characters.")
        String email) {

    public CodeRequestBody {
        email = EmailAddress.normalize(email);
    }
}
