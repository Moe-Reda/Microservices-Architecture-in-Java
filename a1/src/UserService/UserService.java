import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import docs.ServiceUtil;

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

                    //Execute query
                    ServiceUtil.makeResponse(responseMap, params, statement);
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
                    JSONObject dataMap = ServiceUtil.bodyToMap(ServiceUtil.getRequestBody(exchange));

                    //Handle create
                    if(dataMap.get("command").equals("create")){
                        if(!ServiceUtil.getQuery(dataMap.get("id").toString().toString(), statement).isBeforeFirst()){
                            //Create a new User
                            String command = String.format(
                                                "INSERT ITO users\n" + 
                                                "(id, username, email, password)\n" +
                                                "VALUES\n" +
                                                "(%s, \'%s\', \'%s\', \'%s\')",
                                                dataMap.get("id").toString(),
                                                dataMap.get("username"),
                                                dataMap.get("email"),
                                                dataMap.get("password")
                                            );
                            statement.execute(command);
                            ServiceUtil.makeResponse(responseMap, dataMap.get("id").toString(), statement);
                        } else{
                            //User already exists
                            responseMap.put("rcode", "401");
                        }
                    }

                    //Handle update
                    if(dataMap.get("command").equals("update")){
                        if(ServiceUtil.getQuery(dataMap.get("id").toString(), statement).isBeforeFirst()){
                            
                            //Check if the username needs to be updated
                            if(dataMap.get("username") != null){
                                ServiceUtil.updateDB("username", dataMap.get("username").toString(), dataMap.get("id").toString(), statement);
                            }

                             //Check if the email needs to be updated
                            if(dataMap.get("email") != null){
                                ServiceUtil.updateDB("email", dataMap.get("email").toString(), dataMap.get("id").toString(), statement);
                            }

                             //Check if the password needs to be updated
                            if(dataMap.get("password") != null){
                                ServiceUtil.updateDB("password", dataMap.get("password").toString(), dataMap.get("id").toString(), statement);
                            }

                            ServiceUtil.makeResponse(responseMap, dataMap.get("id").toString(), statement);
                        } else{
                            //User does not exist
                            responseMap.put("rcode", "404");
                        }
                    }

                    //Handle delete
                    if(dataMap.get("command").equals("delete")){
                        if(ServiceUtil.getQuery(dataMap.get("id").toString(), statement).isBeforeFirst()){
                            //Check if the username needs to be updated
                            ServiceUtil.makeResponse(responseMap, dataMap.get("id").toString(), statement);
                            String command = String.format("DELETE FROM users WHERE id = %s;", dataMap.get("id").toString());
                            statement.execute(command);
                        } else{
                            //User does not exist
                            responseMap.put("rcode", "404");
                        }
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

}

