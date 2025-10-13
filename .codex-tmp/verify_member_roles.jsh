import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass); Statement st = conn.createStatement()) {
    ResultSet rs = st.executeQuery("SELECT m.email, r.code FROM member_roles mr JOIN members m ON mr.member_id=m.id JOIN roles r ON mr.role_id=r.id ORDER BY m.email");
    while (rs.next()) {
        System.out.println(rs.getString(1)+" -> "+rs.getString(2));
    }
}
