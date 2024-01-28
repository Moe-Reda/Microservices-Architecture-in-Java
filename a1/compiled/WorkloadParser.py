import requests
import json
import sys

def format_data(data):
    if data[0] == 'USER':
        data = {
            'command': data[1],
            'id': data[2],
            'username': data[3],
            'email': data[4],
            'password': data[5]
        }
    elif data[0] == 'PRODUCT':
        data = {
            'command': data[1],
            'name': data[2],
            'description': data[3],
            'price': data[4],
            'quantity': data[5]
        }
    elif data[0] == 'ORDER':
        if len(data) == 5:
            data = {
                'command': data[1],
                'product_id': data[2],
                'user_id': data[3],
                'quantity': data[4],
            }
        else:
            data = {
                'command': data[1],
                'product_id': data[2],
                'user_id': 1,
                'quantity': data[3],
            }
    return data

def read_config(config_file):
    try:
        with open(config_file, 'r') as f:
            config = json.load(f)
            return config
    except FileNotFoundError:
        print(f"Error: Config file '{config_file}' not found.")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON in config file: {e}")
        sys.exit(1)

def send_request(api_url, request):
    try:
        headers = requests.utils.default_headers()
        if request[1] in ['info', 'get']:
            print("Sending GET request to", api_url)
            response = requests.get(api_url + "/" + str(request[2]), headers=headers)
        else:
            print("Sending POST request to", api_url)
            data = format_data(request)
            response = requests.post(api_url, data=str(data), headers=headers)
        print(f"Request: {request} | Response: {response.text}")
    except requests.RequestException as e:
        print(f"Error sending request: {e}")

def main():
    if len(sys.argv) != 3:
        print("Usage: python WorkloadParser.py <requests_file> <config_file>")
        sys.exit(1)

    requests_file = "../" + sys.argv[1]
    config_file = sys.argv[2]

    # Read config
    config = read_config(config_file)

    # Construct API URL
    api_url = f"http://{config['OrderService']['ip']}:{config['OrderService']['port']}/"

    # Read requests from file and send them to the API
    try:
        with open(requests_file, 'r') as file:
            for line in file:
                command = line.split()
                endpoint = command[0].lower()
                send_request(api_url + endpoint, command)
    except FileNotFoundError:
        print(f"Error: Requests file '{requests_file}' not found.")
        sys.exit(1)

if __name__ == "__main__":
    main()
