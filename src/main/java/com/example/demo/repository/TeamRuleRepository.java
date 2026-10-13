package com.example.demo.repository;

import com.example.demo.model.TeamRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRuleRepository extends JpaRepository<TeamRule, Long> {
    
    List<TeamRule> findByIsActiveTrue();
    
    List<TeamRule> findByRuleType(String ruleType);
}

