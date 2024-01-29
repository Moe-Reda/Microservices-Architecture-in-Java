import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import docs.Util;

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
            Util.printClientInfo(exchange);

            // Handle POST request for /test
            String userIP = jsonObject.getJSONObject("UserService").get("ip").toString();
            int userPort = jsonObject.getJSONObject("UserService").getInt("port");
            String UserServiceUrl = userIP.concat(":").concat(String.valueOf(userPort)).concat("/user");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for user");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("user") + "user".length();
                    String params = clientUrl.substring(index);
                    String url = UserServiceUrl.concat(params);
                    responseMap = Util.sendGetRequest(url);
                } catch (Exception e) {
                    Util.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for user");
                    responseMap = Util.sendPostRequest(UserServiceUrl, Util.getRequestBody(exchange));
                } catch (Exception e) {
                    Util.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            Util.sendResponse(exchange, responseMap);


        }

    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //Print client info for debugging
            Util.printClientInfo(exchange);

            // Handle POST request for /test
            String productIP = jsonObject.getJSONObject("ProductService").get("ip").toString();
            int productPort = jsonObject.getJSONObject("ProductService").getInt("port");
            String productServiceUrl = productIP.concat(":").concat(String.valueOf(productPort)).concat("/user");
            JSONObject responseMap = new JSONObject();
            responseMap.put("rcode", "500");
            if ("GET".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a GET request for product");
                    String clientUrl = exchange.getRequestURI().toString();
                    int index = clientUrl.indexOf("product") + "product".length();
                    String params = clientUrl.substring(index);
                    String url = productServiceUrl.concat(params);
                    responseMap = Util.sendGetRequest(url);
                } catch (Exception e) {
                    Util.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if("POST".equals(exchange.getRequestMethod())){
                try {
                    System.out.println("It is a POST request for product");
                    responseMap = Util.sendPostRequest(productServiceUrl, Util.getRequestBody(exchange));
                } catch (Exception e) {
                    Util.sendResponse(exchange, responseMap);
                    System.out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            Util.sendResponse(exchange, responseMap);

        }
    }
}

