package com.example.demo.repository;

import com.example.demo.model.Issue;
import com.example.demo.model.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    List<IssueComment> findByIssueOrderByCreatedAtAsc(Issue issue);
}
