import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement st = conn.createStatement();
     ResultSet rs = st.executeQuery("SELECT 1")) {
    rs.next();
    System.out.println("OK " + rs.getInt(1));
} catch (Exception e) {
    System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
