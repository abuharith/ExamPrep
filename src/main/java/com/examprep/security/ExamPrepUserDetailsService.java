package com.examprep.security;

import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamPrepUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public ExamPrepUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.trim().toLowerCase();
        UserAccount account = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for the supplied email address"));
        return new SecurityUser(account.getId(), account.getEmail(), account.getPasswordHash(), account.getFullName());
    }
}
