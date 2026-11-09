package com.examprep.study.service;

import com.examprep.api.study.StudyPackStatus;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.StudyPackRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A restart during generation would otherwise leave a package in QUEUED or PROCESSING
 * forever. Those records are failed with an explanation so the student can retry; no
 * partial package is ever presented as complete.
 */
@Component
@Order(20)
public class StudyRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StudyRecovery.class);

    private final StudyPackRepository studyPacks;
    private final Clock clock;

    public StudyRecovery(StudyPackRepository studyPacks, Clock clock) {
        this.studyPacks = studyPacks;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<StudyPack> interrupted = studyPacks.findByStatusIn(
                List.of(StudyPackStatus.QUEUED, StudyPackStatus.PROCESSING));
        for (StudyPack pack : interrupted) {
            pack.markFailed("Generation was interrupted by a server restart. Generate the package again.", clock.instant());
        }
        if (!interrupted.isEmpty()) {
            studyPacks.saveAll(interrupted);
            log.info("Marked {} interrupted study package(s) as failed after restart", interrupted.size());
        }
    }
}
