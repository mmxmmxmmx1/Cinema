package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.cinema.model.User.UserType;
import com.example.cinema.service.UserService;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.sql.init.mode=never",
        "spring.devtools.restart.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "mysql.it", matches = "true")
@DisplayName("真 MySQL 整合測試（Testcontainers）")
class RealMySqlContainerIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("cinema_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("應可在真 MySQL 執行 Flyway，並完成會員/員工註冊")
    void shouldRunFlywayAndRegisterUsersAgainstRealMySql() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertNotNull(migrationCount);
        assertTrue(migrationCount > 0, "Flyway migration 應已成功執行");

        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code IN ('EMPLOYEE', 'IT', 'MANAGER', 'ADMIN')",
                Integer.class);
        assertNotNull(roleCount);
        assertEquals(4, roleCount.intValue(), "角色資料應完整建立");

        String memberName = "mysqlm" + System.nanoTime();
        Long memberId = userService.registerUser(memberName, "Abc123", UserType.MEMBER, false);
        String savedMember = jdbcTemplate.queryForObject(
                "SELECT nickname FROM members WHERE id = ?",
                String.class,
                memberId);
        assertEquals(memberName, savedMember);

        String employeeName = "mysqle" + System.nanoTime();
        Long employeeId = userService.registerEmployee(employeeName, "Abc123");
        String roleCode = jdbcTemplate.queryForObject(
                "SELECT r.code FROM employee e JOIN roles r ON r.id = e.role_id WHERE e.id = ?",
                String.class,
                employeeId);
        assertEquals("EMPLOYEE", roleCode);
    }
}
