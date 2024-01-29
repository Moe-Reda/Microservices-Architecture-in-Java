import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UserService {
    static JSONObject jsonObject = new JSONObject();
    static Connection connection = null;
    static Statement statement = null;

    public static void main(String[] args) throws IOException {
        // create a database connection
        try{
            //Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:user.db");
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
        String path = "../../".concat(args[0]);
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


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            printClientInfo(exchange);

            // Handle GET request for /user
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");

                    //Get parameter
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);

                    //Execute query
                    makeResponse(responseMap, params, statement);
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }
            //Handle POST request for /user 
            else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    Map<String, String> dataMap = bodyToMap(getRequestBody(exchange));

                    //Handle create
                    if(dataMap.get("command").equals("create")){
                        if(!getQuery(dataMap.get("id"), statement).isBeforeFirst()){
                            //Create a new User
                            String command = String.format(
                                                "INSERT ITO users\n" + 
                                                "(id, username, email, password)\n" +
                                                "VALUES\n" +
                                                "(%s, \'%s\', \'%s\', \'%s\')",
                                                dataMap.get("id"),
                                                dataMap.get("username"),
                                                dataMap.get("email"),
                                                dataMap.get("password")
                                            );
                            statement.execute(command);
                            makeResponse(responseMap, dataMap.get("id"), statement);
                        } else{
                            //User already exists
                            responseMap.put("rcode", "401");
                        }
                    }

                    //Handle update
                    if(dataMap.get("command").equals("update")){
                        if(getQuery(dataMap.get("id"), statement).isBeforeFirst()){
                            
                            //Check if the username needs to be updated
                            if(dataMap.get("username") != null){
                                updateDB("username", dataMap.get("username"), dataMap.get("id"));
                            }

                             //Check if the email needs to be updated
                            if(dataMap.get("email") != null){
                                updateDB("email", dataMap.get("email"), dataMap.get("id"));
                            }

                             //Check if the password needs to be updated
                            if(dataMap.get("password") != null){
                                updateDB("password", dataMap.get("password"), dataMap.get("id"));
                            }

                            makeResponse(responseMap, dataMap.get("id"), statement);
                        } else{
                            //User does not exist
                            responseMap.put("rcode", "404");
                        }
                    }

                    //Handle delete
                    if(dataMap.get("command").equals("delete")){
                        if(getQuery(dataMap.get("id"), statement).isBeforeFirst()){
                            //Check if the username needs to be updated
                            makeResponse(responseMap, dataMap.get("id"), statement);
                            String command = String.format("DELETE FROM users WHERE id = %s;", dataMap.get("id"));
                            statement.execute(command);
                        } else{
                            //User does not exist
                            responseMap.put("rcode", "404");
                        }
                    }
                    
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            sendResponse(exchange, responseMap);


        }

    }

    private static void updateDB(String field, String value, String id) throws SQLException {
        String command;
        command = String.format("UPDATE users SET %s = \'%s\' WHERE id = %s", field, value, id);
        statement.execute(command);
    }

    private static void makeResponse(Map<String, String> responseMap, String params, Statement statement) throws SQLException, NoSuchAlgorithmException {
            ResultSet result = getQuery(params, statement);

            //Check if user is found
            if (!result.isBeforeFirst() ) {    
                responseMap.put("rcode", "404"); 
            } else{ 
                //Make a response
                responseMap.put("rcode", "200");
                result.first();   
                responseMap.put("id", params);
                responseMap.put("username", result.getString("username"));
                responseMap.put("email", result.getString("email"));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(result.getString("password").getBytes(StandardCharsets.UTF_8));
                responseMap.put("password", encodedhash.toString());
            }
        }

    private static ResultSet getQuery(String params, Statement statement) throws SQLException {
        return statement.executeQuery("SELECT * FROM users WHERE id = " + params + ";");
    }

    private static void sendResponse(HttpExchange exchange, Map<String, String> responseMap) throws IOException {
        int rcode = Integer.parseInt(responseMap.get("rcode"));
        responseMap.remove("rcode");
        exchange.sendResponseHeaders(rcode, responseMap.toString().length());
        OutputStream os = exchange.getResponseBody();
        os.write(responseMap.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    private static String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }

    private static void printClientInfo(HttpExchange exchange) throws IOException {
        String clientAddress = exchange.getRemoteAddress().getAddress().toString();
        String requestMethod = exchange.getRequestMethod();
        String requestURI = exchange.getRequestURI().toString();
        Map<String, List<String>> requestHeaders = exchange.getRequestHeaders();

        System.out.println("Client Address: " + clientAddress);
        System.out.println("Request Method: " + requestMethod);
        System.out.println("Request URI: " + requestURI);
        System.out.println("Request Headers: " + requestHeaders);
        // Print all request headers
        //for (Map.Entry<String, List<String>> header : requestHeaders.entrySet()) {
        //   System.out.println(header.getKey() + ": " + header.getValue().getFirst());
        //}

        //System.out.println("Request Body: " + getRequestBody(exchange));
    }

    private static Map<String, String> bodyToMap(String data) {
            String[] keyValueList = data.replace(" ", "")
                                        .replace("}", "")
                                        .replace("{", "")
                                        .split(",");
            Map<String, String> map = new HashMap<String, String>();
            for(String keyValue : keyValueList){
                String[] keyValuePair = keyValue.split("=");
                map.put(keyValuePair[0], keyValuePair[1]);
            }
            return map;
        }
}

