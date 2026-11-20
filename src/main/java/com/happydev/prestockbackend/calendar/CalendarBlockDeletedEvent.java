package com.happydev.prestockbackend.calendar;

/** Se quitó un bloqueo de agenda: su evento de Google Calendar se borra tras el commit. */
public record CalendarBlockDeletedEvent(Long blockId) {}
