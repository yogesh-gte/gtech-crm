package com.example.demo.repository;

import com.example.demo.model.Issue;
import com.example.demo.model.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {

    List<IssueAttachment> findByIssue(Issue issue);
}
