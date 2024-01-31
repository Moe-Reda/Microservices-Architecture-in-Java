import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import src.lib.ServiceUtil;

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
    static JSONObject data = new JSONObject();
    static Connection connection = null;
    static Statement statement = null;

    
    /** 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // create a database connection
        try{
            connection = DriverManager.getConnection("jdbc:sqlite:compiled/UserService/user.db");
            statement = connection.createStatement();
            // SQL statement for creating a new table
            String sql = "CREATE TABLE IF NOT EXISTS users (\n"
            + "	id integer PRIMARY KEY,\n"
            + "	username varchar(255),\n"
            + "	email varchar(255),\n"
            + "	password varchar(255)\n"
            + ");";
            statement.execute(sql);
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

        int port = jsonObject.getJSONObject("UserService").getInt("port");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up context for /user request
        server.createContext("/user", new UserHandler());

        // Set up context for /shutdown request
        server.createContext("/shutdown", new ShutdownHandler());

        // Set up context for /restart request
        server.createContext("/restart", new RestartHandler());


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            ServiceUtil.printClientInfo(exchange);

            // Handle GET request for /user
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");

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
                    System.out.println("It is a POST request for user");
                    String dataString = ServiceUtil.getRequestBody(exchange);

                    System.out.println("The request body: " + dataString);

                    JSONObject dataMap = ServiceUtil.bodyToMap(dataString);
                    if(ServiceUtil.isJSON(dataString) && ServiceUtil.isValidUser(dataMap)){

                        //Handle create
                        if(dataMap.get("command").equals("create")){
                            System.out.println("Create an entry");
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
                                responseMap.put("rcode", "401");
                            }
                        }

                        //Handle update
                        if(dataMap.get("command").equals("update")){
                            System.out.println("Update an entry");
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
                            System.out.println("Delete an entry");
                            ResultSet resultSet = ServiceUtil.getQuery("users", dataMap.get("id").toString(), statement);
                            if(resultSet.isBeforeFirst()){
                                resultSet.next();
                                //Authenticate
                                if(resultSet.getString("username").equals(dataMap.get("username")) &&
                                    resultSet.getString("email").equals(dataMap.get("email")) &&
                                    resultSet.getString("password").equals(dataMap.get("password"))
                                ){
                                    makeResponse(responseMap, dataMap.get("id").toString(), statement);
                                    String command = String.format("DELETE FROM users WHERE id = %s;", dataMap.get("id").toString());
                                    statement.execute(command);
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
            Connection saveConnection = null;
            ResultSet resultSet = null;

            try {
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

                // Retrieve data from user table in source database
                transferData(connection, saveConnection);

                System.out.println("Data saved successfully, Exiting.");
                responseMap.put("rcode", 200);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close connections
                try {
                    if (resultSet != null) resultSet.close();
                    if (saveConnection != null) saveConnection.close();
                    statement.close();
                    connection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.exit(0);
                ServiceUtil.sendResponse(exchange, responseMap);
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
            Connection saveConnection = null;

            try {
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

                // Retrieve data from user table in source database
                transferData(saveConnection, connection);

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
                responseMap.put("password", encodedhash.toString());
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

}

