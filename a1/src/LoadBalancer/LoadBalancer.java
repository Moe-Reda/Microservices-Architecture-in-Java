import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import src.lib.ServiceUtil;



public class LoadBalancer {
    private static final int LB_PORT = 8000; // Port for the load balancer
    private static List<Integer> orderServicePorts = new ArrayList<>();
    private static int currentIndex = 0;

    public static void main(String[] args) throws IOException {
        // Load configuration
        loadConfig(args[0]);

        // Initialize the LRU Cache

        HttpServer server = HttpServer.create(new InetSocketAddress(LB_PORT), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String requestMethod = exchange.getRequestMethod();
                String path = exchange.getRequestURI().toString();
                JSONObject response = null;


                // Forward the request for GET and other methods
                int port = orderServicePorts.get(currentIndex);
                currentIndex = (currentIndex + 1) % orderServicePorts.size();
                String targetUrl = "127.0.0.1:" + port + path;

                
                try {
                    if ("GET".equals(requestMethod)){
                        response = ServiceUtil.sendGetRequest(targetUrl);
                    }
                    else{
                        response = ServiceUtil.sendPostRequest(targetUrl, ServiceUtil.getRequestBody(exchange));
                    }
                } catch (Exception e) {
                    response = new JSONObject();
                    response.put("rcode", 500);
                    e.printStackTrace();
                }


                // Send response back to client
                ServiceUtil.sendResponse(exchange, response);
            }
        });

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Load Balancer started on port " + LB_PORT);
    }

    private static void loadConfig(String path) throws FileNotFoundException {
        String jsonString = new BufferedReader(new FileReader(path))
                .lines().reduce("", String::concat);

        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray ports = jsonObject.getJSONArray("OrderService");
        for (int i = 0; i < ports.length(); i++) {
            orderServicePorts.add(ports.getJSONObject(i).getInt("port"));
        }
    }
}
