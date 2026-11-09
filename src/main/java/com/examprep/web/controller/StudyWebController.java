package com.examprep.web.controller;

import com.examprep.api.study.QuestionDto;
import com.examprep.api.study.QuizResultDto;
import com.examprep.api.study.QuizSubmissionDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackDto;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.TutorQuestionDto;
import com.examprep.api.user.CurrentUserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/study-packs")
public class StudyWebController {

    private final CurrentUserService currentUser;
    private final StudyPackApplicationService studyPacks;

    public StudyWebController(CurrentUserService currentUser, StudyPackApplicationService studyPacks) {
        this.currentUser = currentUser;
        this.studyPacks = studyPacks;
    }

    @GetMapping("/{id}/status")
    public String status(@PathVariable UUID id, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPackStatusDto status = studyPacks.getStatus(id, ownerId);
        model.addAttribute("status", status);
        return "study/status";
    }

    @GetMapping("/{id}")
    public String studyPack(@PathVariable UUID id, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPackDto pack = studyPacks.getStudyPack(id, ownerId);
        model.addAttribute("pack", pack);
        model.addAttribute("attempts", studyPacks.listAttempts(id, ownerId));
        return "study/view";
    }

    @GetMapping("/{id}/flashcards")
    public String flashcards(@PathVariable UUID id, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPackDto pack = studyPacks.getStudyPack(id, ownerId);
        model.addAttribute("pack", pack);
        return "study/flashcards";
    }

    @GetMapping("/{id}/exam")
    public String exam(@PathVariable UUID id, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPackDto pack = studyPacks.getStudyPack(id, ownerId);
        List<QuestionDto> questions = studyPacks.getExamQuestions(id, ownerId);
        model.addAttribute("pack", pack);
        model.addAttribute("questions", questions);
        return "study/exam";
    }

    /** No-JavaScript submission path; the examination page normally posts JSON to the API. */
    @PostMapping("/{id}/exam")
    public String submitExam(@PathVariable UUID id, @RequestParam Map<String, String> params) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Map<UUID, String> answers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().startsWith("answer_")) {
                continue;
            }
            try {
                answers.put(UUID.fromString(entry.getKey().substring("answer_".length())), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed field names.
            }
        }
        int duration = 0;
        try {
            duration = Integer.parseInt(params.getOrDefault("durationSeconds", "0"));
        } catch (NumberFormatException ignored) {
            // Keep zero.
        }
        QuizResultDto result = studyPacks.submitQuiz(id, ownerId, new QuizSubmissionDto(answers, duration));
        return "redirect:/study-packs/" + id + "/attempts/" + result.attemptId();
    }

    @GetMapping("/{id}/attempts/{attemptId}")
    public String attempt(@PathVariable UUID id, @PathVariable UUID attemptId, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        QuizResultDto result = studyPacks.getAttemptResult(id, ownerId, attemptId);
        model.addAttribute("packId", id);
        model.addAttribute("result", result);
        return "study/results";
    }

    @GetMapping("/{id}/tutor")
    public String tutor(@PathVariable UUID id, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPackDto pack = studyPacks.getStudyPack(id, ownerId);
        model.addAttribute("pack", pack);
        model.addAttribute("messages", studyPacks.getTutorConversation(id, ownerId));
        return "study/tutor";
    }

    @PostMapping("/{id}/tutor")
    public String askTutor(@PathVariable UUID id, @RequestParam("question") String question, Model model) {
        UUID ownerId = currentUser.requireCurrentUserId();
        studyPacks.askTutor(id, ownerId, new TutorQuestionDto(question));
        return "redirect:/study-packs/" + id + "/tutor";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        UUID ownerId = currentUser.requireCurrentUserId();
        studyPacks.delete(id, ownerId);
        return "redirect:/dashboard";
    }
}
