import java.sql.*;
String url="jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
String user="";
String pass="";
try(Connection conn=DriverManager.getConnection(url,user,pass); Statement st=conn.createStatement()){
    ResultSet rs = st.executeQuery("SELECT id, username FROM users");
    while(rs.next()){
        System.out.println(rs.getLong(1)+"|"+rs.getString(2));
    }
    ResultSet rs2 = st.executeQuery("SELECT u.username, r.code FROM user_roles ur JOIN users u ON ur.user_id=u.id JOIN roles r ON ur.role_id=r.id");
    while(rs2.next()){
        System.out.println(rs2.getString(1)+" -> "+rs2.getString(2));
    }
}
