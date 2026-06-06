package com.lqragent.backend.quiz.repository;

import com.lqragent.backend.quiz.entity.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    Page<QuestionBank> findByStatus(Integer status, Pageable pageable);

    Page<QuestionBank> findByStatusAndQuestionType(Integer status, String questionType, Pageable pageable);

    Page<QuestionBank> findByStatusAndKnowledgePoint(Integer status, String knowledgePoint, Pageable pageable);

    Page<QuestionBank> findByStatusAndQuestionTypeAndKnowledgePoint(
            Integer status, String questionType, String knowledgePoint, Pageable pageable);

    Optional<QuestionBank> findFirstByStatusAndIdGreaterThanOrderByIdAsc(Integer status, Long id);
}
