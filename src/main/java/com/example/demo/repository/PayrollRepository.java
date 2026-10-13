package com.example.demo.repository;

import com.example.demo.model.Employee;
import com.example.demo.model.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployee_Id(Long employeeId);

    List<Payroll> findByEmployee(Employee employee);

    List<Payroll> findByPayrollStatus(Payroll.PayrollStatus payrollStatus);

    // Replaced complex date queries with simple Month/Year checks for now
    List<Payroll> findByMonthAndYear(Integer month, Integer year);

    List<Payroll> findByEmployeeOrderByYearDescMonthDesc(Employee employee);
}
