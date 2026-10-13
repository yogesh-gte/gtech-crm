package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
    
    List<Suggestion> findByEmployee(Employee employee);
    
    List<Suggestion> findByEmployeeOrderByCreatedAtDesc(Employee employee);
    
    List<Suggestion> findBySuggestionTypeOrderByCreatedAtDesc(String suggestionType);
    
    List<Suggestion> findByStatusOrderByCreatedAtDesc(String status);
    
    List<Suggestion> findAllByOrderByCreatedAtDesc();
}

