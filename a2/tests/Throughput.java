import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

public class Throughput {

    private static final String BASE_URL = "http://127.0.0.1:8000";
    private static final int NUM_REQUESTS = 2000;
    private static final int CONCURRENT_THREADS = 100;
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) throws Exception {
        long totalStartTime = System.currentTimeMillis();

        // Perform POST requests
        long postStartTime = System.currentTimeMillis();
        List<CompletableFuture<Boolean>> postFutures = performPostRequests();
        CompletableFuture.allOf(postFutures.toArray(new CompletableFuture[0])).join();
        long postEndTime = System.currentTimeMillis();
        System.out.println("Completed POST requests in " + (postEndTime - postStartTime) + " milliseconds.");

        // Perform GET requests
        long getStartTime = System.currentTimeMillis();
        List<CompletableFuture<Boolean>> getFutures = performGetRequests();
        CompletableFuture.allOf(getFutures.toArray(new CompletableFuture[0])).join();
        long getEndTime = System.currentTimeMillis();
        System.out.println("Completed GET requests in " + (getEndTime - getStartTime) + " milliseconds.");

        long totalEndTime = System.currentTimeMillis();
        System.out.println("Total operation time: " + (totalEndTime - totalStartTime) + " milliseconds.");
    }

    private static List<CompletableFuture<Boolean>> performPostRequests() {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < NUM_REQUESTS; i++) {
            int index = i;
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String json = "{\"command\": \"create\", \"id\": " + index + ", \"username\": \"User" + index + "\", \"email\": \"user" + index + "@example.com\", \"password\": \"password\"}";
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/user"))
                            .header("Content-Type", "application/json")
                            .POST(BodyPublishers.ofString(json))
                            .build();
                    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                    return response.statusCode() == 200;
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }
        return futures;
    }

    private static List<CompletableFuture<Boolean>> performGetRequests() {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < NUM_REQUESTS; i++) {
            int index = i;
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/user/" + index))
                            .build();
                    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                    return response.statusCode() == 200;
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }
        return futures;
    }
}

