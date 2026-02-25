package com.example.cinema.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.example.cinema.service.UserService;

@WebMvcTest(MemberPasswordController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("會員密碼流程控制器")
class MemberPasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("GET /member/password/forgot 應顯示忘記密碼頁")
    void shouldRenderForgotPage() throws Exception {
        mockMvc.perform(get("/member/password/forgot"))
                .andExpect(status().isOk())
                .andExpect(view().name("member-password-forgot"));
    }

    @Test
    @DisplayName("POST /member/password/forgot 應建立重設碼並導向 reset")
    void shouldIssueResetToken() throws Exception {
        when(userService.issueMemberPasswordResetToken("member01")).thenReturn("ABC123XYZ9");

        mockMvc.perform(post("/member/password/forgot")
                        .with(csrf())
                        .param("username", "member01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/password/reset?username=member01"))
                .andExpect(flash().attributeExists("success"))
                .andExpect(flash().attribute("demoToken", "ABC123XYZ9"));
    }

    @Test
    @DisplayName("POST /member/password/reset 密碼不一致時應回錯")
    void shouldRejectResetWhenConfirmMismatch() throws Exception {
        mockMvc.perform(post("/member/password/reset")
                        .with(csrf())
                        .param("username", "member01")
                        .param("token", "ABCDE12345")
                        .param("newPassword", "1234")
                        .param("confirmPassword", "5678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/password/reset?username=member01"))
                .andExpect(flash().attribute("error", "新密碼與確認密碼不一致。"));
    }

    @Test
    @DisplayName("POST /member/password/reset 成功時應導回登入頁")
    void shouldResetPassword() throws Exception {
        doNothing().when(userService).resetMemberPasswordWithToken("member01", "ABCDE12345", "1234");

        mockMvc.perform(post("/member/password/reset")
                        .with(csrf())
                        .param("username", "member01")
                        .param("token", "ABCDE12345")
                        .param("newPassword", "1234")
                        .param("confirmPassword", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/login"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    @DisplayName("POST /member/password/change 成功時應導回登入頁")
    void shouldChangePassword() throws Exception {
        doNothing().when(userService).changeMemberPassword("test123", "old1234", "new1234");

        mockMvc.perform(post("/member/password/change")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "test123",
                                "N/A",
                                AuthorityUtils.createAuthorityList("ROLE_MEMBER"))))
                        .with(csrf())
                        .param("currentPassword", "old1234")
                        .param("newPassword", "new1234")
                        .param("confirmPassword", "new1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/login"))
                .andExpect(flash().attributeExists("success"));

        verify(userService).changeMemberPassword("test123", "old1234", "new1234");
    }
}
