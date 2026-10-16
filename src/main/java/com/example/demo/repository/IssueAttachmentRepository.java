package com.example.demo.repository;

import com.example.demo.model.Issue;
import com.example.demo.model.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {

    List<IssueAttachment> findByIssue(Issue issue);

    Optional<IssueAttachment> findFirstByFilePathEndingWith(String filename);
}
