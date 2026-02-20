package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.example.cinema.model.Showtime;
import com.example.cinema.service.MovieService;

@WebMvcTest(EmployeeShowtimeAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("管理員場次管理控制器")
class EmployeeShowtimeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieService movieService;

    @Test
    @DisplayName("GET /employee/admin/showtimes 應顯示管理頁")
    void shouldRenderShowtimeAdminPage() throws Exception {
        when(movieService.listCatalogItems()).thenReturn(List.of(
                new MovieService.MovieCatalogItem("mv-01", "測試電影")));
        when(movieService.listCinemaLocations()).thenReturn(List.of(
                new MovieService.CinemaLocationItem("taipei-main", "台北信義館", "台北", 10)));
        when(movieService.listConfiguredShowtimes("mv-01")).thenReturn(List.of(
                new Showtime("mv-01-st1", "10:00", 120, "1號廳", "taipei-main", "台北信義館")));

        mockMvc.perform(get("/employee/admin/showtimes"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-showtimes"))
                .andExpect(model().attributeExists("movies"))
                .andExpect(model().attributeExists("showtimes"))
                .andExpect(model().attributeExists("locations"));
    }

    @Test
    @DisplayName("POST /employee/admin/showtimes/save 應儲存並導回")
    void shouldSaveShowtime() throws Exception {
        doNothing().when(movieService).saveShowtimeOverride(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString());

        mockMvc.perform(post("/employee/admin/showtimes/save")
                        .with(csrf())
                        .param("movieId", "mv-01")
                        .param("showtimeId", "mv-01-st9")
                        .param("startTime", "22:20")
                        .param("durationMinutes", "130")
                        .param("auditorium", "2號廳")
                        .param("locationCode", "taipei-main"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/admin/showtimes?movieId=mv-01"));
    }
}
