import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
String raw = "abc123";
String hash = "{bcrypt}" + BCrypt.hashpw(raw, BCrypt.gensalt(10));
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    String upsert = "INSERT INTO members(first_name,last_name,email,phone,password) VALUES (?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE first_name=VALUES(first_name), last_name=VALUES(last_name), phone=VALUES(phone), password=VALUES(password)";
    try (PreparedStatement ps = conn.prepareStatement(upsert)) {
        ps.setString(1, "abc123");
        ps.setString(2, "abc123");
        ps.setString(3, "abc123@luv2code.com");
        ps.setString(4, "N/A");
        ps.setString(5, hash);
        int n = ps.executeUpdate();
        System.out.println("UPSERTED=" + n);
    }
    try (PreparedStatement ps = conn.prepareStatement("SELECT id, first_name, last_name, email, phone, password FROM members WHERE email=?")) {
        ps.setString(1, "abc123@luv2code.com");
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.println(rs.getLong(1)+","+rs.getString(2)+","+rs.getString(3)+","+rs.getString(4)+","+rs.getString(5)+","+rs.getString(6).substring(0,8)+"...");
            }
        }
    }
}
