package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.example.cinema.service.EmployeeChecklistService;
import com.example.cinema.service.MovieService;

@WebMvcTest(EmployeeChecklistController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("員工影廳檢查表控制器")
class EmployeeChecklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeChecklistService employeeChecklistService;

    @MockBean
    private MovieService movieService;

    @Test
    @DisplayName("GET /employee/checklist 應回傳頁面與檢查項目")
    void shouldRenderChecklistPage() throws Exception {
        Movie movie = new Movie("mv-01", "測試電影", "", "", "", List.of(
                new Showtime("mv-01-st1", "10:00", 120, "1號廳"),
                new Showtime("mv-01-st2", "13:00", 120, "2號廳")));
        when(movieService.getMovies()).thenReturn(List.of(movie));
        when(employeeChecklistService.loadEntriesByDate(any())).thenReturn(java.util.Map.of());
        when(employeeChecklistService.loadHistorySince(any())).thenReturn(List.of());

        mockMvc.perform(get("/employee/checklist"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-checklist"))
                .andExpect(model().attributeExists("entries"))
                .andExpect(model().attributeExists("hallCompletion"));
    }

    @Test
    @DisplayName("POST /employee/checklist 應儲存並導回列表")
    void shouldSaveChecklistAndRedirect() throws Exception {
        when(employeeChecklistService.saveChecklist(any(), anyString(), anyList())).thenReturn(1);

        mockMvc.perform(post("/employee/checklist")
                        .with(csrf())
                        .param("checkDate", "2026-02-12")
                        .param("entries[0].auditorium", "1號廳")
                        .param("entries[0].itemCode", "SCREEN")
                        .param("entries[0].itemLabel", "螢幕與投影")
                        .param("entries[0].checked", "true")
                        .param("entries[0].note", "正常"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/checklist?date=2026-02-12"))
                .andExpect(flash().attributeExists("success"));
    }
}
