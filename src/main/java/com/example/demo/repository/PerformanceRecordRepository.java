package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.PerformanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PerformanceRecordRepository extends JpaRepository<PerformanceRecord, Long> {

    List<PerformanceRecord> findByEmployeeAndPeriodTypeOrderByPeriodStartDesc(Employee employee, String periodType);

    List<PerformanceRecord> findByPeriodTypeAndPeriodStartBetweenOrderByCompositeScoreDesc(String periodType, LocalDate start, LocalDate end);
}
