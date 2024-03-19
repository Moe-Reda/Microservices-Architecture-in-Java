import requests
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

BASE_URL = "http://127.0.0.1:8000"
NUM_REQUESTS = 2000
CONCURRENT_THREADS = 100

def post_request(index):
    """Send a POST request to create a user."""
    url = f"{BASE_URL}/user"
    data = {
        "command": "create",
        "id": index,
        "username": f"User{index}",
        "email": f"user{index}@example.com",
        "password": "password"  # Sending plain password
    }
    start_time = time.time()
    try:
        response = requests.post(url, json=data)
        end_time = time.time()
        if response.status_code == 200:
            return index, True, end_time - start_time
    except Exception as e:
        end_time = time.time()
    return index, False, end_time - start_time

def get_request(index):
    """Send a GET request to retrieve a user."""
    url = f"{BASE_URL}/user/{index}"
    start_time = time.time()
    try:
        response = requests.get(url)
        end_time = time.time()
        if response.status_code == 200:
            return True, end_time - start_time
    except Exception as e:
        end_time = time.time()
    return False, end_time - start_time

def perform_post_requests():
    """Perform POST requests using concurrent threads."""
    with ThreadPoolExecutor(max_workers=CONCURRENT_THREADS) as executor:
        futures = [executor.submit(post_request, i) for i in range(NUM_REQUESTS)]
        results = [future.result() for future in as_completed(futures)]
    return results

def perform_get_requests(successful_indices):
    """Perform GET requests for successfully created users."""
    with ThreadPoolExecutor(max_workers=CONCURRENT_THREADS) as executor:
        futures = [executor.submit(get_request, index) for index, success, _ in successful_indices if success]
        results = [future.result() for future in as_completed(futures)]
    return results

if __name__ == "__main__":
    total_start_time = time.time()

    # Perform POST requests and measure time
    post_start_time = time.time()
    post_results = perform_post_requests()
    post_end_time = time.time()
    print(f"Completed POST requests in {post_end_time - post_start_time:.2f} seconds.")

    # Extract successful POST request indices for corresponding GET requests
    successful_posts = [result for result in post_results if result[1]]

    # Perform GET requests and measure time
    get_start_time = time.time()
    get_results = perform_get_requests(successful_posts)
    get_end_time = time.time()
    print(f"Completed GET requests in {get_end_time - get_start_time:.2f} seconds.")

    total_end_time = time.time()
    print(f"Total operation time: {total_end_time - total_start_time:.2f} seconds.")
