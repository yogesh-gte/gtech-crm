package com.example.demo.model;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "payroll")
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "payroll_month", nullable = false)
    private Integer month;

    @Column(name = "payroll_year", nullable = false)
    private Integer year;

    @Column(name = "base_salary")
    private Double grossSalary;

    @Column(name = "lop_deduction")
    private Double lopDeduction;

    @Column(name = "total_deductions")
    private Double totalDeductions;

    @Column(name = "net_salary")
    private Double netPay;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_status")
    private PayrollStatus payrollStatus = PayrollStatus.GENERATED;

    @Column(name = "generated_on")
    @Temporal(TemporalType.DATE)
    private Date generatedOn;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "paid_on")
    @Temporal(TemporalType.DATE)
    private Date paidOn;

    @Column(name = "pay_period_start")
    private java.time.LocalDate payPeriodStart;

    @Column(name = "pay_period_end")
    private java.time.LocalDate payPeriodEnd;

    @PrePersist
    protected void onCreate() {
        if (generatedOn == null) {
            generatedOn = new Date();
        }
        if (month != null && year != null) {
            java.time.YearMonth ym = java.time.YearMonth.of(year, month);
            this.payPeriodStart = ym.atDay(1);
            this.payPeriodEnd = ym.atEndOfMonth();
        }
    }

    public enum PayrollStatus {
        GENERATED, APPROVED, PAID, SENT
    }

    // Constructors
    public Payroll() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Double getGrossSalary() {
        return grossSalary;
    }

    public void setGrossSalary(Double grossSalary) {
        this.grossSalary = grossSalary;
    }

    public Double getLopDeduction() {
        return lopDeduction;
    }

    public void setLopDeduction(Double lopDeduction) {
        this.lopDeduction = lopDeduction;
    }

    public Double getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(Double totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public Double getNetPay() {
        return netPay;
    }

    public void setNetPay(Double netPay) {
        this.netPay = netPay;
    }

    public PayrollStatus getPayrollStatus() {
        return payrollStatus;
    }

    public void setPayrollStatus(PayrollStatus payrollStatus) {
        this.payrollStatus = payrollStatus;
    }

    public Date getGeneratedOn() {
        return generatedOn;
    }

    public void setGeneratedOn(Date generatedOn) {
        this.generatedOn = generatedOn;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Date getPaidOn() {
        return paidOn;
    }

    public void setPaidOn(Date paidOn) {
        this.paidOn = paidOn;
    }

    public java.time.LocalDate getPayPeriodStart() {
        return payPeriodStart;
    }

    public void setPayPeriodStart(java.time.LocalDate payPeriodStart) {
        this.payPeriodStart = payPeriodStart;
    }

    public java.time.LocalDate getPayPeriodEnd() {
        return payPeriodEnd;
    }

    public void setPayPeriodEnd(java.time.LocalDate payPeriodEnd) {
        this.payPeriodEnd = payPeriodEnd;
    }
}
