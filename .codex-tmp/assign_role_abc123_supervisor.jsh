import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.setAutoCommit(false);
    String sql = "INSERT IGNORE INTO member_roles(member_id, role_id) " +
                 "SELECT m.id, r.id FROM members m JOIN roles r ON r.code=? WHERE m.email=?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, "SUPERVISOR");
        ps.setString(2, "abc123@luv2code.com");
        int a = ps.executeUpdate();
        conn.commit();
        System.out.println("ASSIGNED=" + a);
    }
    try (PreparedStatement ps = conn.prepareStatement("SELECT r.code FROM member_roles mr JOIN members m ON mr.member_id=m.id JOIN roles r ON mr.role_id=r.id WHERE m.email=?")) {
        ps.setString(1, "abc123@luv2code.com");
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.println("ROLE=" + rs.getString(1));
            }
        }
    }
}
