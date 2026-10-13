package com.example.demo.repository;

import com.example.demo.model.Issue;
import com.example.demo.model.IssueActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IssueActivityRepository extends JpaRepository<IssueActivity, Long> {

    List<IssueActivity> findByIssueOrderByCreatedAtDesc(Issue issue);

    @Query(value = "SELECT a FROM IssueActivity a LEFT JOIN FETCH a.issue ORDER BY a.createdAt DESC",
           countQuery = "SELECT count(a) FROM IssueActivity a")
    List<IssueActivity> findTop500ByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
