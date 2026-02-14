package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.exception.RateLimitExceededException;
import com.example.cinema.service.ApiRateLimitService;
import com.example.cinema.service.MemberOrderService;

@WebMvcTest(MemberOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("會員訂單 API 限流測試")
class MemberOrderControllerRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberOrderService memberOrderService;

    @MockBean
    private ApiRateLimitService apiRateLimitService;

    @Test
    @DisplayName("超過限流時應返回 429")
    void shouldReturn429WhenRateLimited() throws Exception {
        doThrow(new RateLimitExceededException("操作過於頻繁，請稍後再試。"))
                .when(apiRateLimitService)
                .check(anyString(), anyString(), anyInt(), any());

        mockMvc.perform(post("/member/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"movieId\":\"mv-01\",\"showtimeId\":\"mv-01-st1\",\"seatIds\":[\"A01\"]}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("操作過於頻繁，請稍後再試。"));
    }
}
