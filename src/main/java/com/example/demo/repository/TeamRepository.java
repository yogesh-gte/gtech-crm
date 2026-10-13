package com.example.demo.repository;

import com.example.demo.model.Department;
import com.example.demo.model.Employee;
import com.example.demo.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("SELECT t FROM Team t LEFT JOIN FETCH t.teamLead LEFT JOIN FETCH t.department ORDER BY t.name")
    List<Team> findAllWithTeamLead();

    List<Team> findByDepartment(Department department);

    List<Team> findByDepartmentId(Long departmentId);

    List<Team> findByTeamLead(Employee teamLead);

    java.util.Optional<Team> findByTeamLeadId(Long teamLeadId);

    List<Team> findByIsActiveTrueOrderByNameAsc();

    long countByDepartment(Department department);
}
