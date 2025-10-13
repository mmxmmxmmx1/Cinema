import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;
String url = "jdbc:mysql://localhost:3306/cinema";
String user = "";
String pass = "";
String raw = "abc123";
String hash = BCrypt.hashpw(raw, BCrypt.gensalt(10));
String stored = "{bcrypt}" + hash;
System.out.println("HASH=" + stored);
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    PreparedStatement ps = conn.prepareStatement("UPDATE members SET password=? WHERE email IN (?,?,?)");
    ps.setString(1, stored);
    ps.setString(2, "joe.luv2code.com");
    ps.setString(3, "mary.luv2code.com");
    ps.setString(4, "susan.luv2code.com");
    int updated = ps.executeUpdate();
    System.out.println("UPDATED=" + updated);
}
