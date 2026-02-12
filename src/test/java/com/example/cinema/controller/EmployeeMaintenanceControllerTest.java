package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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

import com.example.cinema.model.Movie;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.MaintenanceRequestService;
import com.example.cinema.service.MovieService;

@WebMvcTest(EmployeeMaintenanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("維修申請控制器")
class EmployeeMaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaintenanceRequestService maintenanceRequestService;

    @MockBean
    private MovieService movieService;

    @Test
    @DisplayName("GET /employee/maintenance 應顯示申請頁")
    void shouldRenderMaintenancePage() throws Exception {
        when(movieService.getMovies()).thenReturn(List.of(
                new Movie("mv-01", "測試", "", "", "", List.of(new Showtime("st1", "10:00", 120, "1號廳")))));
        when(maintenanceRequestService.listRecent(50)).thenReturn(List.of());

        mockMvc.perform(get("/employee/maintenance"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-maintenance"))
                .andExpect(model().attributeExists("halls"));
    }

    @Test
    @DisplayName("POST /employee/maintenance 應送出並回傳追蹤編號")
    void shouldSubmitMaintenanceRequest() throws Exception {
        when(maintenanceRequestService.createRequest(any(), any(), any(), any(), any()))
                .thenReturn("MR-20260212-00001");

        mockMvc.perform(post("/employee/maintenance")
                        .with(csrf())
                        .param("auditorium", "1號廳")
                        .param("title", "冷氣異常")
                        .param("description", "溫度過高")
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/maintenance"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("POST /employee/maintenance/{id}/status 應更新狀態")
    void shouldUpdateMaintenanceStatus() throws Exception {
        doNothing().when(maintenanceRequestService).updateStatus(anyLong(), any(), any(), any(), any());

        mockMvc.perform(post("/employee/maintenance/1/status")
                        .with(csrf())
                        .param("status", "IN_PROGRESS")
                        .param("assignee", "it01")
                        .param("resolutionNote", "開始處理"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/maintenance"))
                .andExpect(flash().attributeExists("success"));
    }
}
