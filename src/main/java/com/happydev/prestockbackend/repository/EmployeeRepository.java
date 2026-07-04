package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Employee;
import com.happydev.prestockbackend.entity.EmployeeDepartment;
import com.happydev.prestockbackend.entity.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query(value = """
            SELECT e FROM Employee e
            WHERE (:search IS NULL OR
                   LOWER(e.nombreCompleto) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.cargo) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:departamento IS NULL OR e.departamento = :departamento)
            """,
           countQuery = """
            SELECT COUNT(e) FROM Employee e
            WHERE (:search IS NULL OR
                   LOWER(e.nombreCompleto) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.cargo) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:departamento IS NULL OR e.departamento = :departamento)
            """)
    Page<Employee> findFiltered(
            @Param("search") String search,
            @Param("status") EmployeeStatus status,
            @Param("departamento") EmployeeDepartment departamento,
            Pageable pageable);

    List<Employee> findByStatusOrderByNombreCompletoAsc(EmployeeStatus status);

    long countByStatus(EmployeeStatus status);
}
