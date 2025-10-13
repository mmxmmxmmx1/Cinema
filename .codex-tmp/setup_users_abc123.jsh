import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;
String url="jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user="";
String pass="";
try(Connection conn=DriverManager.getConnection(url,user,pass); Statement st=conn.createStatement()){
    st.executeUpdate("CREATE TABLE IF NOT EXISTS users ("+
                     "id BIGINT PRIMARY KEY AUTO_INCREMENT,"+
                     "username VARCHAR(50) NOT NULL UNIQUE,"+
                     "password_hash VARCHAR(100) NOT NULL,"+
                     "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"+
                     ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    st.executeUpdate("CREATE TABLE IF NOT EXISTS user_roles ("+
                     "user_id BIGINT NOT NULL,"+
                     "role_id BIGINT NOT NULL,"+
                     "PRIMARY KEY (user_id, role_id),"+
                     "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,"+
                     "FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE"+
                     ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    st.executeUpdate("INSERT IGNORE INTO roles(code,name) VALUES"+
                     "('ROLE_USER','ROLE_USER'),"+
                     "('ROLE_CUSTOMER','Customer')");
    String hash = "{bcrypt}" + BCrypt.hashpw("abc123", BCrypt.gensalt(10));
    PreparedStatement upsert = conn.prepareStatement(
            "INSERT INTO users(username,password_hash) VALUES(?,?) "+
            "ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash)");
    upsert.setString(1, "abc123");
    upsert.setString(2, hash);
    upsert.executeUpdate();
    ResultSet userRs = st.executeQuery("SELECT id FROM users WHERE username='abc123'");
    Long userId = null;
    if(userRs.next()){
        userId = userRs.getLong(1);
    }
    if(userId != null){
        PreparedStatement assign = conn.prepareStatement(
            "INSERT IGNORE INTO user_roles(user_id, role_id) "+
            "SELECT ?, id FROM roles WHERE code IN ('ROLE_USER','ROLE_CUSTOMER')");
        assign.setLong(1, userId);
        assign.executeUpdate();
    }
    System.out.println("DONE");
}
