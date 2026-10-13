package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.IdleIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdleIncidentRepository extends JpaRepository<IdleIncident, Long> {
    
    List<IdleIncident> findByEmployee(Employee employee);
    
    List<IdleIncident> findByStatus(String status);
    
    @Query("SELECT i FROM IdleIncident i WHERE i.status = 'ACTIVE' ORDER BY i.idleStartTime DESC")
    List<IdleIncident> findActiveIncidents();
    
    @Query("SELECT i FROM IdleIncident i WHERE i.employee = :employee AND i.status = 'ACTIVE' ORDER BY i.idleStartTime DESC")
    Optional<IdleIncident> findActiveIncidentByEmployee(Employee employee);
    
    @Query("SELECT i FROM IdleIncident i WHERE i.createdAt >= :startDate ORDER BY i.createdAt DESC")
    List<IdleIncident> findRecentIncidents(LocalDateTime startDate);
}

