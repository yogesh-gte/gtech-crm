package com.example.demo.repository;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByEmployeeAndAttendanceDate(Employee employee, LocalDate date);

    List<Attendance> findByEmployee(Employee employee);

    List<Attendance> findByAttendanceDate(LocalDate date);

    Optional<Attendance> findTopByEmployeeAndAttendanceDateOrderByCheckInTimeDesc(Employee employee, LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate BETWEEN :startDate AND :endDate")
    List<Attendance> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT a FROM Attendance a WHERE a.employee = :employee AND a.attendanceDate BETWEEN :startDate AND :endDate")
    List<Attendance> findByEmployeeAndDateRange(@Param("employee") Employee employee,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
