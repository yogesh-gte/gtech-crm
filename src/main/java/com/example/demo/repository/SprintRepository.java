package com.example.demo.repository;

import com.example.demo.model.Project;
import com.example.demo.model.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByProjectOrderByStartDateDesc(Project project);

    List<Sprint> findByProjectIdOrderByStartDateDesc(Long projectId);

    List<Sprint> findByStatus(String status);

    @Query("SELECT s FROM Sprint s WHERE s.project = :project AND s.status = 'ACTIVE'")
    List<Sprint> findActiveByProject(Project project);

    java.util.List<Sprint> findByStatusAndEndDate(String status, java.time.LocalDate endDate);
}
