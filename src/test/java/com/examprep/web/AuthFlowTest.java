package com.examprep.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examprep.support.IntegrationTestBase;
import com.examprep.support.WithMockStudent;
import com.examprep.user.repository.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AuthFlowTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void landingPageIsPublicAndUsesTheProductName() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ExamPrep")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mock examination")));
    }

    @Test
    void registrationAndLoginFormsRender() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create your ExamPrep account")));
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in")));
    }

    @Test
    void privacyPageExplainsGeminiProcessing() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gemini")));
    }

    @Test
    void stylesheetIsServed() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void registrationStoresABcryptHashAndRequiresLogin() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@example.edu";

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Nadia Patel")
                        .param("email", email)
                        .param("password", "correct-horse-battery"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        var account = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(account.getFullName()).isEqualTo("Nadia Patel");
        assertThat(account.getPasswordHash()).isNotEqualTo("correct-horse-battery");
        assertThat(account.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("correct-horse-battery", account.getPasswordHash())).isTrue();
    }

    @Test
    void registrationRejectsDuplicateEmailAndInvalidInput() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.edu";
        mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "First Student").param("email", email).param("password", "password123"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Second Student").param("email", email).param("password", "password456"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "S").param("email", "not-an-email").param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("valid email")));
    }

    @Test
    void protectedPagesRedirectToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/documents"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void loginSucceedsWithValidCredentialsAndFailsWithWrongPassword() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.edu";
        mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "Login Student").param("email", email).param("password", "password123"));

        mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockStudent(email = "logout@example.edu")
    void logoutEndsTheSession() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?loggedOut"));
    }

    @Test
    @WithMockStudent(email = "dashboard@example.edu", name = "Sam Nkosi")
    void dashboardIsPersonalisedForTheSignedInStudent() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sam Nkosi")));
    }
}
