import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;
String url = "jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user = "";
String pass = "";
String raw = "abc123";
String hash = "{bcrypt}" + BCrypt.hashpw(raw, BCrypt.gensalt(10));
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.setAutoCommit(false);
    try (Statement st = conn.createStatement()) {
        st.executeUpdate("INSERT IGNORE INTO roles(code, name) VALUES ('EMPLOYEE','EMPLOYEE'),('ADMIN','ADMIN'),('SUPERVISOR','SUPERVISOR')");
    }
    String upsert = "INSERT INTO members(first_name,last_name,email,phone,password) VALUES (?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE phone=VALUES(phone), password=VALUES(password)";
    try (PreparedStatement ps = conn.prepareStatement(upsert)) {
        ps.setString(1, "joe"); ps.setString(2, "doe"); ps.setString(3, "joe.luv2code.com"); ps.setString(4, "3345678"); ps.setString(5, hash); ps.executeUpdate();
        ps.setString(1, "mary"); ps.setString(2, "jon"); ps.setString(3, "mary.luv2code.com"); ps.setString(4, "0800092000"); ps.setString(5, hash); ps.executeUpdate();
        ps.setString(1, "susan"); ps.setString(2, "coco"); ps.setString(3, "susan.luv2code.com"); ps.setString(4, "N/A"); ps.setString(5, hash); ps.executeUpdate();
    }
    String link = "INSERT IGNORE INTO member_roles(member_id, role_id) " +
                  "SELECT m.id, r.id FROM members m JOIN roles r ON r.code=? WHERE m.email=?";
    try (PreparedStatement ps = conn.prepareStatement(link)) {
        ps.setString(1, "EMPLOYEE"); ps.setString(2, "joe.luv2code.com"); ps.executeUpdate();
        ps.setString(1, "ADMIN"); ps.setString(2, "mary.luv2code.com"); ps.executeUpdate();
        ps.setString(1, "SUPERVISOR"); ps.setString(2, "susan.luv2code.com"); ps.executeUpdate();
    }
    conn.commit();
    System.out.println("DONE");
}
