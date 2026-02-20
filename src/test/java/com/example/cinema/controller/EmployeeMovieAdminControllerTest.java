package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
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

import com.example.cinema.service.MovieService;

@WebMvcTest(EmployeeMovieAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("管理員電影 CMS 控制器")
class EmployeeMovieAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieService movieService;

    @Test
    @DisplayName("GET /employee/admin/movies 應顯示電影管理頁")
    void shouldRenderMovieAdminPage() throws Exception {
        when(movieService.listMovieAdminItems()).thenReturn(List.of(
                new MovieService.MovieAdminItem(
                        "mv-01",
                        "測試電影",
                        "",
                        "https://example.com/poster.jpg",
                        "https://example.com/carousel.jpg",
                        "desc",
                        true,
                        10)));
        when(movieService.getMovieAdminItem("mv-01")).thenReturn(java.util.Optional.of(
                new MovieService.MovieAdminItem(
                        "mv-01",
                        "測試電影",
                        "",
                        "https://example.com/poster.jpg",
                        "https://example.com/carousel.jpg",
                        "desc",
                        true,
                        10)));

        mockMvc.perform(get("/employee/admin/movies"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-movies"))
                .andExpect(model().attributeExists("movies"))
                .andExpect(model().attributeExists("selectedMovie"));
    }

    @Test
    @DisplayName("POST /employee/admin/movies/save 應儲存並導回")
    void shouldSaveMovieCatalog() throws Exception {
        doNothing().when(movieService).saveMovieCatalog(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyBoolean(),
                anyString());

        mockMvc.perform(post("/employee/admin/movies/save")
                        .with(csrf())
                        .param("movieId", "mv-11")
                        .param("title", "新電影")
                        .param("subtitle", "")
                        .param("posterUrl", "https://example.com/p.jpg")
                        .param("carouselImageUrl", "https://example.com/c.jpg")
                        .param("description", "描述")
                        .param("sortOrder", "110")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/employee/admin/movies?movieId=mv-11"));
    }
}
