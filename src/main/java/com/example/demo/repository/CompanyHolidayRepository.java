package com.example.demo.repository;

import com.example.demo.model.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@Repository
public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {
    Optional<CompanyHoliday> findByDate(LocalDate date);
    List<CompanyHoliday> findAllByOrderByDateAsc();
}
