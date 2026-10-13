package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.TeamBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamBadgeRepository extends JpaRepository<TeamBadge, Long> {
    
    List<TeamBadge> findByEmployee(Employee employee);
    
    List<TeamBadge> findByBadgeType(String badgeType);
    
    List<TeamBadge> findByIsVisibleTrue();
}

