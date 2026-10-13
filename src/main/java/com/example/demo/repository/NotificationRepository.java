package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Notification;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientAndIsReadFalseOrderByCreatedAtDesc(Employee recipient);

    List<Notification> findByRecipientOrderByCreatedAtDesc(Employee recipient);

    List<Notification> findByUserRecipientAndIsReadFalseOrderByCreatedAtDesc(User userRecipient);

    List<Notification> findByUserRecipientOrderByCreatedAtDesc(User userRecipient);
}
