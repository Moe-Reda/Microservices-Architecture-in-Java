#!/bin/bash

# Function to compile Java files
compile_java() {
    # Check if any Java files exist
    if ls *.java >/dev/null 2>&1; then
        # Compile Java files
        javac *.java
    else
        echo "No Java files found."
    fi
}

# Function to run Java program
run_java() {
    # Check if any class files exist
    if ls *.class >/dev/null 2>&1; then
        # Run Java program
        java Main
    else
        echo "No class files found. Please compile first."
    fi
}

# Check argument and execute corresponding function
if [ "$1" == "-c" ]; then
    compile_java
elif [ "$1" == "-r" ]; then
    run_java
else
    echo "Invalid argument. Usage: $0 [-c|-r]"
    exit 1
fi