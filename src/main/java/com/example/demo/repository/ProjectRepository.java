package com.example.demo.repository;

import com.example.demo.model.Project;
import com.example.demo.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectCode(String projectCode);

    List<Project> findByTeam(Team team);

    List<Project> findByStatus(String status);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.team LEFT JOIN FETCH p.owner ORDER BY p.name")
    List<Project> findAllWithDetails();
}
