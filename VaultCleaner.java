public class VaultCleaner {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:bank.db";
        try {
            java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
            java.sql.Statement stmt = conn.createStatement();
            
            // Wiping both tables completely clean
            stmt.execute("DELETE FROM Users;");
            stmt.execute("DELETE FROM Transactions;");
            
            System.out.println("Vault completely bleached. Database is clean and ready.");
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}