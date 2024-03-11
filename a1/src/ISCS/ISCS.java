import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import src.lib.ServiceUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ISCS {
    static JSONObject jsonObject = new JSONObject();

    static List<String> userIPs = new ArrayList<>();
    static List<Integer> userPorts = new ArrayList<>(); // List of service URLs
    static int currentUserServiceIndex = 0;

    static List<String> productIPs = new ArrayList<>();
    static List<Integer> productPorts = new ArrayList<>(); // List of service URLs
    static int currentProductServiceIndex = 0;

    
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

        //Fill the list of user services
        JSONArray userPortsConfig = jsonObject.getJSONArray("UserService");
        for (int i = 0; i < userPortsConfig.length(); i++) {
            userPorts.add(userPortsConfig.getJSONObject(i).getInt("port"));
            userIPs.add(userPortsConfig.getJSONObject(i).getString("ip"));
        }

        //Fill the list of product services
        JSONArray productPortsConfig = jsonObject.getJSONArray("ProductService");
        for (int i = 0; i < productPortsConfig.length(); i++) {
            productPorts.add(productPortsConfig.getJSONObject(i).getInt("port"));
            productIPs.add(productPortsConfig.getJSONObject(i).getString("ip"));
        }

        int port = jsonObject.getJSONObject("InterServiceCommunication").getInt("port");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up context for /user request
        server.createContext("/user", new UserHandler());
        // Set up context for /product request
        server.createContext("/product", new ProductHandler());

        server.createContext("/shutdown", new ShutdownHandler());
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

            String userIP = userIPs.get(currentUserServiceIndex);
            int userPort = userPorts.get(currentUserServiceIndex);
            String userServiceUrl = userIP.concat(":").concat(String.valueOf(userPort)).concat("/user");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            String clientUrl = exchange.getRequestURI().toString();
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for hamid");
                    System.out.println("index=");
                    int index = clientUrl.indexOf("user/purchased");
                    System.out.println("index=" + String.valueOf(index));
                    String params = null;
                    String url = null;
                    if(index != -1){
                        System.out.println("It is a GET request for purchased");
                        index += "user/purchased".length();
                        params = clientUrl.substring(index);
                        url = userIP.concat(":").concat(String.valueOf(userPort)).concat("/purchased").concat(params);
                    } else{
                        index = clientUrl.indexOf("user") + "user".length();
                        params = clientUrl.substring(index);
                        url = userServiceUrl.concat(params);
                    }
                    responseMap = ServiceUtil.sendGetRequest(url);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ServiceUtil.sendResponse(exchange, responseMap);
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    if(clientUrl.equals("/user")){
                        responseMap = ServiceUtil.sendPostRequest(userServiceUrl, ServiceUtil.getRequestBody(exchange));
                    } else{
                        responseMap = ServiceUtil.sendPostRequest(userIP.concat(":").concat(String.valueOf(userPort)).concat("/purchased"), ServiceUtil.getRequestBody(exchange));
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ServiceUtil.sendResponse(exchange, responseMap);
                    throw new RuntimeException(e);
                }
            }
            ServiceUtil.sendResponse(exchange, responseMap);


        }

    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            ServiceUtil.printClientInfo(exchange);

            // Handle POST request for /test
            String productIP = productIPs.get(currentProductServiceIndex);
            int productPort = productPorts.get(currentProductServiceIndex);
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
    
    static class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JSONObject response = new JSONObject();
            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject command = new JSONObject();
                command.put("command", "shutdown");
                String shutdownCommand = command.toString();
                
                String userIP = jsonObject.getJSONObject("UserService").get("ip").toString();
                int userPort = jsonObject.getJSONObject("UserService").getInt("port");

                String productIP = jsonObject.getJSONObject("ProductService").get("ip").toString();
                int productPort = jsonObject.getJSONObject("ProductService").getInt("port");

                // URLs for UserService and ProductService shutdown endpoints
                String userServiceUrl = userIP + ":" + userPort + "/shutdown";
                String productServiceUrl = productIP + ":" + productPort + "/shutdown";
    
                try {
                    // Forwarding the shutdown command to UserService
                    JSONObject userResponse = ServiceUtil.sendPostRequest(userServiceUrl, shutdownCommand);
    
                    // Forwarding the shutdown command to ProductService
                    JSONObject productResponse = ServiceUtil.sendPostRequest(productServiceUrl, shutdownCommand);
    
                    // Constructing response for the ISCS shutdown handler
                    if(userResponse.getInt("rcode") == 200 && productResponse.getInt("rcode") == 200) response.put("rcode", 200);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    response.put("error", "Failed to forward shutdown command");
                    response.put("rcode", 500);
                }
    
                ServiceUtil.sendResponse(exchange, response);
                System.exit(0);
            }
        }
    }
    
    static class RestartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JSONObject response = new JSONObject();
            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject command = new JSONObject();
                command.put("command", "restart");
                String restartCommand = command.toString();

                String userIP = jsonObject.getJSONObject("UserService").get("ip").toString();
                int userPort = jsonObject.getJSONObject("UserService").getInt("port");

                String productIP = jsonObject.getJSONObject("ProductService").get("ip").toString();
                int productPort = jsonObject.getJSONObject("ProductService").getInt("port");

                // URLs for UserService and ProductService restart endpoints
                String userServiceUrl = userIP + ":" + userPort + "/restart";
                String productServiceUrl = productIP + ":" + productPort + "/restart";
    
                try {
                    // Forwarding the restart command to UserService
                    JSONObject userResponse = ServiceUtil.sendPostRequest(userServiceUrl, restartCommand);
    
                    // Forwarding the restart command to ProductService
                    JSONObject productResponse = ServiceUtil.sendPostRequest(productServiceUrl, restartCommand);
    
                    // Constructing response for the ISCS restart handler
                    response.put("command", "restart");
                    response.put("status", "commands forwarded");
                    response.put("userServiceResponse", userResponse);
                    response.put("productServiceResponse", productResponse);
                    response.put("rcode", 200);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    response.put("error", "Failed to forward restart command");
                    response.put("rcode", 500);
                }
    
                ServiceUtil.sendResponse(exchange, response);
            }
        }
    }
}
