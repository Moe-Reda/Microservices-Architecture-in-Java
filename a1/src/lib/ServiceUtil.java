package src.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;

public class ServiceUtil {
    
    /** 
     * @param exchange
     * @param responseMap
     * @throws IOException
     */
    public static void sendResponse(HttpExchange exchange, JSONObject responseMap) throws IOException {
        int rcode = responseMap.getInt("rcode");
        responseMap.remove("rcode");
        exchange.sendResponseHeaders(rcode, responseMap.toString().length()); //Change for final version
        OutputStream os = exchange.getResponseBody();
        os.write(responseMap.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    
    /** 
     * @param url
     * @return JSONObject
     * @throws Exception
     */
    public static JSONObject sendGetRequest(String url) throws Exception {
        URI apiUri = new URI("http://".concat(url));
        URL apiUrl = apiUri.toURL();
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        JSONObject responseMap = getResponse(connection, responseCode);
        responseMap.put("rcode", responseCode);

        return responseMap;
    }


    
    /** 
     * @param url
     * @param postData
     * @return JSONObject
     * @throws Exception
     */
    public static JSONObject sendPostRequest(String url, String postData) throws Exception {
        URI apiUri = new URI("http://".concat(url));
        URL apiUrl = apiUri.toURL();
        System.out.println(apiUrl.toString());
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = postData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        JSONObject responseMap = getResponse(connection, responseCode);
        responseMap.put("rcode", responseCode);

        return responseMap;
    }


    
    /** 
     * @param connection
     * @param rcode
     * @return JSONObject
     * @throws IOException
     */
    public static JSONObject getResponse(HttpURLConnection connection, int rcode) throws IOException {
        BufferedReader in;
        if(rcode == 200){
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        } else{
            in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        in.close();
        System.out.println("The response code: " + String.valueOf(rcode));
        System.out.println("The reponse body: " + response.toString());
        return bodyToMap(response.toString());
    }

    
    /** 
     * @param exchange
     * @return String
     * @throws IOException
     */
    public static String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }

    
    /** 
     * @param exchange
     * @throws IOException
     */
    public static void printClientInfo(HttpExchange exchange) throws IOException {
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

    
    /** 
     * @param data
     * @return JSONObject
     */
    public static JSONObject bodyToMap(String data) {
        return new JSONObject(data);
    }

    
    /** 
     * @param database
     * @param field
     * @param value
     * @param id
     * @param statement
     * @throws SQLException
     */
    public static void updateDB(String database, String field, String value, String id, Statement statement) throws SQLException {
        String command;
        command = String.format("UPDATE " + database + " SET %s = \'%s\' WHERE id = %s", field, value, id);
        statement.execute(command);
    }

    
    /** 
     * @param database
     * @param params
     * @param statement
     * @return ResultSet
     * @throws SQLException
     */
    public static ResultSet getQuery(String database, String params, Statement statement) throws SQLException {
        return statement.executeQuery("SELECT * FROM " + database + " WHERE id = " + params + ";");
    }

    
    /** 
     * @param str
     * @return boolean
     */
    public static boolean isNumeric(String str) {
        try {
            Double n = Double.parseDouble(str);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    
    /** 
     * @param json
     * @return boolean
     */
    public static boolean isJSON(String json) {
        try {
            new JSONObject(json);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    
    /** 
     * @param data
     * @return boolean
     */
    public static boolean isValidUser(JSONObject data){
        // Check if all required fields are present
        if (!data.has("command") ||
            !data.has("id")) {
            return false;
        }

        if(!data.getString("command").equals("update") && (
            !data.has("username") ||
            !data.has("email") ||
            !data.has("password"))
        ){
            return false;
        }
        
        // Check if any required field is blank or the wrong type
        if (data.getString("command").isEmpty() ||
            !Integer.class.isInstance(data.get("id"))) {
            return false;
        }

        if(data.has("username")){
            if(!String.class.isInstance(data.get("username"))){
                return false;
            } else if(data.getString("username").isEmpty()){
                return false;
            }
        }

        if(data.has("email")){
            if(!String.class.isInstance(data.get("email"))){
                return false;
            } else if(data.getString("email").isEmpty()){
                return false;
            }
        }

        if(data.has("password")){
            if(!String.class.isInstance(data.get("password"))){
                return false;
            } else if(data.getString("password").isEmpty()){
                return false;
            }
        }
        
        // Check for extra fields
        if (data.length() > 5) {
            return false;
        }
        
        // No issues found, JSON object is valid
        return true;
    }

    
    /** 
     * @param data
     * @return boolean
     */
    public static boolean isValidProduct(JSONObject data) {
        // Check if all required fields are present
        if (!data.has("command") ||
            !data.has("id")) {
            return false;
        }

        if(!data.getString("command").equals("update") && (
            !data.has("name") ||
            (!data.has("description") && (!data.getString("command").equals("delete")))  ||
            !data.has("price") ||
            !data.has("quantity"))
        ){
            return false;
        }
        
        // Check if any required field is blank or the wrong type
        if (data.getString("command").isEmpty() ||
            !Integer.class.isInstance(data.get("id"))) {
            return false;
        }

        if(data.has("name")){
            if(!String.class.isInstance(data.get("name"))){
                return false;
            } else if(data.getString("name").isEmpty()){
                return false;
            }
        }

        if(data.has("description")){
            if(!String.class.isInstance(data.get("description"))){
                return false;
            } else if(data.getString("description").isEmpty()){
                return false;
            }
        }

        if(data.has("price")){
            if(!isNumeric(data.get("price").toString())){
                return false;
            } else if(data.getDouble("price") < 0){
                return false;
            }
        }

        if(data.has("quantity")){
            if(!Integer.class.isInstance(data.get("quantity"))){
                return false;
            } else if(data.getInt("quantity") < 0){
                return false;
            }
        }
        
        // Check for extra fields
        if (data.length() > 6) {
            return false;
        }
        
        // No issues found, JSON object is valid
        return true;
    }

    
    /** 
     * @param dataMap
     * @return boolean
     */
    public static boolean isValidOrder(JSONObject dataMap) {
        // Check if all required fields are present
        if (!dataMap.has("command") || !dataMap.has("product_id") ||
            !dataMap.has("user_id") || !dataMap.has("quantity")) {
            return false;
        }

        // Check if any field is blank
        String command = dataMap.getString("command");
        int productId, userId, quantity;

        try {
            productId = dataMap.getInt("product_id");
            userId = dataMap.getInt("user_id");
            quantity = dataMap.getInt("quantity");
        } catch (Exception e) {
            return false; // If any field is not an integer, return false
        }

        return !command.isEmpty() && quantity > 0;
    }
}