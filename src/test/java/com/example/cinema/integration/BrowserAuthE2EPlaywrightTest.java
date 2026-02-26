package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "browser.e2e", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("瀏覽器 E2E（Playwright）登入與登出流程")
class BrowserAuthE2EPlaywrightTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void startBrowser() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putIfAbsent("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void stopBrowser() {
        if (context != null) {
            context.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void setUpSchemaAndPage() {
        resetAuthSchema();
        context = browser.newContext();
        page = context.newPage();
        page.setDefaultTimeout(10_000);
    }

    @AfterEach
    void tearDownPage() {
        if (context != null) {
            context.close();
            context = null;
            page = null;
        }
    }

    @Test
    @DisplayName("會員應可從登入頁登入並成功登出")
    void memberShouldLoginAndLogout() {
        navigate(baseUrl("/member/login"));
        page.fill("input[name='username']", "test123");
        page.fill("input[name='password']", "test123");
        page.click("button[type='submit']");

        page.waitForURL("**/member");
        assertTrue(page.url().contains("/member"));
        assertTrue(page.locator("body").innerText().contains("很好睡電影院會員專區"));

        page.click("button:has-text('登出會員')");
        page.waitForURL("**/member/login");
        assertTrue(page.url().contains("/member/login"));
    }

    @Test
    @DisplayName("員工應可從登入頁登入並成功登出")
    void employeeShouldLoginAndLogout() {
        navigate(baseUrl("/employee/login"));
        page.fill("input[name='username']", "emp01");
        page.fill("input[name='password']", "emp01");
        page.click("button[type='submit']");

        page.waitForURL("**/employee");
        assertTrue(page.url().contains("/employee"));
        assertTrue(page.locator("body").innerText().contains("很好睡電影院員工後台"));

        page.click("button:has-text('登出員工帳號')");
        page.waitForURL("**/employee/login");
        assertTrue(page.url().contains("/employee/login"));
    }

    @Test
    @DisplayName("未登入直接開啟 movies 深連結應導回首頁")
    void anonymousMoviesDeepLinkShouldRedirectHome() {
        navigate(baseUrl("/movies/mv-01"));
        waitForPath("/");
        assertEquals("/", currentPath());
    }

    @Test
    @DisplayName("未登入直接開啟 checkout 深連結應導回首頁")
    void anonymousCheckoutDeepLinkShouldRedirectHome() {
        navigate(baseUrl("/checkout/mv-01/showtimes/mv-01-st1"));
        waitForPath("/");
        assertEquals("/", currentPath());
    }

    @Test
    @DisplayName("未登入直接開啟 orders 深連結應導回首頁")
    void anonymousOrdersDeepLinkShouldRedirectHome() {
        navigate(baseUrl("/orders/123"));
        waitForPath("/");
        assertEquals("/", currentPath());
    }

    private void navigate(String url) {
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    private String baseUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private String currentPath() {
        return URI.create(page.url()).getPath();
    }

    private void waitForPath(String expectedPath) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (expectedPath.equals(currentPath())) {
                return;
            }
            page.waitForTimeout(100);
        }
    }

    private void resetAuthSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("DROP TABLE IF EXISTS roles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS members");

        jdbcTemplate.execute(
                "CREATE TABLE members (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "nickname VARCHAR(100) NOT NULL UNIQUE, " +
                        "first_name VARCHAR(100) NOT NULL DEFAULT '', " +
                        "last_name VARCHAR(100) NOT NULL DEFAULT '', " +
                        "email VARCHAR(255), " +
                        "phone VARCHAR(50), " +
                        "password VARCHAR(255) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.execute(
                "CREATE TABLE roles (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "code VARCHAR(32) NOT NULL UNIQUE, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "description VARCHAR(255), " +
                        "level INT NOT NULL)");

        jdbcTemplate.execute(
                "CREATE TABLE employee (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "nickname VARCHAR(100) NOT NULL UNIQUE, " +
                        "first_name VARCHAR(100) NOT NULL DEFAULT '', " +
                        "last_name VARCHAR(100) NOT NULL DEFAULT '', " +
                        "email VARCHAR(255), " +
                        "phone VARCHAR(50), " +
                        "password VARCHAR(255) NOT NULL, " +
                        "role_id BIGINT NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "CONSTRAINT fk_employee_role FOREIGN KEY (role_id) REFERENCES roles(id))");

        jdbcTemplate.update(
                "INSERT INTO roles (code, name, description, level) VALUES (?, ?, ?, ?)",
                "EMPLOYEE",
                "Employee",
                "Employee role",
                10);
        jdbcTemplate.update(
                "INSERT INTO roles (code, name, description, level) VALUES (?, ?, ?, ?)",
                "IT",
                "IT",
                "IT role",
                20);
        jdbcTemplate.update(
                "INSERT INTO roles (code, name, description, level) VALUES (?, ?, ?, ?)",
                "MANAGER",
                "Manager",
                "Manager role",
                30);
        jdbcTemplate.update(
                "INSERT INTO roles (code, name, description, level) VALUES (?, ?, ?, ?)",
                "ADMIN",
                "Admin",
                "Admin role",
                40);

        jdbcTemplate.update(
                "INSERT INTO members (nickname, password) VALUES (?, ?)",
                "test123",
                "{noop}test123");

        jdbcTemplate.update(
                "INSERT INTO employee (nickname, password, role_id) VALUES (?, ?, (SELECT id FROM roles WHERE code = 'EMPLOYEE'))",
                "emp01",
                "{noop}emp01");
    }
}
