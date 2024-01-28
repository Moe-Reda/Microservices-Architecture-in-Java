import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderService {
    public static String iscsIp = "";
    public static int iscsPort = -1;

    public static void main(String[] args) throws IOException {
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

        iscsPort = jsonObject.getJSONObject("InterServiceCommunication").getInt("port");
        iscsIp = jsonObject.getJSONObject("InterServiceCommunication").get("ip").toString();

        int port = jsonObject.getJSONObject("OrderService").getInt("port");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up context for /order POST request
        server.createContext("/order", new OrderHandler());

        // Set up context for /user request
        server.createContext("/user", new UserHandler());
        // Set up context for /product request
        server.createContext("/product", new ProductHandler());


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info
            printClientInfo(exchange);


            // Handle POST request for /order
            if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    
                    //Get the ISCS URL
                    String iscsUserUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/user");
                    String data = getRequestBody(exchange);

                    //Create a map with the request body
                    Map<String, String> dataMap = bodyToMap(data);

                    //Get user data
                    Map<String, String> userResponseMap = sendGetRequest(iscsUserUrl.concat("/").concat(dataMap.get("user_id")));
                    int userRcode = Integer.parseInt(userResponseMap.get("rcode"));

                    //Get product data
                    Map<String, String> productResponseMap = sendGetRequest(iscsUserUrl.concat("/").concat(dataMap.get("product_id")));
                    int productRcode = Integer.parseInt(productResponseMap.get("rcode"));
                    int productQuantity = Integer.parseInt(productResponseMap.get("quantity"));
                    int orderQuantity = Integer.parseInt(dataMap.get("quantity"));

                    Map<String, String> responseMap = new HashMap<>();
                    
                    //Send POST request to order service if it is a valid request
                    if(userRcode == 200 && productRcode == 200 && productQuantity >= orderQuantity){
                        Map<String, String> orderMap = new HashMap<>();
                        orderMap.put("command", productResponseMap.get("update"));
                        orderMap.put("id", productResponseMap.get("product_id"));
                        int newProductQuantity = productQuantity - orderQuantity;
                        orderMap.put("quantity", String.valueOf(newProductQuantity));
                        responseMap = sendPostRequest(iscsUserUrl, orderMap.toString());
                    } else if(userRcode != 200 || productRcode != 200){
                        responseMap.put("rcode", "404");
                    } else{
                        responseMap.put("rcode", "401");
                    }

                    //Send a response back to client
                    Map<String, String> clientResponseMap = new HashMap<>();
                    clientResponseMap.put("id", UUID.randomUUID().toString());
                    clientResponseMap.put("product_id", dataMap.get("product_id"));
                    clientResponseMap.put("user_id", dataMap.get("user_id"));
                    clientResponseMap.put("quantity", dataMap.get("quantity"));
                    if(responseMap.get("rcode").equals("200")){
                        clientResponseMap.put("status message", "Success");
                        clientResponseMap.put("rcode", "200");
                    } else if(responseMap.get("rcode").equals("404")){
                        clientResponseMap.put("status message", "Invalid request");
                        clientResponseMap.put("rcode", "404");
                    } else{
                        clientResponseMap.put("status message", "Exceeded quantity limit");
                        clientResponseMap.put("rcode", "401");
                    }

                    sendResponse(exchange, clientResponseMap);


                } catch (Exception e) {
                    //Handle possible errors here
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }
            //Potentially handle non-POST requests here
        }


    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            printClientInfo(exchange);

            // Handle POST request for /test
            String iscsUserUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/user");
            Map<String, String> responseMap = new HashMap<>();
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);
                    String url = iscsUserUrl.concat(params);
                    responseMap = sendGetRequest(url);
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    responseMap = sendPostRequest(iscsUserUrl, getRequestBody(exchange));
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            if(responseMap.get("rcode") == null){
                responseMap.put("rcode", "500");
            }

            sendResponse(exchange, responseMap);


        }

    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            printClientInfo(exchange);

            // Handle POST request for /test
            String iscsproductUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/product");
            Map<String, String> responseMap = new HashMap<>();
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for product");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    String url = iscsproductUrl.concat(params);
                    responseMap = sendGetRequest(url);
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for product");
                    responseMap = sendPostRequest(iscsproductUrl, getRequestBody(exchange));
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            if(responseMap.get("rcode") == null){
                responseMap.put("rcode", "500");
            }

            sendResponse(exchange, responseMap);


        }
    }

    private static void sendResponse(HttpExchange exchange, Map<String, String> responseMap) throws IOException {
        System.out.println("The response code is: " + responseMap.get("rcode"));
        int rcode = Integer.parseInt(responseMap.get("rcode"));
        responseMap.remove("rcode");
        System.out.println("The response is: " + responseMap.toString());
        exchange.sendResponseHeaders(rcode, responseMap.toString().length());
        OutputStream os = exchange.getResponseBody();
        os.write(responseMap.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    private static Map<String, String> sendGetRequest(String url) throws Exception {
        URI apiUri = new URI("http://".concat(url));
        URL apiUrl = apiUri.toURL();
        System.out.println("Connecting to: " + url);
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        Map<String, String> responseMap = getResponse(connection);
        responseMap.put("rcode", String.valueOf(responseCode));

        return responseMap;
    }

    private static Map<String, String> sendPostRequest(String url, String postData) throws Exception {
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
        Map<String, String> responseMap = getResponse(connection);
        responseMap.put("rcode", String.valueOf(responseCode));

        return responseMap;
    }

    private static Map<String, String> getResponse(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        in.close();
        return bodyToMap(response.toString());
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

        System.out.println("Request Body: " + getRequestBody(exchange));
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
