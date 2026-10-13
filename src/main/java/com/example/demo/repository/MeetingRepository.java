package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByStatus(String status);

    List<Meeting> findByOrganizer(Employee organizer);

    @Query("SELECT m FROM Meeting m WHERE m.status = 'ONGOING' ORDER BY m.startTime DESC")
    List<Meeting> findOngoingMeetings();

    @Query("SELECT m FROM Meeting m WHERE m.startTime >= :startDate AND m.startTime <= :endDate")
    List<Meeting> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT m FROM Meeting m WHERE m.status = 'SCHEDULED' AND m.startTime > :now ORDER BY m.startTime ASC")
    List<Meeting> findUpcomingMeetings(LocalDateTime now);


    List<Meeting> findByAttendeesContaining(Employee employee);

    List<Meeting> findByEmployeeParticipantsContaining(Employee employee);

    List<Meeting> findByTargetEmployee(Employee employee);

    List<Meeting> findByInviteEveryoneTrueAndStatus(String status);

    @Query("SELECT m FROM Meeting m WHERE ((m.inviteEveryone = true AND m.meetingType = 'INTERNAL') OR m.organizer = :employee) AND m.status = :status")
    List<Meeting> findVisibleMeetingsForEmployee(Employee employee, String status);

}
