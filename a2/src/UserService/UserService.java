import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import src.lib.ServiceUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UserService {
    static int currUserConnection = 0;
    static int currOrderConnection = 0;
    static Connection[] userConnections;
    static Connection[] orderConnections;

    
    /** 
     * @param args
     * @throws IOException
     * @throws SQLException 
     */
    public static void main(String[] args) throws IOException, SQLException {
        // create a database connection
        try{
            String wal = "pragma journal_mode=wal;\n"+
                        "PRAGMA synchronous=NORMAL;\n"+
                        "PRAGMA cache_size=-500000;\n"+
                        "ATTACH DATABASE ':memory:' AS memdb;";


            Connection connection = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/user.db");
            Statement statement = connection.createStatement();
            // SQL statement for creating a new table
            String sql = "CREATE TABLE IF NOT EXISTS users (\n"
            + "	id integer PRIMARY KEY,\n"
            + "	username varchar(255),\n"
            + "	email varchar(255),\n"
            + "	password varchar(255)\n"
            + ");\n";
            statement.execute(sql);
            statement.execute(wal);
            connection.close();

            Connection connectionOrder = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/order.db");
            Statement statementOrder = connectionOrder.createStatement();
            // SQL statement for creating a new table
            sql = "CREATE TABLE IF NOT EXISTS orders (\n"
            + "	id integer PRIMARY KEY,\n"
            + "	userid integer,\n"
            + "	productid integer,\n"
            + "	quantity integer\n"
            + ");";
            statementOrder.execute(sql);
            statementOrder.execute(wal);
            connectionOrder.close();
        } catch(SQLException e){
          // if the error message is "out of memory",
          // it probably means no database file is found
          System.err.println(e.getMessage());
        }

        //Read config.json
        String path = args[0];
        String jsonString = "";
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Process each line
                jsonString = jsonString.concat(line);
                jsonString = jsonString.replace(" ","");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Map representing config.json
        JSONObject jsonObject = new JSONObject(jsonString);
        
        JSONArray UserServices = jsonObject.getJSONArray("UserService");
        //int num_services = UserServices.length();
        userConnections = new Connection[1];
        orderConnections = new Connection[1];
        userConnections[0] = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/user.db");
        orderConnections[0] = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/order.db");
        for (int i = 0; i < UserServices.length(); i++) {
            // int curr = 100 * i;
            // int end = curr + 100;
            // for(int j = curr; j < end; j++){
            //     userConnections[j] = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/user.db");
            //     orderConnections[j] = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/order.db");
            // }
            
            JSONObject service = UserServices.getJSONObject(i);
            int port = service.getInt("port");

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Set up context for /user request
            server.createContext("/user", new UserHandler());

            // Set up context for /shutdown request
            server.createContext("/shutdown", new ShutdownHandler());

            // Set up context for /restart request
            server.createContext("/restart", new RestartHandler());

            server.createContext("/purchased", new PurchasedHandler());


            server.setExecutor(null); // creates a default executor

            server.start();

            System.out.println("Server started on port " + port);
        }

       
        
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            //ServiceUtil.printClientInfo(exchange);

            Connection userConnection = userConnections[currUserConnection];
            Statement statement = null;
            try {
                statement = userConnection.createStatement();
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            currUserConnection = (currUserConnection + 1) % userConnections.length;

            // Handle GET request for /user
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {

                    //Get parameter
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length() + 1;
                    String params = clientUrl.substring(index);

                    //Checking if the request is valid
                    if(!ServiceUtil.isNumeric(params)){
                        responseMap.put("rcode", "400");
                    } else{
                        //Execute query
                        makeResponse(responseMap, params, statement);
                    }
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }
            //Handle POST request for /user 
            else if("POST".equals(exchange.getRequestMethod())){
                try {
                    String dataString = ServiceUtil.getRequestBody(exchange);

                    JSONObject dataMap = ServiceUtil.bodyToMap(dataString);
                    if(ServiceUtil.isJSON(dataString) && ServiceUtil.isValidUser(dataMap)){

                        //Handle create
                        if(dataMap.get("command").equals("create")){
                            if(!ServiceUtil.getQuery("users", dataMap.get("id").toString().toString(), statement).isBeforeFirst()){
                                //Create a new User
                                String command = String.format(
                                                    "INSERT INTO users\n" + 
                                                    "(id, username, email, password)\n" +
                                                    "VALUES\n" +
                                                    "(%s, \'%s\', \'%s\', \'%s\')",
                                                    dataMap.get("id").toString(),
                                                    dataMap.get("username"),
                                                    dataMap.get("email"),
                                                    dataMap.get("password")
                                                );
                                statement.execute(command);
                                makeResponse(responseMap, dataMap.get("id").toString(), statement);
                            } else{
                                //User already exists
                                responseMap.put("rcode", "409");
                            }
                        }

                        //Handle update
                        if(dataMap.get("command").equals("update")){
                            if(ServiceUtil.getQuery("users", dataMap.get("id").toString(), statement).isBeforeFirst()){
                                
                                //Check if the username needs to be updated
                                if(dataMap.has("username")){
                                    ServiceUtil.updateDB("users", "username", dataMap.get("username").toString(), dataMap.get("id").toString(), statement);
                                }

                                //Check if the email needs to be updated
                                if(dataMap.has("email")){
                                    ServiceUtil.updateDB("users", "email", dataMap.get("email").toString(), dataMap.get("id").toString(), statement);
                                }

                                //Check if the password needs to be updated
                                if(dataMap.has("password")){
                                    ServiceUtil.updateDB("users", "password", dataMap.get("password").toString(), dataMap.get("id").toString(), statement);
                                }

                                makeResponse(responseMap, dataMap.get("id").toString(), statement);
                            } else{
                                //User does not exist
                                responseMap.put("rcode", "404");
                            }
                        }

                        //Handle delete
                        if(dataMap.get("command").equals("delete")){
                            ResultSet resultSet = ServiceUtil.getQuery("users", dataMap.get("id").toString(), statement);
                            if(resultSet.isBeforeFirst()){
                                resultSet.next();
                                //Authenticate
                                if(resultSet.getString("username").equals(dataMap.get("username")) &&
                                    resultSet.getString("email").equals(dataMap.get("email")) &&
                                    resultSet.getString("password").equals(dataMap.get("password"))
                                ){
                                    String command = String.format("DELETE FROM users WHERE id = %s;", dataMap.get("id").toString());
                                    statement.execute(command);
                                    responseMap.put("rcode", "200");
                                } else{
                                    //Authetication failed
                                    responseMap.put("rcode", "404");
                                }
                            } else{
                                //User does not exist
                                responseMap.put("rcode", "404");
                            }
                        }
                    } else{
                        responseMap.put("rcode", 400);
                    }
                    
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            ServiceUtil.sendResponse(exchange, responseMap);

        }

    }


    static class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");

            // JDBC connection parameters for save.db database
            String saveUrl = "jdbc:sqlite:compiled/UserService/save.db";
            String savePurchasedUrl = "jdbc:sqlite:compiled/UserService/savePurchased.db";
            Connection purchasedConnection = null;
            Connection saveConnection = null;
            Connection savePurchasedConnection = null;
            Connection userConnection = null;

            try {
            
                userConnection = userConnections[currOrderConnection];
                currUserConnection = (currUserConnection + 1) % userConnections.length;





                purchasedConnection = orderConnections[currOrderConnection];
                currOrderConnection = (currOrderConnection + 1) % orderConnections.length;

                // Connect to save.db database (SQLite)
                saveConnection = DriverManager.getConnection(saveUrl);
                PreparedStatement createStatement = saveConnection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS users (\n"
                    + "	id integer PRIMARY KEY,\n"
                    + "	username varchar(255),\n"
                    + "	email varchar(255),\n"
                    + "	password varchar(255)\n"
                    + ");");
                createStatement.execute();
                createStatement.close();

                savePurchasedConnection = DriverManager.getConnection(savePurchasedUrl);
                PreparedStatement createStatementPurchased = savePurchasedConnection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS orders (\n"
                    + "	id integer PRIMARY KEY,\n"
                    + "	userid integer,\n"
                    + "	productid integer,\n"
                    + "	quantity integer\n"
                    + ");");
                createStatementPurchased.execute();
                createStatementPurchased.close();

                // Retrieve data from user table in source database
                transferData(userConnection, saveConnection);
                transferPurchasedData(purchasedConnection, savePurchasedConnection);

                System.out.println("Data saved successfully, Exiting.");
                responseMap.put("rcode", 200);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close connections
                try {
                    if (saveConnection != null) saveConnection.close();
                    userConnection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ServiceUtil.sendResponse(exchange, responseMap);
                System.exit(0);
            }
        }
    }

    static class RestartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");

            // JDBC connection parameters for save.db database
            String saveUrl = "jdbc:sqlite:compiled/UserService/save.db";
            String savePurchasedUrl = "jdbc:sqlite:compiled/UserService/savePurchased.db";
            Connection purchasedConnection = null;
            Connection saveConnection = null;
            Connection savePurchasedConnection = null;
            Connection userConnection = null;

            try {
                userConnection = userConnections[currUserConnection];
                currUserConnection = (currUserConnection + 1) % userConnections.length;





                purchasedConnection = orderConnections[currOrderConnection];
                currOrderConnection = (currOrderConnection + 1) % orderConnections.length;
                // Connect to save.db database (SQLite)
                saveConnection = DriverManager.getConnection(saveUrl);
                savePurchasedConnection = DriverManager.getConnection(savePurchasedUrl);

                // Retrieve data from user table in source database
                transferData(saveConnection, userConnection);
                transferPurchasedData(savePurchasedConnection, purchasedConnection);

                System.out.println("Data restored successfully.");
                responseMap.put("rcode", 200);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close connections
                try {
                    if (saveConnection != null) saveConnection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ServiceUtil.sendResponse(exchange, responseMap);
            }
        }
    }

    static class PurchasedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            //Print client info for debugging
            //ServiceUtil.printClientInfo(exchange);

            // Handle POST request for /test
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", 500);


            Connection orderConnection = orderConnections[currOrderConnection];
            Statement orderStatement = null;
            try {
                orderStatement = orderConnection.createStatement();
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            currOrderConnection = (currOrderConnection + 1) % orderConnections.length;


            if ("GET".equals(exchange.getRequestMethod())){
                try {

                    //Get parameter
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("purchased") + "purchased".length() + 1;
                    String params = clientUrl.substring(index);

                    //Checking if the request is valid
                    if(!ServiceUtil.isNumeric(params)){
                        responseMap.put("rcode", "400");
                    } else{
                        //Execute query
                        makeResponsePurchased(responseMap, params, orderStatement);
                    }
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else{
                try{
                String dataString = ServiceUtil.getRequestBody(exchange);

                JSONObject dataMap = ServiceUtil.bodyToMap(dataString);


                //Save order
                String command = String.format(
                    "INSERT INTO orders\n" + 
                    "(id, userid, productid, quantity)\n" +
                    "VALUES\n" +
                    "(%s, %s, %s, %s)",
                    dataMap.get("id").toString(),
                    dataMap.get("user_id").toString(),
                    dataMap.get("product_id").toString(),
                    dataMap.get("quantity").toString()
                );
                orderStatement.execute(command);
                } catch(SQLException e){
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            ServiceUtil.sendResponse(exchange, responseMap);


        }

    }



    /** 
     * @param responseMap
     * @param params
     * @param statement
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public static void makeResponse(JSONObject responseMap, String params, Statement statement) throws SQLException, NoSuchAlgorithmException {
        ResultSet result = ServiceUtil.getQuery("users", params, statement);

        //Check if user is found
        if (!result.isBeforeFirst() ) {
            responseMap.put("rcode", "404"); 
        } else{ 
            //Make a response
            responseMap.put("rcode", "200");
            result.next();
            responseMap.put("id", Integer.parseInt(params));
            responseMap.put("username", result.getString("username"));
            responseMap.put("email", result.getString("email"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(result.getString("password").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
            String hex = Integer.toHexString(0xff & encodedhash[i]).toUpperCase();
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
            responseMap.put("password", hexString.toString());
        }
    }

    /** 
     * @param responseMap
     * @param params
     * @param statement
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public static void makeResponsePurchased(JSONObject responseMap, String params, Statement statement) throws SQLException, NoSuchAlgorithmException {
        ResultSet result = statement.executeQuery("SELECT * FROM orders WHERE userid = " + params + ";");

        //Check if user is found
        if (!result.isBeforeFirst() ) {    
            responseMap.put("rcode", "404"); 
        } else{ 
            //Make a response
            responseMap.put("rcode", "200");
            
            // Make a response
        responseMap.put("rcode", "200");
        
        // Iterate over the ResultSet to construct the JSON object
        while (result.next()) {
            int productId = result.getInt("productid");
            int quantity = result.getInt("quantity");
            
            // Add product id and count to the JSON object
            if(responseMap.has(String.valueOf(productId))){
                responseMap.put(String.valueOf(productId), quantity + responseMap.getInt(String.valueOf(productId)));
            } else {
                responseMap.put(String.valueOf(productId), quantity);
            }
        }
        
    }
}

    /** 
     * @param srcConnection
     * @param dstConnection
     * @throws SQLException
     */
    public static void transferData(Connection srcConnection ,Connection dstConnection) throws SQLException {
        PreparedStatement selectStatement = srcConnection.prepareStatement("SELECT * FROM users");
        ResultSet resultSet = selectStatement.executeQuery();
        

        // Insert data into user table in save.db database
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String username = resultSet.getString("username");
            String email = resultSet.getString("email");
            String password = resultSet.getString("password");


            // Insert data into save.db database (SQLite)
            PreparedStatement insertStatement = dstConnection.prepareStatement("INSERT or REPLACE INTO users (id, username, email, password) VALUES (?, ?, ?, ?)");
            insertStatement.setInt(1, id);
            insertStatement.setString(2, username);
            insertStatement.setString(3, email);
            insertStatement.setString(4, password);
            insertStatement.executeUpdate();
            insertStatement.close();
        }

        resultSet.close();
        selectStatement.close();
    }

    /** 
     * @param srcConnection
     * @param dstConnection
     * @throws SQLException
     */
    public static void transferPurchasedData(Connection srcConnection ,Connection dstConnection) throws SQLException {
        PreparedStatement selectStatement = srcConnection.prepareStatement("SELECT * FROM orders");
        ResultSet resultSet = selectStatement.executeQuery();
        

        // Insert data into user table in save.db database
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            int userid = resultSet.getInt("userid");
            int productid = resultSet.getInt("productid");
            int quantity = resultSet.getInt("quantity");


            // Insert data into save.db database (SQLite)
            PreparedStatement insertStatement = dstConnection.prepareStatement("INSERT or REPLACE INTO orders (id, userid, productid, quantity) VALUES (?, ?, ?, ?)");
            insertStatement.setInt(1, id);
            insertStatement.setInt(2, userid);
            insertStatement.setInt(3, productid);
            insertStatement.setInt(4, quantity);
            insertStatement.executeUpdate();
            insertStatement.close();
        }

        resultSet.close();
        selectStatement.close();
    }

}

