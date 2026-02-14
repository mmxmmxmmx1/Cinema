package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.dto.OrderSummaryResponse;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.Movie;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.MovieService;

@WebMvcTest(MemberOrdersPageController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("會員訂單頁面控制器")
class MemberOrdersPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberOrderService memberOrderService;

    @MockBean
    private MovieService movieService;

    @Test
    @DisplayName("取消訂單違規時應顯示錯誤訊息")
    void shouldShowErrorWhenCancelRejected() throws Exception {
        doThrow(new TicketPurchaseRuleViolationException("開演前 30 分鐘內不可取消訂單。"))
                .when(memberOrderService).cancelOrder(nullable(String.class), anyLong());

        mockMvc.perform(post("/member/orders/100/cancel").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/orders"))
                .andExpect(flash().attribute("error", "開演前 30 分鐘內不可取消訂單。"));
    }

    @Test
    @DisplayName("取消訂單成功時應顯示成功訊息")
    void shouldShowSuccessWhenCancelAccepted() throws Exception {
        mockMvc.perform(post("/member/orders/101/cancel").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/orders"))
                .andExpect(flash().attributeExists("success"));

        verify(memberOrderService).cancelOrder(nullable(String.class), anyLong());
    }

    @Test
    @DisplayName("列表應以 MM/dd HH:mm 統一顯示場次時間")
    void shouldRenderConsistentShowtimeLabelFromOrderInstant() throws Exception {
        Instant showStart = ZonedDateTime.of(2026, 2, 12, 8, 50, 0, 0,
                ZoneId.of("Asia/Taipei")).toInstant();
        when(memberOrderService.listAllOrders(nullable(String.class), anyInt())).thenReturn(List.of(
                new OrderSummaryResponse(1L, "mv-02", "mv-02-st1", "1號廳", 4, 1200, "PAID",
                        Instant.parse("2026-02-12T00:00:00Z"), Instant.parse("2026-02-12T00:05:00Z"), showStart)));
        when(movieService.getMovieWithAvailability("mv-02"))
                .thenReturn(Optional.of(new Movie("mv-02", "奧本海默", "", "", "", List.of())));
        when(movieService.getShowtime("mv-02", "mv-02-st1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/member/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("member-orders"))
                .andExpect(model().attributeExists("activePaidOrders"))
                .andExpect(content().string(containsString("02/12 08:50")));
    }
}
