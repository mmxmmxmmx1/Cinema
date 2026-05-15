package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

@WebMvcTest(AuthPageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PageSessionSupport.class)
class AuthPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
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
