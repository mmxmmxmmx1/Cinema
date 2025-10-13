import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement st = conn.createStatement()) {
    st.executeUpdate("CREATE TABLE IF NOT EXISTS roles (" +
                     "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                     "code VARCHAR(50) NOT NULL UNIQUE, " +
                     "name VARCHAR(100) NOT NULL)");
    st.executeUpdate("CREATE TABLE IF NOT EXISTS member_roles (" +
                     "member_id BIGINT NOT NULL, " +
                     "role_id BIGINT NOT NULL, " +
                     "PRIMARY KEY (member_id, role_id), " +
                     "FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE, " +
                     "FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE)");
    st.executeUpdate("INSERT IGNORE INTO roles(code, name) VALUES " +
                     "('EMPLOYEE','EMPLOYEE')," +
                     "('ADMIN','ADMIN')," +
                     "('SUPERVISOR','SUPERVISOR')");
    System.out.println("ROLES_READY");
} catch (Exception e) {
    System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
