package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    Optional<Employee> findByEmail(String email);
    
    Optional<Employee> findByEmployeeId(String employeeId);
    
    List<Employee> findByStatus(String status);
    
    List<Employee> findByDepartment(String department);
    
    @Query("SELECT e FROM Employee e WHERE e.status = 'IN' ORDER BY e.currentCheckIn DESC")
    List<Employee> findCurrentlyPresentEmployees();

    List<Employee> findByTeam(Team team);
}

