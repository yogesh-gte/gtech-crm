package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    List<Task> findByEmployee(Employee employee);
    
    List<Task> findByEmployeeIn(List<Employee> employees);
    
    @Query("SELECT t FROM Task t WHERE t.employee = :employee AND t.assignedDate BETWEEN :start AND :end")
    List<Task> findByEmployeeAndAssignedDateBetween(@Param("employee") Employee employee, @Param("start") LocalDate start, @Param("end") LocalDate end);
    
    List<Task> findByEmployeeAndStatus(Employee employee, String status);
    
    List<Task> findByEmployeeAndAssignedDate(Employee employee, LocalDate date);
    
    @Query("SELECT t FROM Task t WHERE t.employee = :employee AND t.assignedDate = :date ORDER BY t.priority DESC, t.createdAt DESC")
    List<Task> findTodayTasksByEmployee(Employee employee, LocalDate date);
    
    List<Task> findByEmployeeAndStatusNot(Employee employee, String status);

    @Query("SELECT t FROM Task t WHERE t.dueDate < :today AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Task> findOverdueTasks(@Param("today") java.time.LocalDate today);

    Optional<Task> findFirstByAttachmentUrlEndingWith(String filename);
}

