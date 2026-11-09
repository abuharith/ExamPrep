package com.examprep.web.controller;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.DocumentSummaryDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackSummaryDto;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.user.CurrentUserService;
import com.examprep.common.exception.InvalidUploadException;
import com.examprep.config.AppProperties;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/documents")
public class DocumentWebController {

    private final CurrentUserService currentUser;
    private final DocumentApplicationService documents;
    private final StudyPackApplicationService studyPacks;
    private final AppProperties properties;

    public DocumentWebController(CurrentUserService currentUser, DocumentApplicationService documents,
                                 StudyPackApplicationService studyPacks, AppProperties properties) {
        this.currentUser = currentUser;
        this.documents = documents;
        this.studyPacks = studyPacks;
        this.properties = properties;
    }

    @GetMapping
    public String library(Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        model.addAttribute("documents", documents.listDocuments(ownerId));
        model.addAttribute("maxUploadMb", properties.upload().maxSizeMb());
        return "documents/index";
    }

    /** Non-JavaScript upload path; the drag-and-drop interface uses the JSON API instead. */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUser.requireCurrentUserId();
        try {
            documents.upload(file, ownerId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Upload received. Text extraction has started.");
        } catch (InvalidUploadException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/documents";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        DocumentStatusDto document = documents.getStatus(id, ownerId);
        List<StudyPackSummaryDto> packs = studyPacks.listStudyPacks(ownerId).stream()
                .filter(pack -> id.equals(pack.documentId()))
                .toList();
        model.addAttribute("document", document);
        model.addAttribute("studyPacks", packs);
        model.addAttribute("maxUploadMb", properties.upload().maxSizeMb());
        model.addAttribute("today", LocalDate.now());
        return "documents/detail";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUser.requireCurrentUserId();
        documents.delete(id, ownerId);
        redirectAttributes.addFlashAttribute("successMessage",
                "The document and its generated study packages were deleted.");
        return "redirect:/documents";
    }

    @PostMapping("/{id}/study-packs")
    public String createStudyPack(@PathVariable UUID id,
                                  @RequestParam(required = false) String language,
                                  @RequestParam(required = false) String academicLevel,
                                  @RequestParam(required = false) String examDate,
                                  @RequestParam(defaultValue = "8") int practiceQuestionCount,
                                  @RequestParam(defaultValue = "10") int mockQuestionCount,
                                  @RequestParam(defaultValue = "12") int flashcardCount,
                                  @RequestParam(defaultValue = "30") int mockDurationMinutes,
                                  RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPreferences preferences = new StudyPreferences(language, academicLevel, parseDate(examDate),
                practiceQuestionCount, mockQuestionCount, flashcardCount, mockDurationMinutes);
        UUID studyPackId = studyPacks.create(id, ownerId, preferences).studyPackId();
        return "redirect:/study-packs/" + studyPackId + "/status";
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
