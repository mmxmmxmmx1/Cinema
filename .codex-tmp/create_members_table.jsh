import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement st = conn.createStatement()) {
    String ddl = "CREATE TABLE IF NOT EXISTS members (" +
                 "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                 "first_name VARCHAR(50) NOT NULL, " +
                 "last_name VARCHAR(50) NOT NULL, " +
                 "email VARCHAR(255) NOT NULL UNIQUE, " +
                 "phone VARCHAR(30), " +
                 "password VARCHAR(255) NOT NULL)";
    st.executeUpdate(ddl);
    System.out.println("TABLE_CREATED_OR_EXISTS");
    try (ResultSet rs = st.executeQuery("DESCRIBE members")) {
        while (rs.next()) {
            System.out.println(
                rs.getString(1) + ":" + // Field
                rs.getString(2) + ":" + // Type
                rs.getString(3) + ":" + // Null
                rs.getString(4) + ":" + // Key
                rs.getString(5) + ":" + // Default
                rs.getString(6)        // Extra
            );
        }
    }
} catch (Exception e) {
    System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
