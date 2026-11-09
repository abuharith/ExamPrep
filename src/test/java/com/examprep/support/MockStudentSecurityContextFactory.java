package com.examprep.support;

import com.examprep.security.SecurityUser;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.stereotype.Component;

@Component
public class MockStudentSecurityContextFactory implements WithSecurityContextFactory<WithMockStudent> {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public MockStudentSecurityContextFactory(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SecurityContext createSecurityContext(WithMockStudent annotation) {
        String email = annotation.email().toLowerCase();
        UserAccount account = users.findByEmailIgnoreCase(email).orElseGet(() -> users.saveAndFlush(
                new UserAccount(UUID.randomUUID(), annotation.name(), email,
                        passwordEncoder.encode(annotation.password()), Instant.now())));
        SecurityUser principal = new SecurityUser(account.getId(), account.getEmail(),
                account.getPasswordHash(), account.getFullName());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
