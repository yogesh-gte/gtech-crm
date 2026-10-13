package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Issue;
import com.example.demo.model.Project;
import com.example.demo.model.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findByIssueKey(String issueKey);

    List<Issue> findByProject(Project project);

    List<Issue> findByProjectOrderByCreatedAtDesc(Project project);

    List<Issue> findByAssignee(Employee assignee);

    List<Issue> findByReporter(Employee reporter);

    List<Issue> findByIssueType(String issueType);

    List<Issue> findByStatus(String status);

    long countByProject(Project project);

    List<Issue> findBySprint(Sprint sprint);

    List<Issue> findBySprintOrderByStatusAscCreatedAtAsc(Sprint sprint);

    List<Issue> findByProjectAndSprintIsNull(Project project);

    @Query("SELECT i FROM Issue i LEFT JOIN FETCH i.project LEFT JOIN FETCH i.assignee LEFT JOIN FETCH i.reporter ORDER BY i.createdAt DESC")
    List<Issue> findAllWithDetails();
}
