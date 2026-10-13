package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    
    List<Feedback> findByEmployee(Employee employee);
    
    List<Feedback> findByEmployeeOrderByCreatedAtDesc(Employee employee);
}

