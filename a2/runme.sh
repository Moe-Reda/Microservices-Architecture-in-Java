#!/bin/bash

# Define paths to directories
SRC_DIR="src"
COMPILED_DIR="compiled"
LIB_DIR="src/lib"
CLASSPATH="$LIB_DIR/*"

# Ensure the compiled directory exists for each service
mkdir -p $COMPILED_DIR/ISCS
mkdir -p $COMPILED_DIR/OrderService
mkdir -p $COMPILED_DIR/ProductService
mkdir -p $COMPILED_DIR/UserService

# Function to compile all Java files including ServiceUtil.java
compile() {
    echo "Compiling Java files..."
    # Compile ServiceUtil
    javac -cp "$CLASSPATH" $LIB_DIR/ServiceUtil.java -d $LIB_DIR

    # Compile ISCS
    javac -cp ".:$COMPILED_DIR:$CLASSPATH" $SRC_DIR/ISCS/ISCS.java -d $COMPILED_DIR/ISCS
    # Compile OrderService
    javac -cp ".:$COMPILED_DIR:$CLASSPATH" $SRC_DIR/OrderService/OrderService.java -d $COMPILED_DIR/OrderService
    # Compile ProductService
    javac -cp ".:$COMPILED_DIR:$CLASSPATH" $SRC_DIR/ProductService/ProductService.java -d $COMPILED_DIR/ProductService
    # Compile UserService
    javac -cp ".:$COMPILED_DIR:$CLASSPATH" $SRC_DIR/UserService/UserService.java -d $COMPILED_DIR/UserService
    # Compile Load Balancer
    javac -cp ".:$COMPILED_DIR:$CLASSPATH" $SRC_DIR/LoadBalancer/LoadBalancer.java -d $COMPILED_DIR/LoadBalancer
    echo "Compilation completed."
}

# Function to start the User service
start_user_service() {
    echo "Starting User Service..."
    [ -f "compiled/UserService/user.db" ] && rm "compiled/UserService/user.db"
    [ -f "compiled/UserService/order.db" ] && rm "compiled/UserService/order.db"
    java -cp ".:$COMPILED_DIR/UserService:$CLASSPATH" UserService config.json
}

start_load_balancer() {
    echo "Starting Load Balancer..."
    java -cp ".:$COMPILED_DIR/LoadBalancer:$CLASSPATH" LoadBalancer config.json
}
# Function to start the Product service
start_product_service() {
    echo "Starting Product Service..."
    [ -f "compiled/ProductService/product.db" ] && rm "compiled/ProductService/product.db"
    java -cp ".:$COMPILED_DIR/ProductService:$CLASSPATH" ProductService config.json
}

# Function to start the ISCS
start_iscs() {
    echo "Starting Inter-service Communication Service (ISCS)..."
    java -cp ".:$COMPILED_DIR/ISCS:$CLASSPATH" ISCS config.json
}

# Function to start the Order service
start_order_service() {
    echo "Starting Order Service..."
    java -cp ".:$COMPILED_DIR/OrderService:$CLASSPATH" OrderService config.json
}

# Function to start the Workload Parser
start_workload_parser() {
    echo "Starting Workload Parser with workload file: $1"
    # Assuming WorkloadParser.py is not a compiled Java class but a script in the root directory
    python3 WorkloadParser.py "$1" config.json
}

# Check the command-line argument and call the appropriate function
case $1 in
    -c) compile;;
    -u) start_user_service;;
    -l) start_load_balancer;;
    -p) start_product_service;;
    -i) start_iscs;;
    -o) start_order_service;;
    -w) shift
        start_workload_parser "$1";;
    *) echo "Invalid option. Please use one of the following options:
        -c to compile all code.
        -u to start the User service.
        -l to start the Load Balancer.
        -p to start the Product service.
        -i to start the ISCS.
        -o to start the Order service.
        -w [workloadfile] to start the Workload Parser with the specified workload file.";;
esac
