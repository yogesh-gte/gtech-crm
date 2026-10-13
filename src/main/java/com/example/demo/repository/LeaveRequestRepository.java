package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    
    List<LeaveRequest> findByEmployee(Employee employee);
    
    List<LeaveRequest> findByStatus(String status);
    
    List<LeaveRequest> findByEmployeeAndStatus(Employee employee, String status);
    
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.startDate <= :date AND lr.endDate >= :date")
    List<LeaveRequest> findActiveLeavesOnDate(@Param("date") LocalDate date);
    
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.startDate BETWEEN :startDate AND :endDate OR lr.endDate BETWEEN :startDate AND :endDate")
    List<LeaveRequest> findLeavesInDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}

