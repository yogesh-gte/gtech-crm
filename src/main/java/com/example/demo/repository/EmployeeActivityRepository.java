package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.EmployeeActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmployeeActivityRepository extends JpaRepository<EmployeeActivity, Long> {
    
    List<EmployeeActivity> findByEmployee(Employee employee);
    
    List<EmployeeActivity> findByEmployeeOrderByStartedAtDesc(Employee employee);
    
    List<EmployeeActivity> findByEmployeeAndStartedAtBetween(Employee employee, LocalDateTime start, LocalDateTime end);
    
    List<EmployeeActivity> findByIsProductiveFalseAndStartedAtAfter(LocalDateTime start);
    
    List<EmployeeActivity> findByEmployeeAndIsProductiveFalseAndStartedAtAfter(Employee employee, LocalDateTime start);
    
    @Query("SELECT e FROM EmployeeActivity e WHERE e.isProductive = false AND e.startedAt >= :start ORDER BY e.startedAt DESC")
    List<EmployeeActivity> findNonProductiveActivities(LocalDateTime start);
    
    @Query("SELECT e FROM EmployeeActivity e WHERE e.employee = :employee AND e.isProductive = false AND e.startedAt >= :start ORDER BY e.startedAt DESC")
    List<EmployeeActivity> findNonProductiveActivitiesByEmployee(Employee employee, LocalDateTime start);
    
    List<EmployeeActivity> findByActivityTypeAndStartedAtAfter(String activityType, LocalDateTime start);
}

