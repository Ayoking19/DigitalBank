import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class VaultBuilder {
    public static void main(String[] args) {
        // The digital pathway to our SQLite database
        String url = "jdbc:sqlite:bank.db";
        
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            System.out.println("Initiating Master Vault Construction...");

            // 1. The Purge Phase 
            // [Drops any existing tables to guarantee a 100% clean slate]
            stmt.execute("DROP TABLE IF EXISTS Users;");
            stmt.execute("DROP TABLE IF EXISTS Transactions;");
            stmt.execute("DROP TABLE IF EXISTS Beneficiaries;");
            stmt.execute("DROP TABLE IF EXISTS Loans;");
            stmt.execute("DROP TABLE IF EXISTS Savings;");
            System.out.println("Clean slate verified.");

            // 2. Constructing the Users Table (The core identity and balance ledger)
            // [Includes all KYC data, FICO Scores, and Time-Based Transfer Limits]
            String createUsers = "CREATE TABLE Users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "google_id TEXT UNIQUE NOT NULL,"
                + "account_number TEXT UNIQUE NOT NULL,"
                + "pin TEXT NOT NULL,"
                + "balance REAL NOT NULL DEFAULT 0.0,"
                + "full_name TEXT,"
                + "dob TEXT,"
                + "email TEXT UNIQUE,"
                + "phone TEXT UNIQUE,"
                + "country TEXT,"
                + "address TEXT,"
                + "nok_name TEXT,"
                + "nok_dob TEXT,"
                + "nok_phone TEXT,"
                + "nok_address TEXT,"
                + "credit_score INTEGER DEFAULT 750,"
                + "transfer_limit REAL DEFAULT 10000.0,"
                + "daily_limit REAL DEFAULT 0.0,"
                + "weekly_limit REAL DEFAULT 0.0,"
                + "monthly_limit REAL DEFAULT 0.0,"
                + "pending_limit_type TEXT,"
                + "pending_limit_value REAL,"
                + "limit_unlock_time TEXT"
                + ");";
            stmt.execute(createUsers);
            System.out.println("1/5: Institutional Users table constructed.");

            // 3. Constructing the Transactions Table (The historical ledger)
            String createTx = "CREATE TABLE Transactions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sender_account TEXT,"
                + "receiver_account TEXT,"
                + "amount REAL,"
                + "description TEXT,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            stmt.execute(createTx);
            System.out.println("2/5: Transactions ledger constructed.");

            // 4. Constructing the Beneficiaries Table (The saved contacts book)
            String createBene = "CREATE TABLE Beneficiaries ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "owner_account TEXT,"
                + "bene_account TEXT,"
                + "bene_name TEXT"
                + ");";
            stmt.execute(createBene);
            System.out.println("3/5: Beneficiaries contact book constructed.");

            // 5. Constructing the Loans Table (The Credit Facility)
            String createLoans = "CREATE TABLE Loans ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "account_number TEXT,"
                + "principal REAL,"
                + "total_owed REAL,"
                + "amount_paid REAL DEFAULT 0.0,"
                + "repayment_type TEXT,"
                + "next_due_date TEXT,"
                + "status TEXT DEFAULT 'ACTIVE'," // Changes to 'CLOSED' when repaid
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            stmt.execute(createLoans);
            System.out.println("4/5: Credit Liability (Loans) table constructed.");

            // 6. Constructing the Savings Table (The Wealth Reserves)
            String createSavings = "CREATE TABLE Savings ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "account_number TEXT,"
                + "plan_name TEXT,"
                + "category TEXT,"           // DAILY, TARGET, LOCKED
                + "discipline TEXT,"         // STRICT, LENIENT
                + "current_balance REAL DEFAULT 0.0,"
                + "target_amount REAL DEFAULT 0.0,"
                + "daily_amount REAL DEFAULT 0.0,"
                + "exec_time TEXT,"
                + "start_date TEXT,"
                + "end_date TEXT,"
                + "withdrawal_lock_until DATETIME," // Manages the 12-hour cooling period
                + "status TEXT DEFAULT 'ACTIVE',"   // ACTIVE, COMPLETED
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            stmt.execute(createSavings);
            System.out.println("5/5: Wealth Reserves (Savings) table constructed.");

            System.out.println("\nMaster Architecture Complete: All 5 institutional tables have been built flawlessly.");

        } catch (Exception e) {
            System.out.println("Critical System Error: " + e.getMessage());
        }
    }
}