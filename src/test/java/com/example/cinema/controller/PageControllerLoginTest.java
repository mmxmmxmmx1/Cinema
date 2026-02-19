package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.service.MovieService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

@WebMvcTest(PageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PageControllerLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieService movieService;

    @MockBean
    private SessionService sessionService;

    @Test
    void loginDefaultsToMemberTarget() throws Exception {
        when(sessionService.isAuthenticated(any(), eq(Realm.MEMBER))).thenReturn(false);
        when(sessionService.isAuthenticated(any(), eq(Realm.EMPLOYEE))).thenReturn(false);

        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
    }

    @Test
    void loginTargetEmployeeUsesEmployeeFormAction() throws Exception {
        when(sessionService.isAuthenticated(any(), eq(Realm.MEMBER))).thenReturn(false);
        when(sessionService.isAuthenticated(any(), eq(Realm.EMPLOYEE))).thenReturn(false);

        mockMvc.perform(get("/login").param("target", "employee"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/login"));
    }
}
