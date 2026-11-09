package com.examprep.study.repository;

import com.examprep.study.domain.Question;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByStudyPackIdAndMockExamOrderByPositionAsc(UUID studyPackId, boolean mockExam);

    List<Question> findByStudyPackIdOrderByMockExamAscPositionAsc(UUID studyPackId);

    List<Question> findByStudyPackIdAndIdIn(UUID studyPackId, Collection<UUID> ids);

    void deleteByStudyPackId(UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);
}
