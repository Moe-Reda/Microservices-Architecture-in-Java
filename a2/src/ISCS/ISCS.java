import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import src.lib.ServiceUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ISCS {
    static JSONObject jsonObject = new JSONObject();

    static List<String> userIPs = new ArrayList<>();
    static List<Integer> userPorts = new ArrayList<>(); // List of service URLs
    static volatile AtomicInteger currentUserServiceIndex = new AtomicInteger(0);

    static List<String> productIPs = new ArrayList<>();
    static List<Integer> productPorts = new ArrayList<>(); // List of service URLs
    static volatile AtomicInteger currentProductServiceIndex = new AtomicInteger(0);

    static int CACHESIZE = 500000;
    static Map<Integer, String> userCache = new ConcurrentHashMap<Integer, String>(CACHESIZE);
    static Map<Integer, String> productCache = new ConcurrentHashMap<Integer, String>(CACHESIZE);

    
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

        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            //ServiceUtil.printClientInfo(exchange);

            String userIP = userIPs.get(currentUserServiceIndex.get() % userPorts.size());
            int userPort = userPorts.get(currentUserServiceIndex.get() % userPorts.size());
            currentUserServiceIndex.getAndIncrement();
            String userServiceUrl = userIP.concat(":").concat(String.valueOf(userPort)).concat("/user");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            String clientUrl = exchange.getRequestURI().toString();
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    int index = clientUrl.indexOf("user/purchased");
                    String params = null;
                    String url = null;
                    if(index != -1){
                        index += "user/purchased".length();
                        params = clientUrl.substring(index);
                        url = userIP.concat(":").concat(String.valueOf(userPort)).concat("/purchased").concat(params);
                        responseMap = ServiceUtil.sendGetRequest(url);
                    } else{
                        index = clientUrl.indexOf("user") + "user".length();
                        params = clientUrl.substring(index);
                        if(userCache.containsKey(Integer.parseInt(params.substring(1)))){
                            responseMap = ServiceUtil.bodyToMap(userCache.get(Integer.parseInt(params.substring(1))));
                            responseMap.put("rcode", "200");
                        } else{
                            url = userServiceUrl.concat(params);
                            responseMap = ServiceUtil.sendGetRequest(url);
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ServiceUtil.sendResponse(exchange, responseMap);
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    if(clientUrl.equals("/user")){
                        String body = ServiceUtil.getRequestBody(exchange);
                        JSONObject bodyJson = ServiceUtil.bodyToMap(body);

                    
                        if(bodyJson.has("command") && bodyJson.has("id")){
                            //Delete from cache if command is delete
                            if(bodyJson.getString("command").equals("delete")){
                                userCache.remove(bodyJson.getInt("id"));
                            } 
                            
                            //Send 409 if id already in cache
                            else if(bodyJson.getString("command").equals("create")){
                                if(userCache.containsKey(bodyJson.getInt("id"))){
                                    responseMap.put("rcode", "409");
                                } else{
                                    bodyJson.remove("command");
                                    int id = bodyJson.getInt("id");
                                    userCache.put(id, bodyJson.toString());
                                }
                            }

                            //update cache on update
                            else if(bodyJson.getString("command").equals("update")){
                                if(ServiceUtil.isValidUser(bodyJson) && userCache.containsKey(bodyJson.getInt("id"))){
                                    JSONObject updatedUser = ServiceUtil.bodyToMap(userCache.get(bodyJson.getInt("id")));
                                    //Check if the username needs to be updated
                                    if(bodyJson.has("username")){
                                        updatedUser.put("username", bodyJson.getString("username"));
                                    }

                                    //Check if the email needs to be updated
                                    if(bodyJson.has("email")){
                                        updatedUser.put("email", bodyJson.getString("email"));
                                    }

                                    //Check if the password needs to be updated
                                    if(bodyJson.has("password")){
                                        updatedUser.put("password", bodyJson.getString("password"));
                                    }
                                    userCache.put(bodyJson.getInt("id"), updatedUser.toString());
                                } else if(!ServiceUtil.isValidUser(bodyJson)){
                                    responseMap.put("rcode", "400");
                                }
                            }
                        }


                        if(responseMap.getInt("rcode") == 500) responseMap = ServiceUtil.sendPostRequest(userServiceUrl, body);
                    } else{
                        responseMap = ServiceUtil.sendPostRequest(userIP.concat(":").concat(String.valueOf(userPort)).concat("/purchased"), ServiceUtil.getRequestBody(exchange));
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    ServiceUtil.sendResponse(exchange, responseMap);
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }


            if(responseMap.has("id")){
                userCache.put(responseMap.getInt("id"), responseMap.toString());
            }
            ServiceUtil.sendResponse(exchange, responseMap);


        }

    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            //ServiceUtil.printClientInfo(exchange);

            // Handle POST request for /test
            String productIP = productIPs.get(currentProductServiceIndex.get() % productPorts.size());
            int productPort = productPorts.get(currentProductServiceIndex.get() % productPorts.size());
            currentProductServiceIndex.getAndIncrement();
            String productServiceUrl = productIP.concat(":").concat(String.valueOf(productPort)).concat("/product");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    if(productCache.containsKey(Integer.parseInt(params.substring(1)))){
                            responseMap = ServiceUtil.bodyToMap(productCache.get(Integer.parseInt(params.substring(1))));
                    } else{
                        String url = productServiceUrl.concat(params);
                        responseMap = ServiceUtil.sendGetRequest(url);
                    }
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {

                    String body = ServiceUtil.getRequestBody(exchange);
                    JSONObject bodyJson = ServiceUtil.bodyToMap(body);

                
                    if(bodyJson.has("command") && bodyJson.has("id")){
                        //Delete from cache if command is delete
                        if(bodyJson.getString("command").equals("delete")){
                            productCache.remove(bodyJson.getInt("id"));
                        } 
                        
                        //Send 409 if id already in cache
                        else if(bodyJson.getString("command").equals("create")){
                            if(productCache.containsKey(bodyJson.getInt("id"))){
                                responseMap.put("rcode", "409");
                            } else{
                                bodyJson.remove("command");
                                int id = bodyJson.getInt("id");
                                productCache.put(id, bodyJson.toString());
                            }
                        }

                        //update cache on update
                        else if(bodyJson.getString("command").equals("update")){
                            if(ServiceUtil.isValidProduct(bodyJson) && userCache.containsKey(bodyJson.getInt("id"))){
                                JSONObject updatedUser = ServiceUtil.bodyToMap(productCache.get(bodyJson.getInt("id")));
                                //Check if the name needs to be updated
                                if(bodyJson.has("name")){
                                    updatedUser.put("name", bodyJson.getString("name"));
                                }

                                //Check if the description needs to be updated
                                if(bodyJson.has("description")){
                                    updatedUser.put("description", bodyJson.getString("description"));
                                }

                                //Check if the price needs to be updated
                                if(bodyJson.has("price")){
                                    updatedUser.put("price", bodyJson.getDouble("price"));
                                }

                                //Check if the quantity needs to be updated
                                if(bodyJson.has("quantity")){
                                    updatedUser.put("quantity", bodyJson.getInt("quantity"));
                                }
                                productCache.put(bodyJson.getInt("id"), updatedUser.toString());
                            } else if(!ServiceUtil.isValidProduct(bodyJson)){
                                responseMap.put("rcode", "400");
                            }
                        }
                    }



                    if(responseMap.getInt("rcode") == 500) responseMap = ServiceUtil.sendPostRequest(productServiceUrl, body);
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            if(responseMap.has("id")){
                productCache.put(responseMap.getInt("id"), responseMap.toString());
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
