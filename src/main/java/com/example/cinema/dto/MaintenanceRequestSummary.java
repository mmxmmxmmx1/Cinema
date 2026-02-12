package com.example.cinema.dto;

import java.time.Instant;

public record MaintenanceRequestSummary(
        long id,
        String trackingNo,
        String requester,
        String assignee,
        String auditorium,
        String title,
        String priority,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant resolvedAt,
        Instant closedAt,
        String closedBy,
        String resolutionNote) {
}
