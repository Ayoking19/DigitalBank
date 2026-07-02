import java.io.InputStream;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;


public class BankServer {
    public static void main(String[] args) throws Exception {
        // 1. Setting up the desk
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // 2. Creating the Authentication Door
        server.createContext("/api/auth", new AuthHandler());
        
        // THE FIX: Creating the brand new Registration Door for our locked-in data
        server.createContext("/api/register", new RegisterHandler());
        
        // THE FIX: Creating the Dashboard Door so the frontend can read the vault ledger
        server.createContext("/api/user", new UserHandler());
        
        // THE FIX: Creating the new Transfer Door to catch the Wire Transfer payload
        server.createContext("/api/transfer", new TransferHandler());
        
        // THE FIX: Creating the Ledger Door so the frontend can pull the transaction history
        server.createContext("/api/transactions", new TransactionHandler());
        
        // THE FIX: Creating the Beneficiary Door to fetch saved contacts
        server.createContext("/api/beneficiaries", new BeneficiaryHandler());
        
        // THE FIX: Creating the Account Lookup Door to verify receivers
        server.createContext("/api/lookup", new LookupHandler());
        
        // THE FIX: Creating the Live State Manager for the Beneficiary Toggle Switch
        server.createContext("/api/manage-beneficiary", new ManageBeneficiaryHandler());
        
        // THE FIX: Creating the External Deposit Door for the Add Money Modal
        server.createContext("/api/add-money", new AddMoneyHandler());
        
        // THE FIX: The Wealth Reserves API Pipeline
        server.createContext("/api/get-savings", new GetSavingsHandler());
        server.createContext("/api/create-savings", new CreateSavingsHandler());
        server.createContext("/api/fund-savings", new FundSavingsHandler());
        server.createContext("/api/withdraw-savings", new WithdrawSavingsHandler());
        server.createContext("/api/cancel-withdrawal", new CancelWithdrawalHandler()); 
        server.createContext("/api/delete-savings", new DeleteSavingsHandler()); 
        
        // THE FIX: The Profile & Configuration Pipeline
        server.createContext("/api/full-profile", new FullProfileHandler());
        server.createContext("/api/update-profile", new UpdateProfileHandler());
        server.createContext("/api/settings/pin", new ChangePinHandler());
        server.createContext("/api/settings/account", new ChangeAccountHandler());
        server.createContext("/api/settings/limit", new ChangeLimitHandler());
        
        // THE FIX: The Credit Lifecycle API Pipeline
        server.createContext("/api/loan-status", new LoanStatusHandler());
        server.createContext("/api/take-loan", new TakeLoanHandler());
        server.createContext("/api/repay-loan", new RepayLoanHandler());
        
        // 3. Waking the guard up
        server.setExecutor(null);
        server.start();
        
        // THE FIX: The Bulletproof Database Auto-Upgrader. Individual try/catch blocks guarantee NO skipping!
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
            try { c.createStatement().execute("ALTER TABLE Users ADD COLUMN daily_limit REAL DEFAULT 0"); } catch(Exception ignore) {}
            try { c.createStatement().execute("ALTER TABLE Users ADD COLUMN weekly_limit REAL DEFAULT 0"); } catch(Exception ignore) {}
            try { c.createStatement().execute("ALTER TABLE Users ADD COLUMN monthly_limit REAL DEFAULT 0"); } catch(Exception ignore) {}
            try { c.createStatement().execute("ALTER TABLE Users ADD COLUMN limit_unlock_time TEXT"); } catch(Exception ignore) {}
        } catch(Exception e) {} 
        
        System.out.println("Security Guard is awake. Server listening on port 8080...");
        
        // THE FIX: The Automated Cron Engine. Now processes Daily Sweeps AND 24-Hour Transfer Limit Deletions!
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Wait exactly 1 minute
                    java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db");
                    
                    // 1. Check for expired Time-Locks and completely erase the Transfer Limits from the vault
                    try {
                        conn.createStatement().executeUpdate("UPDATE Users SET transfer_limit = 0, daily_limit = 0, weekly_limit = 0, monthly_limit = 0, limit_unlock_time = NULL WHERE limit_unlock_time IS NOT NULL AND limit_unlock_time <= datetime('now', 'localtime')");
                    } catch(Exception ignore) {}

                    // 2. Find active portfolios with AUTO funding
                    
                    // Find active portfolios with AUTO funding whose scheduled time matches the current HH:MM
                    String sweepSql = "SELECT id, account_number, plan_name, daily_amount FROM Savings WHERE status = 'ACTIVE' AND daily_amount > 0 AND exec_time = strftime('%H:%M', 'now', 'localtime')";
                    java.sql.ResultSet rs = conn.createStatement().executeQuery(sweepSql);
                    
                    while (rs.next()) {
                        int planId = rs.getInt("id");
                        String acc = rs.getString("account_number");
                        String name = rs.getString("plan_name");
                        double amt = rs.getDouble("daily_amount");
                        
                        // Check if the user has enough money in their main vault to cover the sweep
                        java.sql.PreparedStatement balStmt = conn.prepareStatement("SELECT balance FROM Users WHERE account_number = ?");
                        balStmt.setString(1, acc);
                        java.sql.ResultSet balRs = balStmt.executeQuery();
                        
                        if (balRs.next() && balRs.getDouble("balance") >= amt) {
                            // Execute the automated transfer
                            conn.prepareStatement("UPDATE Users SET balance = balance - " + amt + " WHERE account_number = '" + acc + "'").executeUpdate();
                            conn.prepareStatement("UPDATE Savings SET current_balance = current_balance + " + amt + " WHERE id = " + planId).executeUpdate();
                            
                            // Log the automated transaction
                            java.sql.PreparedStatement txStmt = conn.prepareStatement("INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES (?, ?, ?, 'Automated Daily Sweep', datetime('now'))");
                            txStmt.setString(1, acc);
                            txStmt.setString(2, name + " (Savings)");
                            txStmt.setDouble(3, amt);
                            txStmt.executeUpdate();
                        }
                    }
                    conn.close();
                } catch (Exception e) { /* Failsafe: Ignore errors and try again next minute */ }
            }
        }).start();
    }

    // 4. The strict rules for the Authentication Door
    // 4. The strict rules for the Authentication Door
    // 4. The strict rules for the Authentication Door
    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            // Opening the window for CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // 1. Read the incoming pneumatic tube package
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());
            
            try {
                // 2. Extract the token string from the JSON package
                String token = requestBody.split("\"token\":\"")[1].split("\"")[0];
                
                // 3. Crack open the JWT badge (Header.Payload.Signature)
                String[] tokenParts = token.split("\\.");
                
                // 4. Decode the middle part (The Payload containing user data)
                String payload = new String(java.util.Base64.getUrlDecoder().decode(tokenParts[1]));
                
                // 5. Extract the unique Google ID (Google calls this the "sub")
                String googleId = payload.split("\"sub\":\"")[1].split("\"")[0];
                
                // THE FIX: Connecting to the vault to see if this user already exists
                String url = "jdbc:sqlite:bank.db";
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
                String checkSql = "SELECT account_number FROM Users WHERE google_id = ?";
                java.sql.PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                checkStmt.setString(1, googleId);
                java.sql.ResultSet rs = checkStmt.executeQuery();
                
                // THE FIX: If rs.next() is true, they exist. If false, they are new.
                String status = rs.next() ? "existing_user" : "new_user";
                conn.close();
                
                // THE FIX: Packaging the status and ID together as a JSON response
                String jsonResponse = "{\"status\":\"" + status + "\", \"googleId\":\"" + googleId + "\"}";
                
                exchange.sendResponseHeaders(200, jsonResponse.length());
                OutputStream os = exchange.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
                
            } catch (Exception e) {
                // If the badge is unreadable
                String error = "Error decoding badge.";
                exchange.sendResponseHeaders(400, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The new Handler that catches the 3-piece package and saves it to the Vault
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            // Opening the window for CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Catching the delivery tube payload
            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());
            
            try {
                // THE FIX: Slicing the massive JSON payload to extract all 14 KYC variables
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String accountNum = requestBody.split("\"accountNumber\":\"")[1].split("\"")[0];
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                double initialDeposit = Double.parseDouble(requestBody.split("\"initialDeposit\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));
                
                String fullName = requestBody.split("\"fullName\":\"")[1].split("\"")[0];
                String dob = requestBody.split("\"dob\":\"")[1].split("\"")[0];
                String email = requestBody.split("\"email\":\"")[1].split("\"")[0];
                String phone = requestBody.split("\"phone\":\"")[1].split("\"")[0];
                String country = requestBody.split("\"country\":\"")[1].split("\"")[0];
                String address = requestBody.split("\"address\":\"")[1].split("\"")[0];
                
                String nokName = requestBody.split("\"nokName\":\"")[1].split("\"")[0];
                String nokDob = requestBody.split("\"nokDob\":\"")[1].split("\"")[0];
                String nokPhone = requestBody.split("\"nokPhone\":\"")[1].split("\"")[0];
                String nokAddress = requestBody.split("\"nokAddress\":\"")[1].split("\"")[0];
                
                // THE FIX: Failsafe connection wrapper and explicit Uniqueness Gates
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    
                    // Explicitly rejecting ONLY duplicate Emails, Phones, Google IDs, and Account Numbers!
                    java.sql.PreparedStatement checkEmail = conn.prepareStatement("SELECT id FROM Users WHERE email = ?");
                    checkEmail.setString(1, email);
                    if (checkEmail.executeQuery().next()) throw new Exception("This email address is already registered.");

                    java.sql.PreparedStatement checkPhone = conn.prepareStatement("SELECT id FROM Users WHERE phone = ?");
                    checkPhone.setString(1, phone);
                    if (checkPhone.executeQuery().next()) throw new Exception("This phone number is already registered.");

                    java.sql.PreparedStatement checkId = conn.prepareStatement("SELECT id FROM Users WHERE google_id = ?");
                    checkId.setString(1, googleId);
                    if (checkId.executeQuery().next()) throw new Exception("This Google Account is already linked to a vault.");

                    java.sql.PreparedStatement checkAccStmt = conn.prepareStatement("SELECT id FROM Users WHERE account_number = ?");
                    checkAccStmt.setString(1, accountNum);
                    if (checkAccStmt.executeQuery().next()) {
                        String error = "That account number is already in use. Please choose another.";
                        exchange.sendResponseHeaders(409, error.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(error.getBytes());
                        os.close();
                        return; // Stop execution
                    }
                    
                    // Upgrading the PreparedStatement to securely inject all 14 parameters
                    String sql = "INSERT INTO Users (google_id, account_number, pin, balance, full_name, dob, email, phone, country, address, nok_name, nok_dob, nok_phone, nok_address) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, googleId);
                    pstmt.setString(2, accountNum);
                    pstmt.setString(3, pin);
                    pstmt.setDouble(4, initialDeposit);
                    pstmt.setString(5, fullName);
                    pstmt.setString(6, dob);
                    pstmt.setString(7, email);
                    pstmt.setString(8, phone);
                    pstmt.setString(9, country);
                    pstmt.setString(10, address);
                    pstmt.setString(11, nokName);
                    pstmt.setString(12, nokDob);
                    pstmt.setString(13, nokPhone);
                    pstmt.setString(14, nokAddress);
                    pstmt.executeUpdate();
                    
                    String genesisSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, timestamp) VALUES ('SYSTEM', ?, ?, datetime('now'))";
                    java.sql.PreparedStatement genesisStmt = conn.prepareStatement(genesisSql);
                    genesisStmt.setString(1, accountNum);
                    genesisStmt.setDouble(2, initialDeposit);
                    genesisStmt.executeUpdate();
                } // Connection automatically closes!
                
                String response = "Account securely originated. Initial deposit received.";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                
            } catch (Exception e) {
                String error = "Database Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The new Handler that securely reads the user's balance and account number
    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());
            
            try {
                // Extracting the Google ID from the digital backpack payload
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                
                // Connecting to the Vault
                String url = "jdbc:sqlite:bank.db";
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
                
                // THE FIX: Expanding the query to pull the user's institutional Credit Score
                String sql = "SELECT account_number, balance, credit_score FROM Users WHERE google_id = ?";
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, googleId);
                
                // ResultSet acts as a magnifying glass looking directly at the specific row
                java.sql.ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    String accNum = rs.getString("account_number");
                    double balance = rs.getDouble("balance");
                    int creditScore = rs.getInt("credit_score");
                    
                    // Formatting the response back as a perfect JSON package
                    String jsonResponse = "{\"accountNumber\":\"" + accNum + "\", \"balance\":" + balance + ", \"creditScore\":" + creditScore + "}";
                    
                    exchange.sendResponseHeaders(200, jsonResponse.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(jsonResponse.getBytes());
                    os.close();
                } else {
                    // THE FIX: Ensuring the server doesn't freeze if the user is missing
                    String error = "User not found in vault.";
                    exchange.sendResponseHeaders(404, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                }
                
                conn.close();
                
            } catch (Exception e) {
                String error = "Database Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The core Wire Transfer Engine logic
    static class TransferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());
            
            try {
                // THE FIX: Upgrading the payload slicer to extract all 6 pieces of data
                String senderId = requestBody.split("\"senderId\":\"")[1].split("\"")[0];
                String receiverAcc = requestBody.split("\"receiverAcc\":\"")[1].split("\"")[0];
                double transferAmount = Double.parseDouble(requestBody.split("\"transferAmount\":\"")[1].split("\"")[0]);
                String senderPin = requestBody.split("\"senderPin\":\"")[1].split("\"")[0];
                
                // Extracting the new description and beneficiary toggle
                String description = requestBody.split("\"description\":\"")[1].split("\"")[0];
                boolean saveBeneficiary = requestBody.contains("\"saveBeneficiary\":true");
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                
                // THE FIX: Adding the Time-Based limit columns to the SELECT query so the ResultSet can actually find them!
                String verifySql = "SELECT account_number, balance, transfer_limit, daily_limit, weekly_limit, monthly_limit FROM Users WHERE google_id = ? AND pin = ?";
                java.sql.PreparedStatement verifyStmt = conn.prepareStatement(verifySql);
                verifyStmt.setString(1, senderId);
                verifyStmt.setString(2, senderPin);
                java.sql.ResultSet rsSender = verifyStmt.executeQuery();
                
                if (!rsSender.next()) {
                    String error = "Transfer Failed: Invalid PIN.";
                    exchange.sendResponseHeaders(401, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                    conn.close();
                    return;
                }
                
                double currentBalance = rsSender.getDouble("balance");
                String senderAccNum = rsSender.getString("account_number");
                
                // THE FIX: The Advanced Time-Based Limit Matrix!
                double perTransferLimit = rsSender.getDouble("transfer_limit");
                double dailyLimit = rsSender.getDouble("daily_limit");
                double weeklyLimit = rsSender.getDouble("weekly_limit");
                double monthlyLimit = rsSender.getDouble("monthly_limit");
                
                if (perTransferLimit > 0 && transferAmount > perTransferLimit) throw new Exception("Exceeds Per-Transfer limit of $" + String.format("%.2f", perTransferLimit));

                // Mathematical Time Aggregation queries
                java.sql.PreparedStatement dailyStmt = conn.prepareStatement("SELECT SUM(amount) AS spent FROM Transactions WHERE sender_account = ? AND timestamp >= datetime('now', '-1 day')");
                dailyStmt.setString(1, senderAccNum);
                java.sql.ResultSet rsDaily = dailyStmt.executeQuery();
                if (dailyLimit > 0 && ((rsDaily.next() ? rsDaily.getDouble("spent") : 0) + transferAmount > dailyLimit)) throw new Exception("Transfer blocked: Daily limit exceeded.");

                java.sql.PreparedStatement weeklyStmt = conn.prepareStatement("SELECT SUM(amount) AS spent FROM Transactions WHERE sender_account = ? AND timestamp >= datetime('now', '-7 days')");
                weeklyStmt.setString(1, senderAccNum);
                java.sql.ResultSet rsWeekly = weeklyStmt.executeQuery();
                if (weeklyLimit > 0 && ((rsWeekly.next() ? rsWeekly.getDouble("spent") : 0) + transferAmount > weeklyLimit)) throw new Exception("Transfer blocked: Weekly limit exceeded.");

                java.sql.PreparedStatement monthlyStmt = conn.prepareStatement("SELECT SUM(amount) AS spent FROM Transactions WHERE sender_account = ? AND timestamp >= datetime('now', '-1 month')");
                monthlyStmt.setString(1, senderAccNum);
                java.sql.ResultSet rsMonthly = monthlyStmt.executeQuery();
                if (monthlyLimit > 0 && ((rsMonthly.next() ? rsMonthly.getDouble("spent") : 0) + transferAmount > monthlyLimit)) throw new Exception("Transfer blocked: Monthly limit exceeded.");
                
                // THE FIX: The Circular Transfer Block. Halting the engine if the accounts match perfectly.
                if (senderAccNum.equals(receiverAcc)) {
                    String error = "Transfer Failed: You cannot wire funds to your own account.";
                    exchange.sendResponseHeaders(400, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                    conn.close();
                    return;
                }
                
                if (currentBalance < transferAmount) {
                    String error = "Transfer Failed: Insufficient funds.";
                    exchange.sendResponseHeaders(400, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                    conn.close();
                    return;
                }
                
                // 3. Verifying the receiver actually exists
                String checkReceiverSql = "SELECT id FROM Users WHERE account_number = ?";
                java.sql.PreparedStatement receiverStmt = conn.prepareStatement(checkReceiverSql);
                receiverStmt.setString(1, receiverAcc);
                if (!receiverStmt.executeQuery().next()) {
                    String error = "Transfer Failed: Receiver account does not exist.";
                    exchange.sendResponseHeaders(404, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                    conn.close();
                    return;
                }
                
                // 4. Executing the mathematical transfer
                conn.setAutoCommit(false); // Locking the vault so we can do multiple math operations safely
                
                // Deduct from sender
                String deductSql = "UPDATE Users SET balance = balance - ? WHERE account_number = ?";
                java.sql.PreparedStatement deductStmt = conn.prepareStatement(deductSql);
                deductStmt.setDouble(1, transferAmount);
                deductStmt.setString(2, senderAccNum);
                deductStmt.executeUpdate();
                
                // Add to receiver
                String addSql = "UPDATE Users SET balance = balance + ? WHERE account_number = ?";
                java.sql.PreparedStatement addStmt = conn.prepareStatement(addSql);
                addStmt.setDouble(1, transferAmount);
                addStmt.setString(2, receiverAcc);
                addStmt.executeUpdate();
                
                // THE FIX: Upgrading the transaction record to physically save the Description
                String recordSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES (?, ?, ?, ?, datetime('now'))";
                // THE FIX: RETURN_GENERATED_KEYS tells JDBC to capture the new row's auto-incremented
                // ID the instant this INSERT executes, before anything else can touch the connection.
                java.sql.PreparedStatement recordStmt = conn.prepareStatement(recordSql, java.sql.Statement.RETURN_GENERATED_KEYS);
                recordStmt.setString(1, senderAccNum);
                recordStmt.setString(2, receiverAcc);
                recordStmt.setDouble(3, transferAmount);
                recordStmt.setString(4, description);
                recordStmt.executeUpdate();

                // THE FIX: Capturing the real Transaction ID HERE, immediately after the Transactions
                // INSERT and BEFORE any Beneficiary INSERT. If we waited until after the Beneficiary
                // INSERT, last_insert_rowid() would return the Beneficiary's ID instead.
                java.sql.ResultSet generatedKeys = recordStmt.getGeneratedKeys();
                int realId = generatedKeys.next() ? generatedKeys.getInt(1) : 1;
                String formattedId = String.format("TRX-%010d", realId);

                // THE FIX: If the user toggled the switch, save the receiver's details to the Beneficiaries table
                if (saveBeneficiary) {
                    // First, we must fetch the receiver's actual name to save it
                    String getReceiverNameSql = "SELECT full_name FROM Users WHERE account_number = ?";
                    java.sql.PreparedStatement nameStmt = conn.prepareStatement(getReceiverNameSql);
                    nameStmt.setString(1, receiverAcc);
                    java.sql.ResultSet nameRs = nameStmt.executeQuery();
                    
                    if (nameRs.next()) {
                        String actualReceiverName = nameRs.getString("full_name");
                        
                        String beneSql = "INSERT INTO Beneficiaries (owner_account, bene_account, bene_name) VALUES (?, ?, ?)";
                        java.sql.PreparedStatement beneStmt = conn.prepareStatement(beneSql);
                        beneStmt.setString(1, senderAccNum);
                        beneStmt.setString(2, receiverAcc);
                        beneStmt.setString(3, actualReceiverName);
                        beneStmt.executeUpdate();
                    }
                }
                
                conn.commit(); // Approving the final math and unlocking the vault
                // THE FIX: Deleted the conn.close() that used to be here. The try-with-resources
                // block that wraps this entire handler automatically closes the connection when it
                // reaches its closing brace below. Manually calling conn.close() early and then
                // trying to use the connection again is what caused the "database connection closed"
                // crash and the disappearing receipt popup.

                String success = "{\"status\":\"Success\", \"id\":\"" + formattedId + "\"}";
                exchange.sendResponseHeaders(200, success.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(success.getBytes());
                os.close();
                } // THE FIX: Closes the try-with-resources for TransferHandler
                
            } catch (Exception e) {
                String error = "System Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The new Handler that retrieves a user's transaction history from the Vault
    static class TransactionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            // Opening the window for CORS [Cross-Origin Resource Sharing: A mandatory security protocol that explicitly grants a web browser permission to load data from a backend server operating on a different port]
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());

            try {
                // Extracting the Google ID from the incoming frontend package
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];

                String url = "jdbc:sqlite:bank.db";
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url);

                // Step 1: Find the user's actual account number using their Google ID
                String userSql = "SELECT account_number FROM Users WHERE google_id = ?";
                java.sql.PreparedStatement userStmt = conn.prepareStatement(userSql);
                userStmt.setString(1, googleId);
                java.sql.ResultSet userRs = userStmt.executeQuery();

                if (!userRs.next()) {
                    String error = "User not found.";
                    exchange.sendResponseHeaders(404, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                    conn.close();
                    return;
                }
                
                String accountNum = userRs.getString("account_number");

                // THE FIX: The Name-Resolution Subquery. It automatically converts Account Numbers into User Names if a match exists!
                String txSql = "SELECT t.id, t.sender_account, t.receiver_account, t.amount, t.description, t.timestamp, " +
                               "COALESCE((SELECT full_name FROM Users WHERE account_number = t.sender_account), t.sender_account) AS sender_name, " +
                               "COALESCE((SELECT full_name FROM Users WHERE account_number = t.receiver_account), t.receiver_account) AS receiver_name " +
                               "FROM Transactions t WHERE t.sender_account = ? OR t.receiver_account = ? ORDER BY t.timestamp DESC";
                
                java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                txStmt.setString(1, accountNum);
                txStmt.setString(2, accountNum);
                java.sql.ResultSet txRs = txStmt.executeQuery();

                StringBuilder jsonArray = new StringBuilder("[");
                while (txRs.next()) {
                    if (jsonArray.length() > 1) jsonArray.append(","); 
                    
                    int id = txRs.getInt("id");
                    String formattedId = String.format("%010d", id); 
                    
                    // Passing BOTH the raw account number (for mathematical logic) AND the name (for UI display)
                    String senderAcc = txRs.getString("sender_account");
                    String receiverAcc = txRs.getString("receiver_account");
                    String senderName = txRs.getString("sender_name");
                    String receiverName = txRs.getString("receiver_name");
                    
                    double amount = txRs.getDouble("amount");
                    String desc = txRs.getString("description");
                    if (desc == null || desc.trim().isEmpty()) desc = "Funds Transfer";
                    
                    jsonArray.append("{\"id\":\"TRX-").append(formattedId)
                             .append("\",\"senderAcc\":\"").append(senderAcc)
                             .append("\",\"receiverAcc\":\"").append(receiverAcc)
                             .append("\",\"sender\":\"").append(senderName)
                             .append("\",\"receiver\":\"").append(receiverName)
                             .append("\",\"amount\":").append(amount)
                             .append(",\"description\":\"").append(desc)
                             .append("\",\"timestamp\":\"").append(txRs.getString("timestamp"))
                             .append("\"}");
                }
                jsonArray.append("]");

                // Step 4: Shooting the compiled history back to the frontend
                exchange.sendResponseHeaders(200, jsonArray.toString().length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(jsonArray.toString().getBytes());
                os.close();

                conn.close();

            } catch (Exception e) {
                String error = "System Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The new Handler that securely retrieves a user's saved contacts
    static class BeneficiaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            // Opening the window for CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());

            try {
                // Extracting the Google ID from the payload
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];

                String url = "jdbc:sqlite:bank.db";
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url);

                // Step 1: Find the user's actual account number to establish ownership
                String userSql = "SELECT account_number FROM Users WHERE google_id = ?";
                java.sql.PreparedStatement userStmt = conn.prepareStatement(userSql);
                userStmt.setString(1, googleId);
                java.sql.ResultSet userRs = userStmt.executeQuery();

                if (!userRs.next()) {
                    String error = "User not found.";
                    exchange.sendResponseHeaders(404, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                    conn.close();
                    return;
                }
                
                String ownerAcc = userRs.getString("account_number");

                // THE FIX: The Aggressive GROUP BY query forces the DB to merge Ghost users by Name and only pick their newest Account Number!
                String beneSql = "SELECT bene_name, MAX(bene_account) as bene_account FROM Beneficiaries WHERE owner_account = ? GROUP BY bene_name ORDER BY MAX(id) DESC";
                java.sql.PreparedStatement beneStmt = conn.prepareStatement(beneSql);
                beneStmt.setString(1, ownerAcc);
                java.sql.ResultSet beneRs = beneStmt.executeQuery();

                // Step 3: Construct the JSON Array payload
                StringBuilder jsonArray = new StringBuilder("[");
                while (beneRs.next()) {
                    if (jsonArray.length() > 1) {
                        jsonArray.append(","); 
                    }
                    String bName = beneRs.getString("bene_name");
                    String bAcc = beneRs.getString("bene_account");
                    
                    jsonArray.append("{\"name\":\"").append(bName)
                             .append("\",\"account\":\"").append(bAcc)
                             .append("\"}");
                }
                jsonArray.append("]");

                // Step 4: Transmit the data back to the frontend
                exchange.sendResponseHeaders(200, jsonArray.toString().length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(jsonArray.toString().getBytes());
                os.close();

                conn.close();

            } catch (Exception e) {
                String error = "System Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The new Handler that securely looks up an account name before a transfer
    static class LookupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());

            try {
                // Slicing out the 10-digit number sent from the search bar
                String lookupAcc = requestBody.split("\"account\":\"")[1].split("\"")[0];
                
                String url = "jdbc:sqlite:bank.db";
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url);

                // Scanning the Users table for a direct match
                String sql = "SELECT full_name FROM Users WHERE account_number = ?";
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, lookupAcc);
                java.sql.ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String fullName = rs.getString("full_name");
                    // Package the real name and shoot it back to the frontend
                    String jsonResponse = "{\"name\":\"" + fullName + "\", \"account\":\"" + lookupAcc + "\"}";
                    exchange.sendResponseHeaders(200, jsonResponse.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(jsonResponse.getBytes());
                    os.close();
                } else {
                    // Send a 404 Error if the account does not exist anywhere in the database
                    String error = "Account not found.";
                    exchange.sendResponseHeaders(404, error.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes());
                    os.close();
                }
                conn.close();
            } catch (Exception e) {
                String error = "System Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: The Live State Manager. Instantly adds or deletes contacts when the toggle is flipped.
    static class ManageBeneficiaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            java.io.InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes());

            try {
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String beneAcc = requestBody.split("\"beneAccount\":\"")[1].split("\"")[0];
                String beneName = requestBody.split("\"beneName\":\"")[1].split("\"")[0];
                String action = requestBody.split("\"action\":\"")[1].split("\"")[0]; // Reads "add" or "remove"

                String url = "jdbc:sqlite:bank.db";
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url);

                String userSql = "SELECT account_number FROM Users WHERE google_id = ?";
                java.sql.PreparedStatement userStmt = conn.prepareStatement(userSql);
                userStmt.setString(1, googleId);
                java.sql.ResultSet userRs = userStmt.executeQuery();

                if (userRs.next()) {
                    String ownerAcc = userRs.getString("account_number");

                    if (action.equals("add")) {
                        // Anti-Duplication Check before inserting
                        String checkSql = "SELECT id FROM Beneficiaries WHERE owner_account = ? AND bene_account = ?";
                        java.sql.PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                        checkStmt.setString(1, ownerAcc);
                        checkStmt.setString(2, beneAcc);
                        if (!checkStmt.executeQuery().next()) {
                            String insertSql = "INSERT INTO Beneficiaries (owner_account, bene_account, bene_name) VALUES (?, ?, ?)";
                            java.sql.PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                            insertStmt.setString(1, ownerAcc);
                            insertStmt.setString(2, beneAcc);
                            insertStmt.setString(3, beneName);
                            insertStmt.executeUpdate();
                        }
                    } else if (action.equals("remove")) {
                        // The Deletion Engine
                        String deleteSql = "DELETE FROM Beneficiaries WHERE owner_account = ? AND bene_account = ?";
                        java.sql.PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                        deleteStmt.setString(1, ownerAcc);
                        deleteStmt.setString(2, beneAcc);
                        deleteStmt.executeUpdate();
                    }
                }

                String response = "State Updated";
                exchange.sendResponseHeaders(200, response.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                conn.close();
            } catch (Exception e) {
                String error = "System Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    // THE FIX: Checks if the user currently owes the bank money
    static class LoanStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Browser Security Pass
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            try {
                String account = requestBody.split("\"account\":\"")[1].split("\"")[0];
                java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db");
                
                // THE FIX: Pulling the amount_paid and timestamp to power the State Derivation math on the frontend
                String sql = "SELECT total_owed, amount_paid, next_due_date, repayment_type, timestamp FROM Loans WHERE account_number = ? AND status = 'ACTIVE'";
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, account);
                java.sql.ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    double amountPaid = rs.getDouble("amount_paid");
                    double remaining = rs.getDouble("total_owed") - amountPaid;
                    String due = rs.getString("next_due_date");
                    String repType = rs.getString("repayment_type");
                    String timestamp = rs.getString("timestamp");
                    
                    // THE FIX: Safely extracting the installment amount WITHOUT shrinking it, so date math stays perfect
                    double instAmt = remaining; // Fallback
                    if (repType.contains("|")) {
                        instAmt = Double.parseDouble(repType.split("\\|")[1]);
                    }
                    
                    int clearedInstallments = (instAmt > 0) ? (int) Math.floor(amountPaid / instAmt) : 0;
                    double paidTowardCurrent = amountPaid - (clearedInstallments * instAmt);
                    double dueNow = instAmt - paidTowardCurrent;
                    if (dueNow > remaining) dueNow = remaining;
                    if (dueNow < 0) dueNow = 0;

                    // THE FIX: Packaging the raw tracking data + the new dueNow variable
                    String res = "{\"hasLoan\":true, \"remainingOwed\":" + remaining + ", \"amountPaid\":" + amountPaid + ", \"dueDate\":\"" + due + "\", \"installmentAmount\":" + instAmt + ", \"dueNow\":" + dueNow + ", \"timestamp\":\"" + timestamp + "\"}";
                    exchange.sendResponseHeaders(200, res.length());
                    exchange.getResponseBody().write(res.getBytes());
                } else {
                    String res = "{\"hasLoan\":false}";
                    exchange.sendResponseHeaders(200, res.length());
                    exchange.getResponseBody().write(res.getBytes());
                }
                exchange.getResponseBody().close();
                conn.close();
            } catch (Exception e) { exchange.sendResponseHeaders(500, 0); exchange.getResponseBody().close(); }
        }
    }

    // THE FIX: Issues the capital and signs the contract
    static class TakeLoanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Browser Security Pass
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            try {
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String account = requestBody.split("\"account\":\"")[1].split("\"")[0];
                
                // THE FIX: Reverted to the universally safe string-splitting method used by our other successful modules
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                
                // Safely extracting the mathematical numbers even if the frontend JSON formatting shifts slightly
                String principalStr = requestBody.split("\"principal\":")[1].split(",")[0].replaceAll("[^\\d.]", "");
                double principal = Double.parseDouble(principalStr);
                
                String totalOwedStr = requestBody.split("\"totalOwed\":")[1].split(",")[0].replaceAll("[^\\d.]", "");
                double totalOwed = Double.parseDouble(totalOwedStr);
                
                String type = requestBody.split("\"type\":\"")[1].split("\"")[0];
                
                // THE FIX: Extracting the new installment amount and hiding it inside the 'type' column
                String instStr = requestBody.split("\"installmentAmt\":")[1].split(",")[0].replaceAll("[^\\d.]", "");
                String enhancedType = type + "|" + instStr;
                
                String dueDate = requestBody.split("\"dueDate\":\"")[1].split("\"")[0];

                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                
                // Verify PIN
                String authSql = "SELECT id FROM Users WHERE google_id = ? AND pin = ?";
                java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                authStmt.setString(1, googleId); authStmt.setString(2, pin);
                if (!authStmt.executeQuery().next()) {
                    exchange.sendResponseHeaders(401, 0); exchange.getResponseBody().close(); conn.close(); return;
                }

                conn.setAutoCommit(false);

                // 1. Inject Capital into User's Balance
                String addFunds = "UPDATE Users SET balance = balance + ? WHERE account_number = ?";
                java.sql.PreparedStatement addStmt = conn.prepareStatement(addFunds);
                addStmt.setDouble(1, principal); addStmt.setString(2, account);
                addStmt.executeUpdate();

                // 2. Create the Loan Liability
                String loanSql = "INSERT INTO Loans (account_number, principal, total_owed, repayment_type, next_due_date) VALUES (?, ?, ?, ?, ?)";
                java.sql.PreparedStatement loanStmt = conn.prepareStatement(loanSql);
                loanStmt.setString(1, account); loanStmt.setDouble(2, principal); 
                loanStmt.setDouble(3, totalOwed); loanStmt.setString(4, enhancedType); loanStmt.setString(5, dueDate);
                loanStmt.executeUpdate();

                // 3. Record the Transaction
                String txSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES ('SYSTEM', ?, ?, 'Institutional Credit Disbursement', datetime('now'))";
                java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                txStmt.setString(1, account); txStmt.setDouble(2, principal);
                txStmt.executeUpdate();

                // THE FIX: Deduct 15 points for opening a new liability. This mirrors the
                // real FICO "hard inquiry dip" — taking on new debt is a risk signal.
                // adjustCreditScore is our shared helper defined at the bottom of this class.
                adjustCreditScore(conn, account, -15);

                conn.commit(); conn.close();
                String ok = "Loan Executed Successfully";
                exchange.sendResponseHeaders(200, ok.length()); 
                exchange.getResponseBody().write(ok.getBytes());
                exchange.getResponseBody().close();
                } // THE FIX: Closes the try-with-resources for TakeLoanHandler
            } catch (Exception e) {
                // THE FIX: Forcing Java to transmit its internal crash report over the network
                String err = "Vault Exception: " + e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); 
                exchange.getResponseBody().write(err.getBytes());
                exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: Deducts user balance to clear active liability
    static class RepayLoanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Browser Security Pass
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            try {
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String account = requestBody.split("\"account\":\"")[1].split("\"")[0];
                
                // THE FIX: Standardized, secure extraction for the Repayment Engine
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                
                String amountStr = requestBody.split("\"amount\":")[1].split(",")[0].replaceAll("[^\\d.]", "");
                double amount = Double.parseDouble(amountStr);

                java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db");
                
                // Verify PIN & Balance
                String authSql = "SELECT balance FROM Users WHERE google_id = ? AND pin = ?";
                java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                authStmt.setString(1, googleId); authStmt.setString(2, pin);
                java.sql.ResultSet rsAuth = authStmt.executeQuery();
                if (!rsAuth.next()) {
                    String err = "Invalid PIN."; exchange.sendResponseHeaders(401, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); conn.close(); return;
                }
                if (rsAuth.getDouble("balance") < amount) {
                    String err = "Insufficient Vault Funds."; exchange.sendResponseHeaders(400, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); conn.close(); return;
                }

                // THE FIX: The Overpayment Interceptor & Pure Epoch Date Math
                String loanCheckSql = "SELECT total_owed, amount_paid, repayment_type, next_due_date, timestamp FROM Loans WHERE account_number = ? AND status = 'ACTIVE'";
                java.sql.PreparedStatement loanCheckStmt = conn.prepareStatement(loanCheckSql);
                loanCheckStmt.setString(1, account);
                java.sql.ResultSet rsLoan = loanCheckStmt.executeQuery();
                
                if (!rsLoan.next()) {
                    String err = "No active liability found."; exchange.sendResponseHeaders(404, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); conn.close(); return;
                }
                
                // Round remaining to 2 decimal places to avoid floating point precision issues
                    double remaining = Math.round((rsLoan.getDouble("total_owed") - rsLoan.getDouble("amount_paid")) * 100.0) / 100.0;
                    // THE FIX: Added a 0.01 mathematical tolerance to prevent floating-point decimals from blocking valid payments!
                    if (amount > remaining + 0.01) {
                        String err = "Overpayment Blocked: You only owe $" + String.format("%.2f", remaining) + ". Please adjust your repayment amount.";
                        exchange.sendResponseHeaders(400, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); conn.close(); return;
                    }

                    // THE FIX: Calculating the exact due date based ONLY on the number of fully cleared installments. This makes the "2302 Time Travel" bug mathematically impossible.
                    String repType = rsLoan.getString("repayment_type");
                    String timestamp = rsLoan.getString("timestamp");
                    // THE FIX: Capture originalDueDate NOW before the installment block
                    // below overwrites currentDue with the NEXT month's date. We need the
                    // ORIGINAL due date later to determine if this payment was on time or late.
                    String originalDueDate = rsLoan.getString("next_due_date");
                    String currentDue = originalDueDate;
                    double newAmountPaid = rsLoan.getDouble("amount_paid") + amount;

                    // THE FIX: Lowercase "installments" and correct index [1] to match the database!
                    if (repType.startsWith("installments")) {
                        try {
                            String[] typeParts = repType.split("\\|");
                            double instAmt = Double.parseDouble(typeParts[1]);
                            
                            // THE FIX: Added 0.01 to safely calculate divisions without microscopic rounding drops.
                            int clearedInstallments = (instAmt > 0) ? (int) Math.floor((newAmountPaid + 0.01) / instAmt) : 0;
                            
                            String baseDateStr = timestamp.split(" ")[0];
                            java.time.LocalDate baseDate = java.time.LocalDate.parse(baseDateStr);
                            
                            // THE FIX: Switched to plusDays(30) to exactly mirror the 30-day multiplication loop used in your HTML frontend!
                            currentDue = baseDate.plusDays((clearedInstallments + 1) * 30).toString();
                        } catch (Exception e) {} 
                    }

                    // THE FIX: Automatically switch the loan status to 'CLOSED' if the remaining debt drops to zero (or 0.01 due to decimals).
                    String newStatus = (remaining - amount <= 0.01) ? "CLOSED" : "ACTIVE";

                    conn.setAutoCommit(false);

                    // 1. Deduct from User Balance
                    String deductSql = "UPDATE Users SET balance = balance - ? WHERE account_number = ?";
                    java.sql.PreparedStatement deductStmt = conn.prepareStatement(deductSql);
                    deductStmt.setDouble(1, amount); deductStmt.setString(2, account);
                    deductStmt.executeUpdate();

                    // 2. Update the Loan Status & Safe Due Date
                    String updateLoan = "UPDATE Loans SET amount_paid = amount_paid + ?, next_due_date = ?, status = ? WHERE account_number = ? AND status = 'ACTIVE'";
                    java.sql.PreparedStatement updateStmt = conn.prepareStatement(updateLoan);
                    updateStmt.setDouble(1, amount); updateStmt.setString(2, currentDue); updateStmt.setString(3, newStatus); updateStmt.setString(4, account);
                    updateStmt.executeUpdate();

                // 3. Check if fully repaid and close it
                String checkClose = "UPDATE Loans SET status = 'CLOSED' WHERE account_number = ? AND amount_paid >= total_owed AND status = 'ACTIVE'";
                java.sql.PreparedStatement closeStmt = conn.prepareStatement(checkClose);
                closeStmt.setString(1, account); closeStmt.executeUpdate();

                // 4. Record Transaction
                String txSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES (?, 'SYSTEM', ?, 'Credit Facility Repayment', datetime('now'))";
                java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                txStmt.setString(1, account); txStmt.setDouble(2, amount);
                txStmt.executeUpdate();

                // THE FIX: Credit Score Engine. Compares today's date against the
                // originalDueDate we captured before the installment block overwrote it.
                // java.time.LocalDate.parse() [converts a "YYYY-MM-DD" string into a
                // proper date object that supports comparisons like isAfter()] lets us
                // determine mathematically whether this payment was on time or late.
                try {
                    java.time.LocalDate dueOn = java.time.LocalDate.parse(originalDueDate);
                    java.time.LocalDate today = java.time.LocalDate.now();
                    if (!today.isAfter(dueOn)) {
                        // THE FIX: Paid on or before the due date — reward responsible behaviour
                        adjustCreditScore(conn, account, 10);
                    } else {
                        // THE FIX: Paid after the due date — late payments are the biggest
                        // score killer in real FICO, so the penalty is steeper than the reward
                        adjustCreditScore(conn, account, -20);
                    }
                    // THE FIX: Bonus for fully closing the loan. Completing a debt
                    // obligation is a separate, major positive event in FICO scoring.
                    if (newStatus.equals("CLOSED")) {
                        adjustCreditScore(conn, account, 25);
                    }
                } catch (Exception ignore) {}

                conn.commit(); conn.close();
                String ok = "Repayment Successful.";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                // THE FIX: Forcing Java to transmit its internal crash report
                String err = "Vault Exception: " + e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); 
                exchange.getResponseBody().write(err.getBytes());
                exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: The Add Money Engine that securely verifies the PIN and injects external capital
    static class AddMoneyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                
                // Extracting the payload
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                
                // Advanced extraction to handle the amount whether it was sent as a String or a raw Number
                String amountStr = requestBody.split("\"amount\":")[1].split(",")[0].replaceAll("[\"}]", "").trim();
                double depositAmount = Double.parseDouble(amountStr);

                java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db");

                // 1. Verify the PIN is correct
                String authSql = "SELECT account_number FROM Users WHERE google_id = ? AND pin = ?";
                java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                authStmt.setString(1, googleId);
                authStmt.setString(2, pin);
                java.sql.ResultSet rs = authStmt.executeQuery();

                if (!rs.next()) {
                    String err = "Invalid Security PIN.";
                    exchange.sendResponseHeaders(401, err.length());
                    exchange.getResponseBody().write(err.getBytes());
                    exchange.getResponseBody().close();
                    conn.close();
                    return;
                }
                
                String accountNum = rs.getString("account_number");
                conn.setAutoCommit(false);

                // 2. Inject the Capital
                String updateSql = "UPDATE Users SET balance = balance + ? WHERE account_number = ?";
                java.sql.PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setDouble(1, depositAmount);
                updateStmt.setString(2, accountNum);
                updateStmt.executeUpdate();

                // 3. Record the Transaction in the Ledger
                // THE FIX: Professional institutional naming convention
                String txSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES ('Approved Funding Source', ?, ?, 'Vault Deposit', datetime('now'))";
                java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                txStmt.setString(1, accountNum);
                txStmt.setDouble(2, depositAmount);
                txStmt.executeUpdate();

                conn.commit();
                conn.close();

                String ok = "Deposit Successful";
                exchange.sendResponseHeaders(200, ok.length());
                exchange.getResponseBody().write(ok.getBytes());
                exchange.getResponseBody().close();
                
            } catch (Exception e) {
                String err = "System Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, err.length());
                exchange.getResponseBody().write(err.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }

    // THE FIX: Retrieves active savings portfolios to render on the dashboard
    static class GetSavingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String account = requestBody.split("\"account\":\"")[1].split("\"")[0];
                
                java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db");
                
                // THE FIX: Pulling the target end date, the exact seconds alive for Interest Calculation, and the 12-hour lock
                String sql = "SELECT *, CAST(strftime('%s', withdrawal_lock_until) - strftime('%s', 'now') AS INTEGER) AS time_left, " +
                             "CAST(strftime('%s', 'now') - strftime('%s', timestamp) AS INTEGER) AS seconds_alive " +
                             "FROM Savings WHERE account_number = ? ORDER BY timestamp DESC";
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, account);
                java.sql.ResultSet rs = stmt.executeQuery();

                // Building the JSON Array for the frontend
                StringBuilder json = new StringBuilder("[");
                while (rs.next()) {
                    if (json.length() > 1) json.append(",");
                    
                    int timeLeft = rs.getInt("time_left");
                    if (rs.wasNull()) timeLeft = 0;
                    
                    String endDate = rs.getString("end_date");
                    if (endDate == null) endDate = "";

                    // THE FIX: State Derivation Yield Engine. 5% Annual Percentage Yield (APY) calculated down to the precise second!
                    int secondsAlive = rs.getInt("seconds_alive");
                    double baseBalance = rs.getDouble("current_balance");
                    double interestEarned = baseBalance * 0.05 * (secondsAlive / 31536000.0);

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"name\":\"").append(rs.getString("plan_name")).append("\",")
                        .append("\"category\":\"").append(rs.getString("category")).append("\",")
                        .append("\"discipline\":\"").append(rs.getString("discipline")).append("\",")
                        .append("\"balance\":").append(baseBalance).append(",")
                        .append("\"interest\":").append(interestEarned).append(",")
                        .append("\"target\":").append(rs.getDouble("target_amount")).append(",")
                        .append("\"endDate\":\"").append(endDate).append("\",")
                        .append("\"timeLeft\":").append(timeLeft).append(",")
                        .append("\"status\":\"").append(rs.getString("status")).append("\"")
                        .append("}");
                }
                json.append("]");
                conn.close();
                
                exchange.sendResponseHeaders(200, json.toString().length());
                exchange.getResponseBody().write(json.toString().getBytes());
                exchange.getResponseBody().close();
            } catch (Exception e) { exchange.sendResponseHeaders(500, 0); exchange.getResponseBody().close(); }
        }
    }

    // THE FIX: Securely builds the savings contract and transfers any initial locked funds
    static class CreateSavingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String account = requestBody.split("\"account\":\"")[1].split("\"")[0];
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                
                String name = requestBody.split("\"planName\":\"")[1].split("\"")[0];
                String category = requestBody.split("\"category\":\"")[1].split("\"")[0];
                String discipline = requestBody.split("\"discipline\":\"")[1].split("\"")[0];
                
                // Safely extracting the numerical logic variables
                double initial = Double.parseDouble(requestBody.split("\"initialDeposit\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));
                double target = Double.parseDouble(requestBody.split("\"targetAmount\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));
                double daily = Double.parseDouble(requestBody.split("\"dailyAmount\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));
                
                String execTime = requestBody.split("\"execTime\":\"")[1].split("\"")[0];
                String startDate = requestBody.split("\"startDate\":\"")[1].split("\"")[0];
                String endDate = requestBody.split("\"endDate\":\"")[1].split("\"")[0];

                java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db");
                
                // Verify PIN & Balance
                String authSql = "SELECT balance FROM Users WHERE google_id = ? AND pin = ?";
                java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                authStmt.setString(1, googleId); authStmt.setString(2, pin);
                java.sql.ResultSet rsAuth = authStmt.executeQuery();
                
                if (!rsAuth.next()) {
                    String err = "Invalid PIN."; exchange.sendResponseHeaders(401, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); conn.close(); return;
                }
                
                // If they are setting a "Fixed Lock" with an initial deposit, check if they have the funds
                if (initial > 0 && rsAuth.getDouble("balance") < initial) {
                    String err = "Insufficient Vault Funds to fulfill this initial lock deposit."; 
                    exchange.sendResponseHeaders(400, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); conn.close(); return;
                }

                conn.setAutoCommit(false);

                if (initial > 0) {
                    // Deduct initial deposit from main balance
                    String deductSql = "UPDATE Users SET balance = balance - ? WHERE account_number = ?";
                    java.sql.PreparedStatement deductStmt = conn.prepareStatement(deductSql);
                    deductStmt.setDouble(1, initial); deductStmt.setString(2, account);
                    deductStmt.executeUpdate();
                    
                    // THE FIX: Custom Ledger Routing - Assigning the Portfolio Name as the Receiver
                    String txSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES (?, ?, ?, ?, datetime('now'))";
                    java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                    txStmt.setString(1, account); 
                    txStmt.setString(2, name + " (Savings)"); 
                    txStmt.setDouble(3, initial); 
                    txStmt.setString(4, "Initial Deposit");
                    txStmt.executeUpdate();
                }

                // Create the Savings Portfolio in the database
                String saveSql = "INSERT INTO Savings (account_number, plan_name, category, discipline, current_balance, target_amount, daily_amount, exec_time, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                java.sql.PreparedStatement saveStmt = conn.prepareStatement(saveSql);
                saveStmt.setString(1, account); saveStmt.setString(2, name); saveStmt.setString(3, category);
                saveStmt.setString(4, discipline); saveStmt.setDouble(5, initial); saveStmt.setDouble(6, target);
                saveStmt.setDouble(7, daily); saveStmt.setString(8, execTime); saveStmt.setString(9, startDate); saveStmt.setString(10, endDate);
                saveStmt.executeUpdate();

                // THE FIX: Opening a savings plan signals financial discipline (+5).
                // Small but positive — reflects real-world credit behaviour where
                // maintaining savings accounts improves your credit health profile.
                adjustCreditScore(conn, account, 5);

                conn.commit(); conn.close();
                String ok = "Portfolio Activated";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = "Vault Exception: " + e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: Securely transfers capital from the main Vault into a specific Savings Portfolio
    // THE FIX: Securely transfers capital and strictly closes DB connections to prevent server deadlocks
    static class FundSavingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                int planId = Integer.parseInt(requestBody.split("\"planId\":")[1].split(",")[0].replaceAll("[^\\d]", ""));
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                double amount = Double.parseDouble(requestBody.split("\"amount\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));

                // THE FIX: Try-With-Resources automatically closes the database connection even if the server crashes!
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String authSql = "SELECT account_number, balance FROM Users WHERE google_id = ? AND pin = ?";
                    java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                    authStmt.setString(1, googleId); authStmt.setString(2, pin);
                    java.sql.ResultSet rsAuth = authStmt.executeQuery();
                    
                    if (!rsAuth.next()) throw new Exception("Invalid Security PIN.");
                    if (rsAuth.getDouble("balance") < amount) throw new Exception("Insufficient Vault Funds.");
                    
                    String account = rsAuth.getString("account_number");

                    String nameSql = "SELECT plan_name, category, target_amount, current_balance FROM Savings WHERE id = ?";
                    java.sql.PreparedStatement nameStmt = conn.prepareStatement(nameSql);
                    nameStmt.setInt(1, planId);
                    java.sql.ResultSet rsName = nameStmt.executeQuery();
                    String planName = "Savings";
                    
                    if (rsName.next()) {
                        planName = rsName.getString("plan_name");
                        String category = rsName.getString("category");
                        double target = rsName.getDouble("target_amount");
                        double currentBal = rsName.getDouble("current_balance");
                        
                        if (category.equals("TARGET") && (currentBal + amount > target)) {
                            throw new Exception("Deposit exceeds target milestone of $" + String.format("%.2f", target));
                        }
                    }

                    conn.setAutoCommit(false);

                    String deductSql = "UPDATE Users SET balance = balance - ? WHERE account_number = ?";
                    java.sql.PreparedStatement deductStmt = conn.prepareStatement(deductSql);
                    deductStmt.setDouble(1, amount); deductStmt.setString(2, account);
                    deductStmt.executeUpdate();

                    String saveSql = "UPDATE Savings SET current_balance = current_balance + ? WHERE id = ? AND account_number = ?";
                    java.sql.PreparedStatement saveStmt = conn.prepareStatement(saveSql);
                    saveStmt.setDouble(1, amount); saveStmt.setInt(2, planId); saveStmt.setString(3, account);
                    saveStmt.executeUpdate();

                    // THE FIX: Custom Ledger Routing - Assigning the Portfolio Name as the Receiver
                    String txSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES (?, ?, ?, ?, datetime('now'))";
                    java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                    txStmt.setString(1, account); 
                    txStmt.setString(2, planName + " (Savings)"); 
                    txStmt.setDouble(3, amount); 
                    txStmt.setString(4, "Manual Injection");
                    txStmt.executeUpdate();

                    conn.commit();
                } // DB Connection is automatically sealed here.

                String ok = "Funds Injected";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = "Vault Exception: " + e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: The Strict/Lenient Liquidation Engine (Immunized against Deadlocks)
    static class WithdrawSavingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                int planId = Integer.parseInt(requestBody.split("\"planId\":")[1].split(",")[0].replaceAll("[^\\d]", ""));
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                double amount = Double.parseDouble(requestBody.split("\"amount\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));

                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String authSql = "SELECT account_number FROM Users WHERE google_id = ? AND pin = ?";
                    java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                    authStmt.setString(1, googleId); authStmt.setString(2, pin);
                    java.sql.ResultSet rsAuth = authStmt.executeQuery();
                    
                    if (!rsAuth.next()) throw new Exception("Invalid Security PIN.");
                    String account = rsAuth.getString("account_number");
                    
                    // THE FIX: Added 'timestamp' so Java can mathematically mint the APY Interest into the final withdrawal!
                    String planSql = "SELECT plan_name, discipline, category, current_balance, target_amount, end_date, timestamp, " +
                                     "CAST(strftime('%s', withdrawal_lock_until) - strftime('%s', 'now') AS INTEGER) AS time_left, " +
                                     "CAST(strftime('%s', 'now') - strftime('%s', timestamp) AS INTEGER) AS seconds_alive, " +
                                     "withdrawal_lock_until " +
                                     "FROM Savings WHERE id = ? AND account_number = ?";
                    java.sql.PreparedStatement planStmt = conn.prepareStatement(planSql);
                    planStmt.setInt(1, planId); planStmt.setString(2, account);
                    java.sql.ResultSet rsPlan = planStmt.executeQuery();
                    
                    if (!rsPlan.next()) throw new Exception("Portfolio not found.");
                    
                    int secondsAlive = rsPlan.getInt("seconds_alive");
                    double currentBal = rsPlan.getDouble("current_balance");
                    double interestEarned = currentBal * 0.05 * (secondsAlive / 31536000.0);
                    double totalAvailable = currentBal + interestEarned;
                    
                    if (totalAvailable < amount) throw new Exception("Insufficient reserves including yields.");
                    
                    String discipline = rsPlan.getString("discipline");
                    String category = rsPlan.getString("category");
                    double target = rsPlan.getDouble("target_amount");
                    int timeLeft = rsPlan.getInt("time_left");
                    boolean hasLock = rsPlan.getString("withdrawal_lock_until") != null;
                    String planName = rsPlan.getString("plan_name");
                    String endDateStr = rsPlan.getString("end_date");

                    // THE FIX: The Goal Completion Gate - If true, it skips ALL penalties!
                    boolean isGoalMet = false;
                    if (category.equals("TARGET") && currentBal >= target) {
                        isGoalMet = true;
                    } else if ((category.equals("DAILY") || category.equals("LOCKED")) && endDateStr != null && !endDateStr.trim().isEmpty()) {
                        try {
                            java.time.LocalDate endDate = java.time.LocalDate.parse(endDateStr);
                            if (!java.time.LocalDate.now().isBefore(endDate)) isGoalMet = true;
                        } catch (Exception e) {} // Failsafe
                    }

                    // If the goal is NOT met, strictly enforce the protocols
                    if (!isGoalMet) {
                        if (discipline.equals("STRICT")) {
                            if (category.equals("TARGET")) throw new Exception("STRICT PROTOCOL: Target milestone ($" + target + ") not yet reached.");
                            else if (category.equals("DAILY") || category.equals("LOCKED")) throw new Exception("STRICT PROTOCOL: Time lock has not yet expired.");
                            else throw new Exception("STRICT PROTOCOL: Fixed locks cannot be broken manually.");
                        } else if (discipline.equals("LENIENT")) {
                            if (!hasLock) {
                                String trigger = "UPDATE Savings SET withdrawal_lock_until = datetime('now', '+12 hours') WHERE id = ?";
                                java.sql.PreparedStatement trigStmt = conn.prepareStatement(trigger);
                                trigStmt.setInt(1, planId); trigStmt.executeUpdate();
                                throw new Exception("LENIENT PROTOCOL: 12-Hour algorithmic cooling period initiated. Please return later to claim funds.");
                            } else if (timeLeft > 0) {
                                int hours = timeLeft / 3600;
                                int mins = (timeLeft % 3600) / 60;
                                throw new Exception("Cooling period active. " + hours + "h " + mins + "m remaining until system unlock.");
                            }
                        }
                    }

                    conn.setAutoCommit(false);

                    String deductSql = "UPDATE Savings SET current_balance = current_balance - ?, withdrawal_lock_until = NULL WHERE id = ?";
                    java.sql.PreparedStatement deductStmt = conn.prepareStatement(deductSql);
                    deductStmt.setDouble(1, amount); deductStmt.setInt(2, planId);
                    deductStmt.executeUpdate();

                    String addSql = "UPDATE Users SET balance = balance + ? WHERE account_number = ?";
                    java.sql.PreparedStatement addStmt = conn.prepareStatement(addSql);
                    addStmt.setDouble(1, amount); addStmt.setString(2, account);
                    addStmt.executeUpdate();

                    // THE FIX: Custom Ledger Routing - Assigning the Portfolio Name as the Sender
                    String txSql = "INSERT INTO Transactions (sender_account, receiver_account, amount, description, timestamp) VALUES (?, ?, ?, ?, datetime('now'))";
                    java.sql.PreparedStatement txStmt = conn.prepareStatement(txSql);
                    txStmt.setString(1, planName + " (Savings)"); 
                    txStmt.setString(2, account); 
                    txStmt.setDouble(3, amount); 
                    txStmt.setString(4, "Liquidation");
                    txStmt.executeUpdate();

                    conn.commit(); 
                } // DB Connection is automatically sealed here.

                String ok = "Funds Liquidated";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = "Vault Exception: " + e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: The Cancellation Door (Immunized against Deadlocks)
    static class CancelWithdrawalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                int planId = Integer.parseInt(requestBody.split("\"planId\":")[1].split(",")[0].replaceAll("[^\\d]", ""));
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String sql = "UPDATE Savings SET withdrawal_lock_until = NULL WHERE id = ?";
                    java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setInt(1, planId);
                    stmt.executeUpdate();
                } // DB Connection is automatically sealed here.
                
                String ok = "Cancelled";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) {
                String err = e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: The Zero-Balance Deletion Protocol
    static class DeleteSavingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                int planId = Integer.parseInt(requestBody.split("\"planId\":")[1].split(",")[0].replaceAll("[^\\d]", ""));
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    // Failsafe: The SQL specifically demands the balance must be 0 to execute the deletion
                    String sql = "DELETE FROM Savings WHERE id = ? AND current_balance <= 0";
                    java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setInt(1, planId);
                    stmt.executeUpdate();
                } 
                
                String ok = "Portfolio Terminated";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) {
                String err = e.getMessage();
                exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: Retrieves the complete KYC Data and Credit Score
    static class FullProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Security Passport
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String sql = "SELECT * FROM Users WHERE google_id = ?";
                    java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, googleId);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        // THE FIX: Parsing the complete Limit Matrix state
                        double safeLimit = 0.0; try { safeLimit = rs.getDouble("transfer_limit"); } catch (Exception ignore) {}
                        double dLimit = 0.0; try { dLimit = rs.getDouble("daily_limit"); } catch (Exception ignore) {}
                        double wLimit = 0.0; try { wLimit = rs.getDouble("weekly_limit"); } catch (Exception ignore) {}
                        double mLimit = 0.0; try { mLimit = rs.getDouble("monthly_limit"); } catch (Exception ignore) {}
                        String unlockTime = rs.getString("limit_unlock_time");

                        String json = "{"
                            + "\"fullName\":\"" + rs.getString("full_name") + "\","
                            + "\"dob\":\"" + rs.getString("dob") + "\","
                            + "\"email\":\"" + rs.getString("email") + "\","
                            + "\"phone\":\"" + rs.getString("phone") + "\","
                            + "\"country\":\"" + rs.getString("country") + "\","
                            + "\"address\":\"" + rs.getString("address") + "\","
                            + "\"nokName\":\"" + rs.getString("nok_name") + "\","
                            + "\"nokDob\":\"" + rs.getString("nok_dob") + "\","
                            + "\"nokPhone\":\"" + rs.getString("nok_phone") + "\","
                            + "\"nokAddress\":\"" + rs.getString("nok_address") + "\","
                            + "\"account\":\"" + rs.getString("account_number") + "\","
                            + "\"creditScore\":" + rs.getInt("credit_score") + ","
                            + "\"transferLimit\":" + safeLimit + ","
                            + "\"dailyLimit\":" + dLimit + ","
                            + "\"weeklyLimit\":" + wLimit + ","
                            + "\"monthlyLimit\":" + mLimit + ","
                            + "\"limitUnlockTime\":\"" + (unlockTime == null ? "" : unlockTime) + "\""
                            + "}";
                        exchange.sendResponseHeaders(200, json.length());
                        exchange.getResponseBody().write(json.getBytes());
                    }
                }
                exchange.getResponseBody().close();
            } catch (Exception e) { exchange.sendResponseHeaders(500, 0); exchange.getResponseBody().close(); }
        }
    }

    // THE FIX: Updates the KYC Data
    static class UpdateProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Security Passport
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String phone = requestBody.split("\"phone\":\"")[1].split("\"")[0];
                String address = requestBody.split("\"address\":\"")[1].split("\"")[0];
                String nokPhone = requestBody.split("\"nokPhone\":\"")[1].split("\"")[0];
                String nokAddress = requestBody.split("\"nokAddress\":\"")[1].split("\"")[0];
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String authSql = "SELECT id FROM Users WHERE google_id = ? AND pin = ?";
                    java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                    authStmt.setString(1, googleId); authStmt.setString(2, pin);
                    if (!authStmt.executeQuery().next()) throw new Exception("Invalid Security PIN.");

                    String sql = "UPDATE Users SET phone = ?, address = ?, nok_phone = ?, nok_address = ? WHERE google_id = ?";
                    java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, phone); stmt.setString(2, address);
                    stmt.setString(3, nokPhone); stmt.setString(4, nokAddress); stmt.setString(5, googleId);
                    stmt.executeUpdate();
                }
                String ok = "Profile Updated";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = e.getMessage(); exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: Changes the Security PIN
    static class ChangePinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Security Passport
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String oldPin = requestBody.split("\"oldPin\":\"")[1].split("\"")[0];
                String newPin = requestBody.split("\"newPin\":\"")[1].split("\"")[0];
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String authSql = "SELECT id FROM Users WHERE google_id = ? AND pin = ?";
                    java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                    authStmt.setString(1, googleId); authStmt.setString(2, oldPin);
                    if (!authStmt.executeQuery().next()) throw new Exception("Old PIN is incorrect.");

                    String sql = "UPDATE Users SET pin = ? WHERE google_id = ?";
                    java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, newPin); stmt.setString(2, googleId);
                    stmt.executeUpdate();
                }
                String ok = "PIN Updated";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = e.getMessage(); exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: Changes Transfer Limit
    static class ChangeLimitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Security Passport
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                double newLimit = Double.parseDouble(requestBody.split("\"newLimit\":")[1].split(",")[0].replaceAll("[^\\d.]", ""));
                String limitType = requestBody.contains("\"limitType\":\"") ? requestBody.split("\"limitType\":\"")[1].split("\"")[0] : "transfer_limit";
                
                // Security map to prevent SQL Injection
                String targetColumn = "transfer_limit";
                if(limitType.equals("daily")) targetColumn = "daily_limit";
                if(limitType.equals("weekly")) targetColumn = "weekly_limit";
                if(limitType.equals("monthly")) targetColumn = "monthly_limit";

                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String authSql = "SELECT id FROM Users WHERE google_id = ? AND pin = ?";
                    java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                    authStmt.setString(1, googleId); authStmt.setString(2, pin);
                    if (!authStmt.executeQuery().next()) throw new Exception("Invalid Security PIN.");

                    if (limitType.equals("delete")) {
                        // THE FIX: Initiates the 24-Hour Deletion Time-Lock!
                        String lockSql = "UPDATE Users SET limit_unlock_time = datetime('now', '+24 hours', 'localtime') WHERE google_id = ?";
                        conn.prepareStatement(lockSql).executeUpdate();
                    } else {
                        String sql = "UPDATE Users SET " + targetColumn + " = ?, limit_unlock_time = NULL WHERE google_id = ?";
                        java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                        stmt.setDouble(1, newLimit); stmt.setString(2, googleId);
                        stmt.executeUpdate();
                    }
                }
                String ok = "Limit Updated";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = e.getMessage(); exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: The Foreign Key Cascading Engine
    static class ChangeAccountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); // THE FIX: Security Passport
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String googleId = requestBody.split("\"googleId\":\"")[1].split("\"")[0];
                String pin = requestBody.split("\"pin\":\"")[1].split("\"")[0];
                String newAcc = requestBody.split("\"newAccount\":\"")[1].split("\"")[0];
                
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:bank.db")) {
                    String authSql = "SELECT account_number FROM Users WHERE google_id = ? AND pin = ?";
                    java.sql.PreparedStatement authStmt = conn.prepareStatement(authSql);
                    authStmt.setString(1, googleId); authStmt.setString(2, pin);
                    java.sql.ResultSet rs = authStmt.executeQuery();
                    if (!rs.next()) throw new Exception("Invalid Security PIN.");
                    
                    String oldAcc = rs.getString("account_number");

                    String checkSql = "SELECT id FROM Users WHERE account_number = ?";
                    java.sql.PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                    checkStmt.setString(1, newAcc);
                    if (checkStmt.executeQuery().next()) throw new Exception("Account number already in use.");

                    conn.setAutoCommit(false); 

                    conn.prepareStatement("UPDATE Users SET account_number = '" + newAcc + "' WHERE account_number = '" + oldAcc + "'").executeUpdate();
                    conn.prepareStatement("UPDATE Transactions SET sender_account = '" + newAcc + "' WHERE sender_account = '" + oldAcc + "'").executeUpdate();
                    conn.prepareStatement("UPDATE Transactions SET receiver_account = '" + newAcc + "' WHERE receiver_account = '" + oldAcc + "'").executeUpdate();
                    conn.prepareStatement("UPDATE Loans SET account_number = '" + newAcc + "' WHERE account_number = '" + oldAcc + "'").executeUpdate();
                    conn.prepareStatement("UPDATE Savings SET account_number = '" + newAcc + "' WHERE account_number = '" + oldAcc + "'").executeUpdate();
                    conn.prepareStatement("UPDATE Beneficiaries SET owner_account = '" + newAcc + "' WHERE owner_account = '" + oldAcc + "'").executeUpdate();
                    conn.prepareStatement("UPDATE Beneficiaries SET bene_account = '" + newAcc + "' WHERE bene_account = '" + oldAcc + "'").executeUpdate();

                    conn.commit();
                }
                String ok = "Account Cascaded";
                exchange.sendResponseHeaders(200, ok.length()); exchange.getResponseBody().write(ok.getBytes()); exchange.getResponseBody().close();
            } catch (Exception e) { 
                String err = e.getMessage(); exchange.sendResponseHeaders(500, err.length()); exchange.getResponseBody().write(err.getBytes()); exchange.getResponseBody().close(); 
            }
        }
    }

    // THE FIX: The FICO Credit Score Adjustment Engine. A shared private helper method
    // called by TakeLoanHandler, RepayLoanHandler, and CreateSavingsHandler after every
    // meaningful financial event. Centralising this logic here means we only write the
    // clamping SQL once instead of repeating it in every handler.
    //
    // MAX(300, MIN(850, credit_score + ?)) explained step by step:
    //   1. credit_score + ? → applies the delta [the raw change amount, positive or negative]
    //   2. MIN(850, ...) → if the result exceeds 850, caps it at 850 (FICO ceiling)
    //   3. MAX(300, ...) → if the result drops below 300, floors it at 300 (FICO floor)
    // This all happens inside a single SQL expression, so it is atomic [executes as one
    // indivisible database operation with no risk of a partially-applied change].
    private static void adjustCreditScore(java.sql.Connection conn, String accountNumber, int delta) {
        try {
            String sql = "UPDATE Users SET credit_score = MAX(300, MIN(850, credit_score + ?)) WHERE account_number = ?";
            java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, delta);
            stmt.setString(2, accountNumber);
            stmt.executeUpdate();
        } catch (Exception ignore) {
            // THE FIX: Score adjustments are intentionally non-critical. If this UPDATE
            // fails for any reason (e.g. the credit_score column doesn't exist in an
            // older DB), we silently swallow the error so the main financial transaction
            // (the loan, repayment, or savings creation) is NEVER rolled back just because
            // of a score update. The catch block here is the safety net.
        }
    }

}