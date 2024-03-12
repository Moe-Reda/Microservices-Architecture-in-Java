import requests
import time

# Function to send POST requests
def send_post_request(url, data):
    try:
        response = requests.post(url, data=str(data))
        if response.status_code != 200:
            print("POST Request Status Code:", response.status_code)
    except Exception as e:
        print("Error sending POST request:", e)

# Function to send GET requests
def send_get_request(url):
    try:
        response = requests.get(url)
        if response.status_code != 200:
            print("GET Request Status Code:", response.status_code)
    except Exception as e:
        print("Error sending GET request:", e)

# Function to send requests synchronously at a specified rate
def send_requests_synchronously(url, requests_per_second, duration_seconds):
    id = 8000
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

        # Submit POST request for user and product synchronously
        send_post_request(url + "user", user_post_data)
        request_count += 1  # Increment request count for POST request
        
        send_post_request(url + "product", product_post_data)
        request_count += 1  # Increment request count for POST request

        # Submit GET request synchronously
        send_get_request(url + "user/" + str(id))
        request_count += 1  # Increment request count for GET request
        
        send_get_request(url + "product/" + str(id))
        request_count += 1  # Increment request count for GET request

        order_post_data = {"command": "place order",
                           "product_id": id,
                           "user_id": id,
                           "quantity": 20}

        # Submit POST request for order synchronously
        send_post_request(url + "order", order_post_data)
        request_count += 1  # Increment request count for POST request

        send_get_request(url + "user/purchased/" + str(id))
        request_count += 1  # Increment request count for GET request

        id += 1

        # Ensure the request rate is maintained
        time.sleep(1 / requests_per_second)

    print(f"Processed {request_count} requests in {duration_seconds} seconds.")

# Main function
if __name__ == "__main__":
    url = "http://127.0.0.1:8000/"  # Replace with your URL
    requests_per_second = 1  # Adjusted for synchronous demonstration
    duration_seconds = 10  # Duration to send requests for
    send_requests_synchronously(url, requests_per_second, duration_seconds)
