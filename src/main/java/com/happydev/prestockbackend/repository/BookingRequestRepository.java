package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.BookingRequest;
import com.happydev.prestockbackend.entity.BookingRequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

    List<BookingRequest> findByStatusOrderByCreatedAtDesc(BookingRequestStatus status, Pageable pageable);

    List<BookingRequest> findByStatusNotOrderByHandledAtDesc(BookingRequestStatus status, Pageable pageable);

    long countByStatus(BookingRequestStatus status);

    /** Para no duplicar cuando el cliente pulsa enviar dos veces. */
    List<BookingRequest> findByPhoneAndCreatedAtAfterOrderByCreatedAtDesc(String phone, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    /** Solicitudes con hora que se cruzan con [start, end), en un estado (pendientes ocupan la agenda). */
    List<BookingRequest> findByStatusAndEventStartLessThanAndEventEndGreaterThan(
            BookingRequestStatus status, LocalDateTime end, LocalDateTime start);
}
