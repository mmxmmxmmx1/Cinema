package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cinema.config.SecurityConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
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

    @Test
    @DisplayName("會員登出：有 CSRF 時應成功導向會員登入頁")
    void shouldLogoutMemberWithCsrf() throws Exception {
                mockMvc.perform(post("/member/logout")
                        .with(user("test123").roles("MEMBER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
    }

    @Test
    @DisplayName("會員登出：缺少 CSRF 時應回 403")
    void shouldRejectMemberLogoutWithoutCsrf() throws Exception {
        mockMvc.perform(post("/member/logout")
                        .with(user("test123").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("員工登出：有 CSRF 時應成功導向員工登入頁")
    void shouldLogoutEmployeeWithCsrf() throws Exception {
                mockMvc.perform(post("/employee/logout")
                        .with(user("emp01").roles("EMPLOYEE"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/login"));
    }

    @Test
    @DisplayName("員工登出：缺少 CSRF 時應回 403")
    void shouldRejectEmployeeLogoutWithoutCsrf() throws Exception {
        mockMvc.perform(post("/employee/logout")
                        .with(user("emp01").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTPS 請求應帶有 HSTS 安全標頭")
    void shouldReturnHstsHeaderForSecureRequest() throws Exception {
        mockMvc.perform(get("/").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", Matchers.containsString("includeSubDomains")));
    }

    @Test
    @DisplayName("SPA 路徑應套用 SPA CSP（包含 unsafe-eval）")
    void shouldApplySpaCspOnSpaRoutes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberAuthenticated", true);

        mockMvc.perform(get("/movies/mv-01").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("script-src 'self' 'unsafe-eval'")));
    }

    @Test
    @DisplayName("API 路徑應套用標準 CSP（不包含 unsafe-eval）")
    void shouldApplyStandardCspOnApiRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", Matchers.not(Matchers.containsString("'unsafe-eval'"))));
    }

    @Test
    @DisplayName("v1 API 別名應可匿名存取健康檢查")
    void shouldAllowAnonymousAccessToV1HealthApi() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("不應提供電影院地區清單 API")
    void shouldNotExposeCinemaLocationsApi() throws Exception {
        mockMvc.perform(get("/api/movies/locations"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未登入直接開啟 movies 深連結應導回首頁")
    void shouldRedirectAnonymousMoviesDeepLinkToHome() throws Exception {
        mockMvc.perform(get("/movies/mv-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("未登入直接開啟 checkout 深連結應導回首頁")
    void shouldRedirectAnonymousCheckoutDeepLinkToHome() throws Exception {
        mockMvc.perform(get("/checkout/mv-01/showtimes/mv-01-st1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("未登入直接開啟 orders 深連結應導回首頁")
    void shouldRedirectAnonymousOrdersDeepLinkToHome() throws Exception {
        mockMvc.perform(get("/orders/123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("會員已登入時可開啟 movies 深連結並載入 SPA 入口頁")
    void shouldServeSpaEntryForMemberMoviesDeepLink() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberAuthenticated", true);

        mockMvc.perform(get("/movies/mv-01").session(session))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("會員已登入時可開啟 checkout 深連結並載入 SPA 入口頁")
    void shouldServeSpaEntryForMemberCheckoutDeepLink() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberAuthenticated", true);

        mockMvc.perform(get("/checkout/mv-01/showtimes/mv-01-st1").session(session))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("會員已登入時可開啟 orders 深連結並載入 SPA 入口頁")
    void shouldServeSpaEntryForMemberOrdersDeepLink() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberAuthenticated", true);

        mockMvc.perform(get("/orders/123").session(session))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("會員登出不應清掉員工登入狀態（同一瀏覽器 session 可雙登入）")
    void shouldKeepEmployeeContextAfterMemberLogout() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberAuthenticated", true);
        session.setAttribute("employeeAuthenticated", true);
        session.setAttribute(
                SecurityConfig.MEMBER_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                        "member01",
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_MEMBER"))));
        session.setAttribute(
                SecurityConfig.EMPLOYEE_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                        "emp01",
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE"))));

        mockMvc.perform(post("/member/logout")
                .session(session)
                .with(user("member01").roles("MEMBER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));

        assertNull(session.getAttribute(SecurityConfig.MEMBER_SECURITY_CONTEXT_KEY));
        assertNotNull(session.getAttribute(SecurityConfig.EMPLOYEE_SECURITY_CONTEXT_KEY));
        assertTrue(Boolean.TRUE.equals(session.getAttribute("employeeAuthenticated")));

        mockMvc.perform(get("/employee")
                .session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("員工登出不應清掉會員登入狀態（同一瀏覽器 session 可雙登入）")
    void shouldKeepMemberContextAfterEmployeeLogout() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("memberAuthenticated", true);
        session.setAttribute("employeeAuthenticated", true);
        session.setAttribute(
                SecurityConfig.MEMBER_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                        "member01",
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_MEMBER"))));
        session.setAttribute(
                SecurityConfig.EMPLOYEE_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                        "emp01",
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE"))));

        mockMvc.perform(post("/employee/logout")
                .session(session)
                .with(user("emp01").roles("EMPLOYEE"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/login"));

        assertNull(session.getAttribute(SecurityConfig.EMPLOYEE_SECURITY_CONTEXT_KEY));
        assertNotNull(session.getAttribute(SecurityConfig.MEMBER_SECURITY_CONTEXT_KEY));
        assertTrue(Boolean.TRUE.equals(session.getAttribute("memberAuthenticated")));

        mockMvc.perform(get("/member")
                .session(session))
                .andExpect(status().isOk());
    }
}
