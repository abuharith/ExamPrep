package com.examprep.user.service;

import com.examprep.api.user.RegisterUserRequest;
import com.examprep.api.user.UserAccountSummary;
import com.examprep.api.user.UserRegistrationService;
import com.examprep.common.exception.EmailAlreadyRegisteredException;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserRegistrationServiceImpl(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserAccountSummary register(RegisterUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException("An account already exists for that email address.");
        }
        Instant now = clock.instant();
        UserAccount account = new UserAccount(
                UUID.randomUUID(),
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                now);
        try {
            UserAccount saved = users.saveAndFlush(account);
            return new UserAccountSummary(saved.getId(), saved.getFullName(), saved.getEmail());
        } catch (DataIntegrityViolationException ex) {
            // The unique index on lower(email) is the final guard against concurrent sign-ups.
            throw new EmailAlreadyRegisteredException("An account already exists for that email address.");
        }
    }
}
