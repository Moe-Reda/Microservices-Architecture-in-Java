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

public class ISCS {
    static JSONObject jsonObject = new JSONObject();

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

        int port = jsonObject.getJSONObject("InterServiceCommunication").getInt("port");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up context for /user request
        server.createContext("/user", new UserHandler());
        // Set up context for /product request
        server.createContext("/product", new ProductHandler());


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            printClientInfo(exchange);

            // Handle POST request for /test
            String userIP = jsonObject.getJSONObject("UserService").get("ip").toString();
            int userPort = jsonObject.getJSONObject("UserService").getInt("port");
            String UserServiceUrl = userIP.concat(":").concat(String.valueOf(userPort)).concat("/user");
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);
                    String url = UserServiceUrl.concat(params);
                    responseMap = sendGetRequest(url);
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    responseMap = sendPostRequest(UserServiceUrl, getRequestBody(exchange));
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
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
            String productIP = jsonObject.getJSONObject("ProductService").get("ip").toString();
            int productPort = jsonObject.getJSONObject("ProductService").getInt("port");
            String productServiceUrl = productIP.concat(":").concat(String.valueOf(productPort)).concat("/user");
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for product");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    String url = productServiceUrl.concat(params);
                    responseMap = sendGetRequest(url);
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for product");
                    responseMap = sendPostRequest(productServiceUrl, getRequestBody(exchange));
                } catch (Exception e) {
                    sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            sendResponse(exchange, responseMap);

        }
    }

    private static void sendResponse(HttpExchange exchange, Map<String, String> responseMap) throws IOException {
        int rcode = Integer.parseInt(responseMap.get("rcode"));
        responseMap.remove("rcode");
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

