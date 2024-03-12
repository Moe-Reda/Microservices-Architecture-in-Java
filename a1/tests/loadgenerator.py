import requests
import time
from concurrent.futures import ThreadPoolExecutor, as_completed


# Function to send POST requests
def send_post_request(url, data):
    try:
        response = requests.post(url, data=str(data))
        if response.status_code != 200:
            print("POST Request Status Code:", response.status_code)
        return response.status_code != 200
    except Exception as e:
        print("Error sending POST request:", e)
        return False


# Function to send GET requests
def send_get_request(url):
    try:
        response = requests.get(url)
        if response.status_code != 200:
            print("GET Request Status Code:", response.status_code)
    except Exception as e:
        print("Error sending GET request:", e)


# Function to send requests concurrently at a specified rate
def send_requests_concurrently(url, requests_per_second, duration_seconds):
    id = 8000
    with ThreadPoolExecutor(max_workers=requests_per_second * 2) as executor:
        futures = []
        start_time = time.time()
        request_count = 0

        while True:
            current_time = time.time()
            if current_time - start_time > duration_seconds:
                break  # Stop sending requests after the specified duration

            # Prepare POST data
            user_post_data = {"command": "create", "id": id,
                              "username": "tester", "email": "test@example.com",
                              "password": "password"}
            product_post_data = {"command": "create", "id": id,
                                 "name": "Product Name",
                                 "description": "Product Description",
                                 "price": 20, "quantity": 1000}

            # Submit POST request for user and product
            user_future = executor.submit(send_post_request, url + "user",
                                          user_post_data)
            product_future = executor.submit(send_post_request, url + "product",
                                             product_post_data)

            # Increment request count for POST requests
            request_count += 2

            #Block until POST requests complete
            if user_future.result():
                futures.append(
                    executor.submit(send_get_request, url + "user/" + str(id)))
                request_count += 1  # Increment request count for GET request
            if product_future.result():
                futures.append(executor.submit(send_get_request,
                                               url + "product/" + str(id)))
                request_count += 1  # Increment request count for GET request

            order_post_data = {"command": "place order",
                               "product_id": id,
                               "user_id": id,
                               "quantity": 20}

            # Submit POST request for order
            order_future = executor.submit(send_post_request, url + "order",
                                          order_post_data)

            request_count += 1

            if order_future.result():
                futures.append(
                    executor.submit(send_get_request, url + "user/purchased/" + str(id)))
                request_count += 1  # Increment request count for GET request

            id += 1

            # Control the rate of requests per second
            while time.time() - current_time < 1 / requests_per_second:
                time.sleep(0.00001)  # Sleep briefly to avoid tight loop

        # Wait for all submitted futures to complete
        for future in as_completed(futures):
            pass

        print(
            f"Processed {request_count} requests in {duration_seconds} seconds.")


# Main function
if __name__ == "__main__":
    url = "http://127.0.0.1:8000/"  # Replace with your URL
    requests_per_second = 2000  # Adjust for demonstration purposes
    duration_seconds = 1  # Duration to send requests for
    send_requests_concurrently(url, requests_per_second, duration_seconds)
