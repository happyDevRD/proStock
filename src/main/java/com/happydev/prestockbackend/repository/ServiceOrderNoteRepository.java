package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrderNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceOrderNoteRepository extends JpaRepository<ServiceOrderNote, Long> {

    List<ServiceOrderNote> findByServiceOrderIdOrderByCreatedAtAsc(Long serviceOrderId);

    void deleteByServiceOrderId(Long serviceOrderId);
}
