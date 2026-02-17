package com.example.cinema.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("安全授權整合測試")
class SecurityAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("未登入存取會員頁應導向會員登入頁")
    void shouldRedirectAnonymousToMemberLogin() throws Exception {
        mockMvc.perform(get("/member/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/member/login"));
    }

    @Test
    @DisplayName("未登入存取員工管理頁應導向員工登入頁")
    void shouldRedirectAnonymousToEmployeeLogin() throws Exception {
        mockMvc.perform(get("/employee/admin/tools"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/employee/login"));
    }

    @Test
    @DisplayName("會員不可存取員工管理頁")
    void shouldForbidMemberAccessToEmployeeAdmin() throws Exception {
        mockMvc.perform(get("/employee/admin/tools")
                        .with(user("test123").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("員工不可存取會員訂單頁")
    void shouldForbidEmployeeAccessToMemberOrders() throws Exception {
        mockMvc.perform(get("/member/orders")
                        .with(user("emp01").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("員工不可呼叫會員 API，應回傳會員權限錯誤碼")
    void shouldReturnMemberAccessDeniedForEmployeeCallingMemberApi() throws Exception {
        mockMvc.perform(get("/member/api/orders")
                        .with(user("emp01").roles("EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("會員不可呼叫員工管理工具 API")
    void shouldForbidMemberPostingEmployeeAdminTool() throws Exception {
        mockMvc.perform(post("/employee/admin/tools/reset-todos")
                        .with(user("test123").roles("MEMBER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
