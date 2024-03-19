import requests
import threading
from queue import Queue
import time

BASE_URL = "http://127.0.0.1:13471"
NUM_REQUESTS = 1000  # Example number of requests

# Queues for holding IDs for GET requests
get_user_queue = Queue()
get_product_queue = Queue()

def post_request(url, data, queue=None):
    try:
        response = requests.post(url, json=data)
        if response.status_code == 200 and queue is not None:
            # Assuming the response includes an 'id' that we need for GET requests
            response_data = response.json()
            queue.put(response_data['id'])
    except Exception as e:
        print(f"Error sending POST request to {url}: {e}")

def get_request(url):
    try:
        response = requests.get(url)
        if response.status_code != 200:
            print(f"GET Request Status Code: {response.status_code}, URL: {url}")
    except Exception as e:
        print(f"Error sending GET request to {url}: {e}")

def post_requests_thread():
    start_time = time.time()
    for i in range(NUM_REQUESTS):
        user_data = {"command": "create", "id": i, "username": f"User{i}", "email": f"user{i}@example.com", "password": "password"}
        product_data = {"command": "create", "id": i, "name": f"Product{i}", "description": "A product", "price": 10, "quantity": 100}
        post_request(f"{BASE_URL}/user", user_data, get_user_queue)
        post_request(f"{BASE_URL}/product", product_data, get_product_queue)
    end_time = time.time()
    print(f"POST requests completed in {end_time - start_time:.2f} seconds.")

def get_requests_thread(queue, entity):
    start_time = time.time()
    while not queue.empty():
        entity_id = queue.get()
        get_request(f"{BASE_URL}/{entity}/{entity_id}")
    end_time = time.time()
    print(f"GET requests for {entity} completed in {end_time - start_time:.2f} seconds.")

if __name__ == "__main__":
    total_start_time = time.time()

    # Start POST requests in a separate thread
    post_thread = threading.Thread(target=post_requests_thread)
    post_thread.start()
    post_thread.join()

    # Start GET requests in separate threads for users and products
    get_user_thread = threading.Thread(target=get_requests_thread, args=(get_user_queue, 'user'))
    get_product_thread = threading.Thread(target=get_requests_thread, args=(get_product_queue, 'product'))

    get_user_thread.start()
    get_product_thread.start()

    get_user_thread.join()
    get_product_thread.join()

    total_end_time = time.time()
    print(f"Total operation time: {total_end_time - total_start_time:.2f} seconds.")
