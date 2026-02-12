package com.example.cinema.controller;

import com.example.cinema.dto.UpcomingBookingResponse;
import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MemberApiController 測試單元
 * 測試會員 API 控制器的功能
 */
@WebMvcTest(MemberApiController.class)
@DisplayName("會員 API 控制器測試")
class MemberApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;
    @MockBean
    private MemberLoyaltyService memberLoyaltyService;
    @MockBean
    private MemberOrderService memberOrderService;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        when(sessionService.guestWatchlistKey()).thenReturn("guest.watchlist");
    }

    @Test
    @DisplayName("應該能夠添加電影到訪客觀看清單")
    @WithMockUser
    void shouldAddMovieToGuestWatchlist() throws Exception {
        // Given
        Long movieId = 1L;

        // When & Then
        mockMvc.perform(post("/api/guest/watchlist/{movieId}", movieId)
                .session(session)
                .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("應該能夠獲取訪客觀看清單")
    @WithMockUser
    void shouldGetGuestWatchlist() throws Exception {
        // Given
        Set<Long> watchlist = new HashSet<>();
        watchlist.add(1L);
        watchlist.add(2L);
        session.setAttribute("guest.watchlist", watchlist);

        // When & Then
        mockMvc.perform(get("/api/guest/watchlist")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("當觀看清單為空時應該返回空集合")
    @WithMockUser
    void shouldReturnEmptySetWhenWatchlistIsEmpty() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/guest/watchlist")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("已認證的會員應該能夠獲取摘要信息")
    @WithMockUser
    void shouldGetMemberSummaryWhenAuthenticated() throws Exception {
        // Given
        when(sessionService.isAuthenticated(any(), eq(Realm.MEMBER))).thenReturn(true);
        when(memberLoyaltyService.currentPoints(any())).thenReturn(360);
        when(memberOrderService.listUpcomingBookings(any(), eq(5))).thenReturn(List.of(
                new UpcomingBookingResponse(1L, "mv-01", "沙丘:第二部", "mv-01-st1", "1號廳", 2, null, "02/12 18:40")));

        // When & Then
        mockMvc.perform(get("/api/member/summary")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(360))
                .andExpect(jsonPath("$.upcomingBookings").isArray())
                .andExpect(jsonPath("$.upcomingBookings.length()").value(1))
                .andExpect(jsonPath("$.upcomingBookings[0].movieTitle").value("沙丘:第二部"));
    }

    @Test
    @DisplayName("未認證的用戶獲取會員摘要應該返回 401")
    @WithMockUser
    void shouldReturn401WhenNotAuthenticatedForMemberSummary() throws Exception {
        // Given
        when(sessionService.isAuthenticated(any(), eq(Realm.MEMBER))).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/member/summary")
                .session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("應該能夠多次添加不同電影到觀看清單")
    @WithMockUser
    void shouldAddMultipleMoviesToWatchlist() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/guest/watchlist/{movieId}", 1L)
                .session(session)
                .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/guest/watchlist/{movieId}", 2L)
                .session(session)
                .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/guest/watchlist/{movieId}", 3L)
                .session(session)
                .with(csrf()))
                .andExpect(status().isNoContent());

        // 驗證觀看清單包含三部電影
        mockMvc.perform(get("/api/guest/watchlist")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
