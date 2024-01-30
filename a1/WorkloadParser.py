import requests
import json
import sys

def format_data(line):
    if line[0] == 'USER':
        if line[1] == 'update':
            data = {
                'command': line[1],
                'id': int(line[2])
            }
            for token in line[3:]:
                key_value = token.split(':')
                if key_value[0] == "quantity":
                    data[key_value[0]] = int(key_value[1])
                elif key_value[0] == "price":
                    data[key_value[0]] = float(key_value[1])
                else:
                    data[key_value[0]] = key_value[1]     
        else:
            data = {
                'command': line[1],
                'id': int(line[2]),
                'username': line[3],
                'email': line[4],
                'password': line[5]
            }
    elif line[0] == 'PRODUCT':
        if line[1] == 'update':
            data = {
                'command': line[1],
                'id': int(line[2])
            }
            for token in line[3:]:
                key_value = token.split(':')
                if key_value[0] == "quantity":
                    data[key_value[0]] = int(key_value[1])
                elif key_value[0] == "price":
                    data[key_value[0]] = float(key_value[1])
                else:
                    data[key_value[0]] = key_value[1]
            
        else:
            data = {
                'command': line[1],
                'id': int(line[2]),
                'name': line[3],
                'description': line[4],
                'price': float(line[5]),
                'quantity': int(line[6])
            }
    elif line[0] == 'ORDER':
        if len(line) == 5:
            data = {
                'command': line[1],
                'product_id': int(line[2]),
                'user_id': int(line[3]),
                'quantity': int(line[4]),
            }
        else:
            data = {
                'command': line[1],
                'product_id': int(line[2]),
                'user_id': 1,
                'quantity': int(line[3]),
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
            print("Sending a GET request to", api_url)
            response = requests.get(api_url + "/" + str(request[2]), headers=headers)
        else:
            print("Sending a POST request to", api_url)
            data = format_data(request)
            response = requests.post(api_url, data=str(data), headers=headers)
        print(f"Request: {request} | Response: {response.text}")
    except requests.RequestException as e:
        print(f"Error sending request: {e}")

def main():
    if len(sys.argv) != 3:
        print("Usage: python WorkloadParser.py <requests_file> <config_file>")
        sys.exit(1)

    requests_file = sys.argv[1]
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
