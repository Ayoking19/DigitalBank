# 1. THE FOUNDATION
# Pull a lightweight, official Java 21 operating system image. 'alpine' means it is stripped of unnecessary files to keep the server fast and cheap.
FROM eclipse-temurin:21-jdk-alpine

# 2. THE WORKSPACE
# Set the primary working directory inside the virtual container
WORKDIR /app

# Copy all your local Java files from your laptop into the container's /app/src/ directory
COPY . /app/src/

# Create specific digital folders to hold our external database driver and our compiled final application
RUN mkdir -p /app/lib /app/out

# 3. RULE 1: THE DRIVER DEPENDENCY
# Raw Java cannot speak MySQL. We command the container to reach out to the internet and download the official translator library (Connector/J) into our lib folder.
RUN wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar -O /app/lib/mysql-connector.jar

# 4. RULE 2: BYPASSING SHELL EXPANSION LIMITS
# Instead of using a wildcard (javac *.java) which crashes Linux memory limits if you have too many files, we use the precise 'find' command. 
# It safely lists every file into a text document, then feeds that document to the compiler alongside the downloaded MySQL driver.
RUN find /app/src -name "*.java" > sources.txt && \
    javac -cp "/app/lib/mysql-connector.jar" -d /app/out @sources.txt

# 5. THE NETWORK GATEWAY
# Explicitly open a hole in the container's firewall so external internet traffic can reach the Java HTTP Server
EXPOSE 8080

# 6. THE IGNITION SEQUENCE
# The final command the server runs when it boots up. It executes BankServer and explicitly links the MySQL driver to the running memory.
CMD ["java", "-cp", "/app/out:/app/lib/mysql-connector.jar", "BankServer"]