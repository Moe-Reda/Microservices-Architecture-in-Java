import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.UUID;

import docs.ServiceUtil;

public class OrderService {
    public static String iscsIp = "";
    public static int iscsPort = -1;

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
            ServiceUtil.printClientInfo(exchange);


            // Handle POST request for /order
            if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for order");
                    
                    //Get the ISCS URL
                    String iscsUserUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/user");
                    String iscsProductUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/product");
                    String data = ServiceUtil.getRequestBody(exchange);

                    //Create a map with the request body
                    JSONObject dataMap = ServiceUtil.bodyToMap(data);

                    //Get user data
                    JSONObject userResponseMap = ServiceUtil.sendGetRequest(iscsUserUrl.concat("/").concat(dataMap.get("user_id").toString()));
                    int userRcode = userResponseMap.getInt("rcode");

                    //Get product data
                    JSONObject productResponseMap = ServiceUtil.sendGetRequest(iscsProductUrl.concat("/").concat(dataMap.get("product_id").toString()));
                    int productRcode = productResponseMap.getInt("rcode");
                    int productQuantity = productResponseMap.getInt("quantity");
                    int orderQuantity = dataMap.getInt("quantity");

                    JSONObject responseMap = new JSONObject();
                    
                    //Send POST request to order service if it is a valid request
                    if(userRcode == 200 && productRcode == 200 && productQuantity >= orderQuantity){
                        JSONObject orderMap = new JSONObject();
                        orderMap.put("command", "update");
                        orderMap.put("id", productResponseMap.getInt("id"));
                        int newProductQuantity = productQuantity - orderQuantity;
                        orderMap.put("quantity", newProductQuantity);
                        responseMap = ServiceUtil.sendPostRequest(iscsProductUrl, orderMap.toString());
                    } else if(userRcode != 200 || productRcode != 200){
                        responseMap.put("rcode", 404);
                    } else{
                        responseMap.put("rcode", 401);
                    }

                    //Send a response back to client
                    JSONObject clientResponseMap = new JSONObject();
                    clientResponseMap.put("id", UUID.randomUUID());
                    clientResponseMap.put("product_id", dataMap.getInt("product_id"));
                    clientResponseMap.put("user_id", dataMap.getInt("user_id"));
                    clientResponseMap.put("quantity", dataMap.getInt("quantity"));
                    if(responseMap.get("rcode").equals(200)){
                        clientResponseMap.put("status message", "Success");
                        clientResponseMap.put("rcode", 200);
                    } else if(responseMap.get("rcode").equals(404)){
                        clientResponseMap.put("status message", "Invalid request");
                        clientResponseMap.put("rcode", 404);
                    } else{
                        clientResponseMap.put("status message", "Exceeded quantity limit");
                        clientResponseMap.put("rcode", 401);
                    }

                    ServiceUtil.sendResponse(exchange, clientResponseMap);


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
            ServiceUtil.printClientInfo(exchange);

            // Handle POST request for /test
            String iscsUserUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/user");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", 500);
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);
                    String url = iscsUserUrl.concat(params);
                    responseMap = ServiceUtil.sendGetRequest(url);
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    responseMap = ServiceUtil.sendPostRequest(iscsUserUrl, ServiceUtil.getRequestBody(exchange));
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
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
            String iscsproductUrl = iscsIp.concat(":").concat(String.valueOf(iscsPort)).concat("/product");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", 500);
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for product");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    String url = iscsproductUrl.concat(params);
                    responseMap = ServiceUtil.sendGetRequest(url);
                } catch (Exception e) {
                    ServiceUtil.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for product");
                    responseMap = ServiceUtil.sendPostRequest(iscsproductUrl, ServiceUtil.getRequestBody(exchange));
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
