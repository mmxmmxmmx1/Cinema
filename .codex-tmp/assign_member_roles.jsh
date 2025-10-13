import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.setAutoCommit(false);
    String sql = "INSERT IGNORE INTO member_roles(member_id, role_id) " +
                 "SELECT m.id, r.id FROM members m JOIN roles r ON r.code=? WHERE m.email=?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        // joe -> EMPLOYEE
        ps.setString(1, "EMPLOYEE");
        ps.setString(2, "joe.luv2code.com");
        int a = ps.executeUpdate();
        // mary -> ADMIN
        ps.setString(1, "ADMIN");
        ps.setString(2, "mary.luv2code.com");
        int b = ps.executeUpdate();
        // susan -> SUPERVISOR
        ps.setString(1, "SUPERVISOR");
        ps.setString(2, "susan.luv2code.com");
        int c = ps.executeUpdate();
        conn.commit();
        System.out.println("ASSIGNED=" + a + "," + b + "," + c);
    }
}
