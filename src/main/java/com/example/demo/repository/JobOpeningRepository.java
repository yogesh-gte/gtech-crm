package com.example.demo.repository;

import com.example.demo.model.JobOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {
    
    List<JobOpening> findByStatus(String status);
    
    List<JobOpening> findByDepartment(String department);
}

