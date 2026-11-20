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

    /** Bloqueos que aún no están en Google Calendar (los ya terminados hace más de una semana no se suben). */
    @Query("""
            SELECT b FROM CalendarBlock b LEFT JOIN FETCH b.employee
            WHERE b.calendarSyncedAt IS NULL AND b.endsAt >= :since
              AND (b.calendarSyncAttemptAt IS NULL OR b.calendarSyncAttemptAt < :retryBefore)
            ORDER BY b.id ASC
            """)
    List<CalendarBlock> findCalendarPending(@Param("since") LocalDateTime since,
                                            @Param("retryBefore") LocalDateTime retryBefore,
                                            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            UPDATE CalendarBlock b SET b.calendarSyncedAt = :at, b.calendarSyncAttemptAt = NULL, b.calendarSyncError = NULL
            WHERE b.id = :id
            """)
    int markCalendarSynced(@Param("id") Long id, @Param("at") LocalDateTime at);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE CalendarBlock b SET b.calendarSyncAttemptAt = :at, b.calendarSyncError = :error WHERE b.id = :id")
    int markCalendarFailed(@Param("id") Long id, @Param("at") LocalDateTime at, @Param("error") String error);
}
