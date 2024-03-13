import requests
import time

# Function to send POST requests
def send_post_request(url, data):
    try:
        response = requests.post(url, data=str(data))  # Assuming JSON data
        if response.status_code != 200:
            print(f"POST Request Status Code: {response.status_code}, URL: {url}")
    except Exception as e:
        print(f"Error sending POST request to {url}: {e}")

# Function to send GET requests
def send_get_request(url):
    try:
        response = requests.get(url)
        if response.status_code != 200:
            print(f"GET Request Status Code: {response.status_code}, URL: {url}")
    except Exception as e:
        print(f"Error sending GET request to {url}: {e}")

# Function to send requests synchronously at a specified rate
def send_requests_synchronously(url, requests_per_second, duration_seconds):
    id = 20
    start_time = time.time()
    request_count = 0

    while True:
        current_time = time.time()
        if current_time - start_time > duration_seconds:
            break  # Stop sending requests after the specified duration

        # Sequentially process POST and then GET for user, product, and order
        user_post_data = {"command": "create", "id": id, "username": "tester", "email": "test@example.com", "password": "password"}
        product_post_data = {"command": "create", "id": id, "name": "Product Name", "description": "Product Description", "price": 20, "quantity": 1000}
        order_post_data = {"command": "place order", "product_id": id, "user_id": id, "quantity": 20}

        # POST requests
        send_post_request(url + "user", user_post_data)
        request_count += 1
        send_post_request(url + "product", product_post_data)
        request_count += 1
        send_post_request(url + "order", order_post_data)
        request_count += 1

        # Corresponding GET requests
        send_get_request(url + "user/" + str(id))
        request_count += 1
        send_get_request(url + "product/" + str(id))
        request_count += 1
        send_get_request(url + "user/purchased/" + str(id))
        request_count += 1

        id += 1

        # Ensure the request rate is maintained
        elapsed_time = time.time() - current_time
        sleep_time = max(0, (1 / requests_per_second) - elapsed_time)
        time.sleep(sleep_time)

    print(f"Processed {request_count} requests in {duration_seconds} seconds.")

# Main function
if __name__ == "__main__":
    url = "http://127.0.0.1:8000/"  # Replace with your service URL
    requests_per_second = 200  # Adjusted for a more realistic demonstration
    duration_seconds = 1  # Duration to send requests for
    send_requests_synchronously(url, requests_per_second, duration_seconds)
