package com.examprep.web.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String landing(Authentication authentication, Model model) {
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("signedIn", signedIn);
        return "landing";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("heading", "That page is not available");
        model.addAttribute("message", "You do not have permission to open this page.");
        return "error";
    }
}
