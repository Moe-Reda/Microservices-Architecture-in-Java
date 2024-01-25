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
import java.util.List;
import java.util.Map;

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
        iscsIp = (String) jsonObject.getJSONObject("InterServiceCommunication").get("ip");

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

    //Template for POST handler
    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle POST request for /test
            if ("POST".equals(exchange.getRequestMethod())) {
                String response = "Lecture foobar foobar Received POST request for /test";


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
                //    System.out.println(header.getKey() + ": " + header.getValue().getFirst());
                //}

                System.out.println("Request Body: "+ getRequestBody(exchange));

                sendResponse(exchange, response);

            } else {
                // Send a 405 Method Not Allowed response for non-POST requests
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
            }
        }

    }

    //Template for GET handler
    static class Test2Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle GET request for /test2
            // TODO let's do this in class.
            if ("GET".equals(exchange.getRequestMethod())) {
                String requestMethod = exchange.getRequestMethod();
                String clientAddress = exchange.getRemoteAddress().getAddress().toString();
                String requestURI = exchange.getRequestURI().toString();

                System.out.println("Request method: " + requestMethod);
                System.out.println("Client Address: " + clientAddress);
                System.out.println("Request URI: " + requestURI);


                String response = "Received GET for /test2 lecture foo W.";
                sendResponse(exchange, response);




            } else {
                exchange.sendResponseHeaders(405,0);
                exchange.close();
            }

        }
    }

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle POST request for /test
            String response = "Lecture foobar foobar Received request for /user";


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

            sendResponse(exchange, response);
        }

    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle POST request for /test
            String response = "Lecture foobar foobar Received request for /user";
            String iscsUserUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/user");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);
                    String url = iscsUserUrl.concat("/").concat(params);
                    response = sendGetRequest(url);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    response = sendPostRequest(iscsUserUrl, exchange.getRequestBody().toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }


            //Print client info
            printClientInfo(exchange);

            sendResponse(exchange, response);


        }

    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Lecture foobar foobar Received request for /product";

            String iscsUserUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/product");
            // Handle GET request for /product
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    String url = iscsUserUrl.concat("/").concat(params);
                    response = sendGetRequest(url);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            // Handle POST request for /product
            else if("POST".equals(exchange.getRequestMethod())){
                try {
                    response = sendPostRequest(iscsUserUrl, exchange.getRequestBody().toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            //Print client info
            printClientInfo(exchange);

            //Send a response to the client
            sendResponse(exchange, response);

        }
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    private static String sendGetRequest(String url) throws Exception {
        URI apiUri = new URI(url);
        URL apiUrl = apiUri.toURL();
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            return getResponse(connection);
        } else {
            System.out.println("GET request failed with response code: " + responseCode);
            return null;
        }
    }

    private static String sendPostRequest(String url, String postData) throws Exception {
        URI apiUri = new URI(url);
        URL apiUrl = apiUri.toURL();
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = postData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            return getResponse(connection);
        } else {
            System.out.println("POST request failed with response code: " + responseCode);
            return null;
        }
    }

    private static String getResponse(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        in.close();
        return response.toString();
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
}

