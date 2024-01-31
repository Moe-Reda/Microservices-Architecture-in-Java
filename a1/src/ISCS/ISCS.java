import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import src.lib.ServiceUtil;

import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;

public class ISCS {
    static JSONObject jsonObject = new JSONObject();

    
    /** 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
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
        jsonObject = new JSONObject(jsonString);

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
            ServiceUtil.printClientInfo(exchange);

            System.out.println("Getting user service url");
            String userIP = jsonObject.getJSONObject("UserService").get("ip").toString();
            System.out.println("User IP: " + userIP);
            int userPort = jsonObject.getJSONObject("UserService").getInt("port");
            System.out.println("User port: " + String.valueOf(userPort));
            String userServiceUrl = userIP.concat(":").concat(String.valueOf(userPort)).concat("/user");
            System.out.println("User URL: " + userServiceUrl);
            System.out.println("Making Response map");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);
                    String url = userServiceUrl.concat(params);
                    responseMap = ServiceUtil.sendGetRequest(url);
                    System.out.println("The response code in the map before responding is: " + String.valueOf(responseMap.getInt("rcode")));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ServiceUtil.sendResponse(exchange, responseMap);
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    responseMap = ServiceUtil.sendPostRequest(userServiceUrl, ServiceUtil.getRequestBody(exchange));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ServiceUtil.sendResponse(exchange, responseMap);
                    throw new RuntimeException(e);
                }
            }
            System.out.println("Sending a response back");
            ServiceUtil.sendResponse(exchange, responseMap);


        }

    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            ServiceUtil.printClientInfo(exchange);

            // Handle POST request for /test
            String productIP = jsonObject.getJSONObject("ProductService").get("ip").toString();
            int productPort = jsonObject.getJSONObject("ProductService").getInt("port");
            String productServiceUrl = productIP.concat(":").concat(String.valueOf(productPort)).concat("/product");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for product");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    String url = productServiceUrl.concat(params);
                    responseMap = ServiceUtil.sendGetRequest(url);
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for product");
                    responseMap = ServiceUtil.sendPostRequest(productServiceUrl, ServiceUtil.getRequestBody(exchange));
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

