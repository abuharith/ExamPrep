package com.examprep.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "Enter your full name.")
        @Size(max = 120, message = "Your name must be 120 characters or fewer.")
        String fullName,

        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "That email address is too long.")
        String email,

        @NotBlank(message = "Choose a password.")
        @Size(min = 8, max = 72, message = "Use a password between 8 and 72 characters.")
        String password) {
}
