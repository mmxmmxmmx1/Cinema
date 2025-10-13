import java.sql.*;
String url = "jdbc:mysql://localhost:3306/";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement st = conn.createStatement()) {
    st.executeUpdate("CREATE DATABASE IF NOT EXISTS cinema");
    System.out.println("CREATED_OR_EXISTS");
} catch (Exception e) {
    System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
