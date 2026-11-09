package com.examprep.web.controller;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentSummaryDto;
import com.examprep.api.study.QuizAttemptSummaryDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackSummaryDto;
import com.examprep.api.user.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final CurrentUserService currentUser;
    private final DocumentApplicationService documents;
    private final StudyPackApplicationService studyPacks;

    public DashboardController(CurrentUserService currentUser, DocumentApplicationService documents,
                               StudyPackApplicationService studyPacks) {
        this.currentUser = currentUser;
        this.documents = documents;
        this.studyPacks = studyPacks;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        List<DocumentSummaryDto> allDocuments = documents.listDocuments(ownerId);
        List<StudyPackSummaryDto> allPacks = studyPacks.listStudyPacks(ownerId);
        List<QuizAttemptSummaryDto> recentAttempts = studyPacks.listRecentAttempts(ownerId, 5);
        List<QuizAttemptSummaryDto> allAttempts = studyPacks.listRecentAttempts(ownerId, 50);

        int averageScore = allAttempts.isEmpty() ? 0 : (int) Math.round(
                allAttempts.stream().mapToInt(QuizAttemptSummaryDto::percentage).average().orElse(0));
        int readyDocuments = (int) allDocuments.stream()
                .filter(document -> document.status() == com.examprep.api.document.ProcessingStatus.READY)
                .count();

        model.addAttribute("documents", allDocuments.stream().limit(4).toList());
        model.addAttribute("studyPacks", allPacks.stream().limit(4).toList());
        model.addAttribute("recentAttempts", recentAttempts);
        model.addAttribute("documentCount", allDocuments.size());
        model.addAttribute("readyDocumentCount", readyDocuments);
        model.addAttribute("studyPackCount", allPacks.size());
        model.addAttribute("attemptCount", studyPacks.attemptCount(ownerId));
        model.addAttribute("flashcardReviewCount", studyPacks.flashcardReviewCount(ownerId));
        model.addAttribute("averageScore", averageScore);
        return "dashboard";
    }
}
