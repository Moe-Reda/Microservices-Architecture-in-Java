import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import src.lib.ServiceUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ProductService {
    static JSONObject jsonObject = new JSONObject();
    static Connection connection = null;
    static Statement statement = null;

    
    /** 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // create a database connection
        try{
            connection = DriverManager.getConnection("jdbc:sqlite:compiled/ProductService/product.db");
            statement = connection.createStatement();
            // SQL statement for creating a new table
            String sql = "CREATE TABLE IF NOT EXISTS products (\n"
            + "	id integer PRIMARY KEY,\n"
            + "	name varchar(255),\n"
            + "	description varchar(255),\n"
            + "	price float,\n"
            + "	quantity integer\n"
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

        int port = jsonObject.getJSONObject("ProductService").getInt("port");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up context for /product request
        server.createContext("/product", new ProductHandler());

        // Set up context for /shutdown request
        server.createContext("/shutdown", new ShutdownHandler());

        // Set up context for /restart request
        server.createContext("/restart", new RestartHandler());


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            ServiceUtil.printClientInfo(exchange);

            // Handle GET request for /Product
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for Product");

                    //Get parameter
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length() + 1;
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
            //Handle POST request for /Product 
            else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for Product");
                    String dataString = ServiceUtil.getRequestBody(exchange);

                    System.out.println("The request body: " + dataString);

                    JSONObject dataMap = ServiceUtil.bodyToMap(dataString);

                    //Checking if it request is valid
                    if(ServiceUtil.isJSON(dataString) && ServiceUtil.isValidProduct(dataMap)){

                        //Handle create
                        if(dataMap.get("command").equals("create")){
                            System.out.println("Create an entry");
                            if(!ServiceUtil.getQuery("products", dataMap.get("id").toString().toString(), statement).isBeforeFirst()){
                                //Create a new Product
                                String command = String.format(
                                                    "INSERT INTO products\n" + 
                                                    "(id, name, description, price, quantity)\n" +
                                                    "VALUES\n" +
                                                    "(%s, \'%s\', \'%s\', %s, %s)",
                                                    dataMap.get("id"),
                                                    dataMap.get("name"),
                                                    dataMap.get("description"),
                                                    dataMap.get("price"),
                                                    dataMap.get("quantity")
                                                );
                                statement.execute(command);
                                makeResponse(responseMap, dataMap.get("id").toString(), statement);
                            } else{
                                //Product already exists
                                responseMap.put("rcode", "401");
                            }
                        }

                        //Handle update
                        if(dataMap.get("command").equals("update")){
                            System.out.println("Update an entry");
                            if(ServiceUtil.getQuery("products", dataMap.get("id").toString(), statement).isBeforeFirst()){
                                
                                //Check if the name needs to be updated
                                if(dataMap.has("name")){
                                    ServiceUtil.updateDB("products", "name", dataMap.get("name").toString(), dataMap.get("id").toString(), statement);
                                }

                                //Check if the description needs to be updated
                                if(dataMap.has("description")){
                                    ServiceUtil.updateDB("products", "description", dataMap.get("description").toString(), dataMap.get("id").toString(), statement);
                                }

                                //Check if the price needs to be updated
                                if(dataMap.has("price")){
                                    ServiceUtil.updateDB("products", "price", dataMap.get("price").toString(), dataMap.get("id").toString(), statement);
                                }

                                //Check if the quantity needs to be updated
                                if(dataMap.has("quantity")){
                                    ServiceUtil.updateDB("products", "quantity", dataMap.get("quantity").toString(), dataMap.get("id").toString(), statement);
                                }

                                makeResponse(responseMap, dataMap.get("id").toString(), statement);
                            } else{
                                //Product does not exist
                                responseMap.put("rcode", "404");
                            }
                        }

                        //Handle delete
                        if(dataMap.get("command").equals("delete")){
                            System.out.println("Delete an entry");
                            ResultSet resultSet = ServiceUtil.getQuery("products", dataMap.get("id").toString(), statement);
                            if(resultSet.isBeforeFirst()){
                                resultSet.next();
                                //Authenticate
                                if(resultSet.getString("name").equals(dataMap.get("name").toString()) &&
                                    resultSet.getString("description").equals(dataMap.get("description").toString()) &&
                                    resultSet.getString("price").equals(dataMap.get("price").toString()) &&
                                    resultSet.getString("quantity").equals(dataMap.get("quantity").toString())
                                ){
                                    makeResponse(responseMap, dataMap.get("id").toString(), statement);
                                    String command = String.format("DELETE FROM products WHERE id = %s;", dataMap.get("id").toString());
                                    statement.execute(command);
                                } else{
                                    //Authetication failed
                                    responseMap.put("rcode", "401");
                                }
                            } else{
                                //Product does not exist
                                responseMap.put("rcode", "404");
                            }
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

    static class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");

            // JDBC connection parameters for save.db database
            String saveUrl = "jdbc:sqlite:compiled/ProductService/save.db";
            Connection saveConnection = null;
            ResultSet resultSet = null;

            try {
                // Connect to save.db database (SQLite)
                saveConnection = DriverManager.getConnection(saveUrl);
                PreparedStatement createStatement = saveConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS products (\n"
                + "	id integer PRIMARY KEY,\n"
                + "	name varchar(255),\n"
                + "	description varchar(255),\n"
                + "	price float,\n"
                + "	quantity integer\n"
                + ");");
                createStatement.execute();
                createStatement.close();

                // Retrieve data from user table in source database
                transferData(connection, saveConnection);

                System.out.println("Data saved successfully, Exiting.");
                responseMap.put("rcode", 200);
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                // Close connections
                try {
                    if (resultSet != null) resultSet.close();
                    if (saveConnection != null) saveConnection.close();
                    statement.close();
                    connection.close();
                } catch (SQLException e) {
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
            String saveUrl = "jdbc:sqlite:compiled/ProductService/save.db";
            Connection saveConnection = null;

            try {
                // Connect to save.db database (SQLite)
                saveConnection = DriverManager.getConnection(saveUrl);
                PreparedStatement createStatement = saveConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS products (\n"
                + "	id integer PRIMARY KEY,\n"
                + "	name varchar(255),\n"
                + "	description varchar(255),\n"
                + "	price float,\n"
                + "	quantity integer\n"
                + ");");
                createStatement.execute();
                createStatement.close();

                // Retrieve data from user table in source database
                transferData(saveConnection, connection);

                System.out.println("Data restored successfully, Exiting.");
                responseMap.put("rcode", 200);
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                // Close connections
                try {
                    if (saveConnection != null) saveConnection.close();
                } catch (SQLException e) {
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
            ResultSet result = ServiceUtil.getQuery("products", params, statement);

            //Check if product is found
            if (!result.isBeforeFirst() ) {    
                responseMap.put("rcode", "404"); 
            } else{ 
                //Make a response
                responseMap.put("rcode", "200");
                result.next();   
                responseMap.put("id", Integer.parseInt(params));
                responseMap.put("name", result.getString("name"));
                responseMap.put("description", result.getString("description"));
                responseMap.put("price", result.getFloat("price"));
                responseMap.put("quantity", result.getInt("quantity"));
            }
    }

    
    
    /** 
     * @param srcConnection
     * @param dstConnection
     * @throws SQLException
     */
    public static void transferData(Connection srcConnection ,Connection dstConnection) throws SQLException {
        PreparedStatement selectStatement = srcConnection.prepareStatement("SELECT * FROM products");
        ResultSet resultSet = selectStatement.executeQuery();
        selectStatement.close();

        // Insert data into user table in save.db database
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String name = resultSet.getString("name");
            String description = resultSet.getString("description");
            float price = resultSet.getFloat("price");
            int quantity = resultSet.getInt("quantity");


            // Insert data into save.db database (SQLite)
            PreparedStatement insertStatement = dstConnection.prepareStatement("INSERT INTO user (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)");
            insertStatement.setInt(1, id);
            insertStatement.setString(2, name);
            insertStatement.setString(3, description);
            insertStatement.setFloat(4, price);
            insertStatement.setInt(5, quantity);
            insertStatement.executeUpdate();
            insertStatement.close();
        }

        resultSet.close();
    }

}

