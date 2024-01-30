import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class ProductService {
    static JSONObject jsonObject = new JSONObject();
    static Connection connection = null;
    static Statement statement = null;

    public static void main(String[] args) throws IOException {
        // create a database connection
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:product.db");
            statement = connection.createStatement();
            // SQL statement for creating a new table
            String sql = "CREATE TABLE IF NOT EXISTS products (\n"
                    + " id integer PRIMARY KEY,\n"
                    + " name text NOT NULL,\n"
                    + " description text,\n"
                    + " price real NOT NULL,\n"
                    + " quantity integer NOT NULL\n"
                    + ");";
            statement.execute(sql);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        // Read config.json
        String path = "../../".concat(args[0]);
        String jsonString = "";
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString = jsonString.concat(line);
                jsonString = jsonString.replace(" ", "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Map representing config.json
        jsonObject = new JSONObject(jsonString);
        int port = jsonObject.getJSONObject("ProductService").getInt("port");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up context for /product request
        server.createContext("/product", new ProductHandler());

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Product Service started on port " + port);
    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "";
            String method = exchange.getRequestMethod();
            if ("POST".equals(method)) {
                // Handle POST request for product
                try {
                    String requestBody = getRequestBody(exchange);
                    JSONObject productData = new JSONObject(requestBody);
                    String command = productData.getString("command");

                    switch (command) {
                        case "create":
                            createProduct(productData);
                            response = "Product created successfully";
                            break;
                        case "update":
                            updateProduct(productData);
                            response = "Product updated successfully";
                            break;
                        case "delete":
                            deleteProduct(productData.getInt("id"));
                            response = "Product deleted successfully";
                            break;
                        default:
                            response = "Invalid command";
                            exchange.sendResponseHeaders(400, response.length());
                            break;
                    }
                } catch (Exception e) {
                    response = "Internal server error";
                    exchange.sendResponseHeaders(500, response.length());
                    e.printStackTrace();
                }
            } else if ("GET".equals(method)) {
                // Handle GET request for product
                try {
                    String productId = exchange.getRequestURI().getPath().split("/")[2];
                    response = getProduct(Integer.parseInt(productId));
                } catch (Exception e) {
                    response = "Internal server error";
                    exchange.sendResponseHeaders(500, response.length());
                    e.printStackTrace();
                }
            } else {
                // Method not supported
                response = "HTTP method not allowed";
                exchange.sendResponseHeaders(405, response.length());
            }
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }

    private static String getRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
    
    // Helper methods to interact with the database for CRUD operations.
    private static void createProduct(JSONObject productData) throws Exception {
        String sql = "INSERT INTO products (name, description, price, quantity) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, productData.getString("name"));
            pstmt.setString(2, productData.getString("description"));
            pstmt.setDouble(3, productData.getDouble("price"));
            pstmt.setInt(4, productData.getInt("quantity"));
            pstmt.executeUpdate();
        }
    }
    
    private static void updateProduct(JSONObject productData) throws Exception {
        String sql = "UPDATE products SET name = ?, description = ?, price = ?, quantity = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, productData.getString("name"));
            pstmt.setString(2, productData.getString("description"));
            pstmt.setDouble(3, productData.getDouble("price"));
            pstmt.setInt(4, productData.getInt("quantity"));
            pstmt.setInt(5, productData.getInt("id"));
            pstmt.executeUpdate();
        }
    }
    
    private static void deleteProduct(int productId) throws Exception {
        String sql = "DELETE FROM products WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            pstmt.executeUpdate();
        }
    }
    
    private static String getProduct(int productId) throws Exception {
        String sql = "SELECT * FROM products WHERE id = ?";
        JSONObject product = new JSONObject();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                product.put("id", rs.getInt("id"));
                product.put("name", rs.getString("name"));
                product.put("description", rs.getString("description"));
                product.put("price", rs.getDouble("price"));
                product.put("quantity", rs.getInt("quantity"));
            } else {
                product.put("message", "Product not found");
            }
        }
        return product.toString();
    }
    
    // This helper method sends a response back to the client.
    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    // This helper method prints the client info for debugging.
    private static void printClientInfo(HttpExchange exchange) {
        System.out.println("Client Address: " + exchange.getRemoteAddress().toString());
        System.out.println("Request Method: " + exchange.getRequestMethod());
        System.out.println("Request URI: " + exchange.getRequestURI().toString());
    }
}