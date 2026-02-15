package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.service.EmployeeTodoService;
import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberNotificationService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.MovieService;
import com.example.cinema.service.OperationsDashboardService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

@WebMvcTest(PageController.class)
@AutoConfigureMockMvc
class PageControllerMemberPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieService movieService;

    @MockBean
    private EmployeeTodoService employeeTodoService;

    @MockBean
    private MemberLoyaltyService memberLoyaltyService;

    @MockBean
    private MemberOrderService memberOrderService;

    @MockBean
    private MemberNotificationService memberNotificationService;

    @MockBean
    private OperationsDashboardService operationsDashboardService;

    @MockBean
    private SessionService sessionService;

    @Test
    void memberPageShouldNotShowOrderErrorWhenOnlyPointsLookupFails() throws Exception {
        when(sessionService.isAuthenticated(any(), eq(Realm.MEMBER))).thenReturn(true);
        when(memberLoyaltyService.currentPoints("test123"))
                .thenThrow(new RuntimeException("point-balance-row-missing"));
        when(memberOrderService.listActiveOrders("test123", 5)).thenReturn(List.of());
        when(memberOrderService.listHistoryOrders("test123", 5)).thenReturn(List.of());
        when(memberOrderService.listUpcomingBookings("test123", 1)).thenReturn(List.of());
        when(memberNotificationService.listForMember("test123", 5)).thenReturn(List.of());
        when(memberNotificationService.unreadCount("test123")).thenReturn(0);

        mockMvc.perform(get("/member").with(user("test123").roles("MEMBER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("member"))
                .andExpect(model().attribute("memberPoints", 0))
                .andExpect(model().attributeDoesNotExist("orderLoadError"));
    }

    @Test
    void memberPageShouldShowFriendlyOrderErrorWhenOrderLookupFails() throws Exception {
        when(sessionService.isAuthenticated(any(), eq(Realm.MEMBER))).thenReturn(true);
        when(memberLoyaltyService.currentPoints("test123")).thenReturn(0);
        when(memberOrderService.listActiveOrders("test123", 5))
                .thenThrow(new RuntimeException("db down"));
        when(memberNotificationService.listForMember("test123", 5)).thenReturn(List.of());
        when(memberNotificationService.unreadCount("test123")).thenReturn(0);

        mockMvc.perform(get("/member").with(user("test123").roles("MEMBER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("member"))
                .andExpect(model().attribute("orderLoadError", "訂單暫時無法顯示，請稍後再試。"));
    }
}
