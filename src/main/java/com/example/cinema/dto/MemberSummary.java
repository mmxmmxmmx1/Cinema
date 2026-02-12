package com.example.cinema.dto;

import java.util.List;

public record MemberSummary(int points, List<MemberBooking> upcomingBookings) {
}
