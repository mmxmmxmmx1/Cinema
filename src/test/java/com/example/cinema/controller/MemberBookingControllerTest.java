package com.example.cinema.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberBookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("舊版訂票 API 下線測試")
class MemberBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /member/api/bookings 應回 410 Gone")
    void shouldReturnGoneForDeprecatedBookingApi() throws Exception {
        mockMvc.perform(post("/member/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"movieId\":\"mv-01\",\"showtimeId\":\"mv-01-st1\",\"seatIds\":[\"A01\"]}"))
                .andExpect(status().isGone());
    }
}
