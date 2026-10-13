package com.example.demo.repository;

import com.example.demo.model.Candidate;
import com.example.demo.model.JobOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    
    List<Candidate> findByJobOpening(JobOpening jobOpening);
    
    List<Candidate> findByStatus(String status);
    
    List<Candidate> findByJobOpeningAndStatus(JobOpening jobOpening, String status);
}

