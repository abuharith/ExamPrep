package com.examprep.api.user;

import java.util.UUID;

public record UserAccountSummary(UUID id, String fullName, String email) {
}
