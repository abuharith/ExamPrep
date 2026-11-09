package com.examprep.api.study;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TutorQuestionDto(
        @NotBlank(message = "Type a question to ask the tutor.")
        @Size(max = 2000, message = "Keep your question under 2000 characters.")
        String question) {
}
