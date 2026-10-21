package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.CalendarBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarBlockRepository extends JpaRepository<CalendarBlock, Long> {

    /** Bloqueos que se cruzan con [start, end). */
    @Query("""
            SELECT b FROM CalendarBlock b LEFT JOIN FETCH b.employee
            WHERE b.startsAt < :end AND b.endsAt > :start
            ORDER BY b.startsAt ASC, b.id ASC
            """)
    List<CalendarBlock> findOverlapping(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
