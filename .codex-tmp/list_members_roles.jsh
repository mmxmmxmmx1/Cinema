import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    try (Statement st = conn.createStatement()) {
        ResultSet rs1 = st.executeQuery("SELECT id, first_name, last_name, email FROM members");
        System.out.println("MEMBERS:");
        while (rs1.next()) {
            System.out.println(rs1.getLong(1)+","+rs1.getString(2)+","+rs1.getString(3)+","+rs1.getString(4));
        }
        ResultSet rs2 = st.executeQuery("SELECT id, code FROM roles");
        System.out.println("ROLES:");
        while (rs2.next()) {
            System.out.println(rs2.getLong(1)+","+rs2.getString(2));
        }
    }
}
