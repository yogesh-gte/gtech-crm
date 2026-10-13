package com.example.demo.repository;

import com.example.demo.model.Task;
import com.example.demo.model.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    // Find all comments for a specific task, ordered by creation time
    List<TaskComment> findByTaskOrderByCreatedAtAsc(Task task);
}
