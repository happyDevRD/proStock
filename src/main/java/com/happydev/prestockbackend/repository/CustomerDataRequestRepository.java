package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.CustomerDataRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomerDataRequestRepository extends JpaRepository<CustomerDataRequest, Long> {

    Optional<CustomerDataRequest> findByToken(String token);

    List<CustomerDataRequest> findByServiceOrderIdOrderByCreatedAtDesc(Long serviceOrderId);

    boolean existsByCustomerIdAndStatusAndCreatedAtAfter(Long customerId, CustomerDataRequest.Status status,
                                                         Instant after);

    /** Nombre y apellido del cliente para precargar el formulario al enviarlo. */
    @Query("select c.firstName, c.lastName from CustomerDataRequest r join r.customer c where r.token = :token")
    List<Object[]> findNamesByToken(@Param("token") String token);
}
