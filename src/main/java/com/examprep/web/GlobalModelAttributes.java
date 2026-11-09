package com.examprep.web;

import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.security.SecurityUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final StudyPackApplicationService studyPacks;

    public GlobalModelAttributes(StudyPackApplicationService studyPacks) {
        this.studyPacks = studyPacks;
    }

    @ModelAttribute("studentName")
    public String studentName(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            return null;
        }
        return user.fullName();
    }

    @ModelAttribute("aiConfigured")
    public boolean aiConfigured() {
        return studyPacks.aiConfigured();
    }
}
