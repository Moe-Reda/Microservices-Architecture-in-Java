Compilation and Execution Instructions:
----------------------------------------

1. To compile all the code, navigate to the root directory of the assignment (A1) and run the following command:

    ./runme.sh -c

   This will compile all the services including User, Product, ISCS, and Order services.

2. To start the User Service, execute the command:

    ./runme.sh -u

   This will start the User Service, listening for incoming requests.

3. To start the Product Service, use the command:

    ./runme.sh -p

   This initiates the Product Service, ready to handle requests related to products.

4. To launch the Inter-service Communication Service (ISCS), run:

    ./runme.sh -i

   The ISCS acts as a mediator for communication between different services.

5. To start the Order Service, execute:

    ./runme.sh -o

   This command starts the Order Service, which is responsible for handling orders.

6. To run the Workload Parser with a specified workload file, use:

    ./runme.sh -w <workloadfile>

   Replace `<workloadfile>` with the path to your workload file. This parser will simulate the specified workload on the Order Service.

Please ensure that you have all necessary permissions to execute `runme.sh` script. You might need to grant execution permissions using the command `chmod +x runme.sh`.

For any issues or further assistance, please refer to the project documentation or contact the project maintainers.
