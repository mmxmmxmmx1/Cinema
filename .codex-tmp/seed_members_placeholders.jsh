import java.sql.*;
String url = "jdbc:mysql://localhost:3306/cinema";
String user = "";
String pass = "";
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement st = conn.createStatement()) {
    // Create three members with placeholder passwords we will update to bcrypt separately
    PreparedStatement ps = conn.prepareStatement("INSERT INTO members (first_name, last_name, email, phone, password) VALUES (?, ?, ?, ?, ?)");

    ps.setString(1, "joe");
    ps.setString(2, "doe");
    ps.setString(3, "joe.luv2code.com");
    ps.setString(4, "3345678");
    ps.setString(5, "abc123");
    try { ps.executeUpdate(); } catch (SQLException ex) {}

    ps.setString(1, "mary");
    ps.setString(2, "jon");
    ps.setString(3, "mary.luv2code.com");
    ps.setString(4, "0800092000");
    ps.setString(5, "abc123");
    try { ps.executeUpdate(); } catch (SQLException ex) {}

    ps.setString(1, "susan");
    ps.setString(2, "coco");
    ps.setString(3, "susan.luv2code.com");
    ps.setString(4, "N/A");
    ps.setString(5, "abc123");
    try { ps.executeUpdate(); } catch (SQLException ex) {}

    System.out.println("MEMBERS_INSERTED_PLACEHOLDER");
} catch (Exception e) {
    System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
