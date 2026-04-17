package stock.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SqlServerConnect {
	   public static Statement dbConnect(String db_connect_string,String db_userid,String db_password){
		   
	      try {
	         Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
	         Connection conn = DriverManager.getConnection(db_connect_string,db_userid, db_password);
	         System.out.println("connected");
	         Statement st = conn.createStatement();
	         return st;

	      } catch (Exception e) {
	         e.printStackTrace();
	         return null;
	      }
		  
	   }

	   public static void main(String[] args) throws SQLException{
		   
		   Statement st = dbConnect("jdbc:sqlserver://192.168.2.252:1433;databaseName=Notes", "sa", "wanguo");
	       st.executeUpdate("INSERT INTO StockHistroy " + "VALUES ('1111', '106/08/01', '20670924', '673271055', '31.00','33.25','30.80','33.25','6')");
	      
	   }
}
