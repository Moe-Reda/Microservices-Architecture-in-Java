package docs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;

public class ServiceUtil {
    public static void sendResponse(HttpExchange exchange, JSONObject responseMap) throws IOException {
        System.out.println("The response code sent back is: is: " + responseMap.get("rcode"));
        int rcode = responseMap.getInt("rcode");
        responseMap.remove("rcode");
        System.out.println("The response: " + responseMap.toString());
        exchange.sendResponseHeaders(rcode, responseMap.toString().length()); //Change for final version
        OutputStream os = exchange.getResponseBody();
        os.write(responseMap.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    public static JSONObject sendGetRequest(String url) throws Exception {
        URI apiUri = new URI("http://".concat(url));
        URL apiUrl = apiUri.toURL();
        System.out.println("Connecting to: " + url);
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        System.out.println("The response code received is: " + String.valueOf(responseCode));
        JSONObject responseMap = getResponse(connection);
        System.out.println("The response is received");
        responseMap.put("rcode", responseCode);
        System.out.println("The response code added to map");

        return responseMap;
    }

    public static JSONObject sendPostRequest(String url, String postData) throws Exception {
        URI apiUri = new URI("http://".concat(url));
        URL apiUrl = apiUri.toURL();
        System.out.println("Connecting to: " + url);
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = postData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        JSONObject responseMap = getResponse(connection);
        responseMap.put("rcode", responseCode);

        return responseMap;
    }

    public static JSONObject getResponse(HttpURLConnection connection) throws IOException {
        System.out.println("There is an issue here");
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        System.out.println("or here");
        String inputLine;
        StringBuilder response = new StringBuilder();

        System.out.println("Reading response");

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        System.out.println("Response read");

        in.close();
        return bodyToMap(response.toString());
    }

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

    public static JSONObject bodyToMap(String data) {
        String[] intValues = {"id", "quantity", "price", "product_id", "user_id"};
        System.out.println("Splitting response into key value apirs");
        String[] keyValueList = data.replace(" ", "")
                                    .replace("}", "")
                                    .replace("{", "")
                                    .replace("\"", "")
                                    .split(",");
        JSONObject map = new JSONObject();
        System.out.println("Building JSON object");
        for(String keyValue : keyValueList){
            System.out.println(keyValue);
            String[] keyValuePair = keyValue.replace("\'", "").split(":");
            if(Arrays.asList(intValues).contains(keyValuePair[0])){
                map.put(keyValuePair[0], Integer.parseInt(keyValuePair[1]));
            } else{
                map.put(keyValuePair[0], keyValuePair[1]);
            }
        }
        return map;
    }

    public static void updateDB(String field, String value, String id, Statement statement) throws SQLException {
        String command;
        command = String.format("UPDATE users SET %s = \'%s\' WHERE id = %s", field, value, id);
        statement.execute(command);
    }

    public static void makeResponse(JSONObject responseMap, String params, Statement statement) throws SQLException, NoSuchAlgorithmException {
            ResultSet result = getQuery(params, statement);

            //Check if user is found
            if (!result.isBeforeFirst() ) {    
                responseMap.put("rcode", "404"); 
            } else{ 
                //Make a response
                responseMap.put("rcode", "200");
                result.next();   
                responseMap.put("id", params);
                responseMap.put("username", result.getString("username"));
                responseMap.put("email", result.getString("email"));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(result.getString("password").getBytes(StandardCharsets.UTF_8));
                responseMap.put("password", encodedhash.toString());
            }
        }

    public static ResultSet getQuery(String params, Statement statement) throws SQLException {
        return statement.executeQuery("SELECT * FROM users WHERE id = " + params + ";");
    }
}