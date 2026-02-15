package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.service.EmployeeAdminService;
import com.example.cinema.service.EmployeeTodoService;
import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberNotificationService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.MovieService;
import com.example.cinema.service.OperationsDashboardService;

@WebMvcTest(EmployeeAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("管理員維護工具控制器")
class EmployeeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeAdminService employeeAdminService;

    @MockBean
    private MemberNotificationService memberNotificationService;

    @MockBean
    private MemberOrderService memberOrderService;

    @MockBean
    private MemberLoyaltyService memberLoyaltyService;

    @MockBean
    private EmployeeTodoService employeeTodoService;

    @MockBean
    private OperationsDashboardService operationsDashboardService;

    @MockBean
    private MovieService movieService;

    @Test
    @DisplayName("GET /employee/admin/tools 應顯示管理工具頁")
    void shouldRenderAdminToolsPage() throws Exception {
        when(operationsDashboardService.cleanupSnapshot())
                .thenReturn(new OperationsDashboardService.CleanupSnapshot(2, 5));
        when(movieService.listCatalogItems()).thenReturn(List.of());

        mockMvc.perform(get("/employee/admin/tools"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-tools"));
    }

    @Test
    @DisplayName("POST /employee/admin/tools/repair-order-status 應執行修復並回傳成功訊息")
    void shouldRepairOrderStatus() throws Exception {
        when(memberOrderService.repairOrderStatuses())
                .thenReturn(new MemberOrderService.OrderRepairSummary(1, 2, 3, 4));

        mockMvc.perform(post("/employee/admin/tools/repair-order-status").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/admin/tools"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("POST /employee/admin/tools/recalculate-points 應執行點數重算")
    void shouldRecalculatePoints() throws Exception {
        when(memberLoyaltyService.recalculateAllMemberPointBalances()).thenReturn(3);

        mockMvc.perform(post("/employee/admin/tools/recalculate-points").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/admin/tools"))
                .andExpect(flash().attribute("success", "點數重算完成，共更新 3 位會員。"));
    }

    @Test
    @DisplayName("POST /employee/admin/tools/reset-todos 應重置今日待辦")
    void shouldResetTodos() throws Exception {
        doNothing().when(employeeTodoService).replaceTodayTodos(any(), any());

        mockMvc.perform(post("/employee/admin/tools/reset-todos").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/admin/tools"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("POST /employee/admin/tools/update-poster 應更新海報並回傳成功訊息")
    void shouldUpdatePosterUrl() throws Exception {
        doNothing().when(movieService).updatePosterUrl("mv-01", "https://example.com/p1.jpg", "admin-tools");

        mockMvc.perform(post("/employee/admin/tools/update-poster")
                        .with(csrf())
                        .param("movieId", "mv-01")
                        .param("posterUrl", "https://example.com/p1.jpg"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/admin/tools"))
                .andExpect(flash().attributeExists("success"));
    }
}
