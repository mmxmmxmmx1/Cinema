package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.service.EmployeeTodoService;

@WebMvcTest(EmployeeTodoController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("員工待辦控制器")
class EmployeeTodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeTodoService employeeTodoService;

    @Test
    @DisplayName("POST /employee/todos 應更新今日待辦")
    void shouldReplaceTodos() throws Exception {
        mockMvc.perform(post("/employee/todos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"A 任務\",\"B 任務\"]}"))
                .andExpect(status().isNoContent());

        verify(employeeTodoService).replaceTodayTodos(any(), eq("unknown"));
    }
}
