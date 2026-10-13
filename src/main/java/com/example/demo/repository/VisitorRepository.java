package com.example.demo.repository;

import com.example.demo.model.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VisitorRepository extends JpaRepository<Visitor, Long> {
    
    List<Visitor> findByStatus(String status);
    
    List<Visitor> findByCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT v FROM Visitor v ORDER BY v.checkInTime DESC")
    List<Visitor> findAllOrderByCheckInTimeDesc();
    
    List<Visitor> findByPersonToMeet(String personToMeet);
}

