package com.examprep.web.controller;

import com.examprep.api.user.RegisterUserRequest;
import com.examprep.api.user.UserRegistrationService;
import com.examprep.common.exception.EmailAlreadyRegisteredException;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRegistrationService registration;

    public AuthController(UserRegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return signedIn(authentication) ? "redirect:/dashboard" : "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (signedIn(authentication)) {
            return "redirect:/dashboard";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterUserRequest(null, null, null));
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterUserRequest form, BindingResult binding,
                           RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return "auth/register";
        }
        try {
            registration.register(form);
        } catch (EmailAlreadyRegisteredException ex) {
            binding.rejectValue("email", "email.exists", ex.getMessage());
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Your account is ready. Sign in with your email and password.");
        return "redirect:/login";
    }

    private static boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
