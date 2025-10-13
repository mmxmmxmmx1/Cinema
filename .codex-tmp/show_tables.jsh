import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement st = conn.createStatement();
     ResultSet rs = st.executeQuery("SHOW TABLES")) {
    while (rs.next()) {
        System.out.println(rs.getString(1));
    }
}
