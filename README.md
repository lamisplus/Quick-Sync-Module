#  Quick Sync Module
## Description
The **Quick Sync Module** is a critical component of the **LAMISPlus** system, enabling efficient bidirectional synchronization of patient records, clinical data, and program metrics. It is specifically tailored for resource-constrained settings where internet connectivity may be intermittent or unreliable. By supporting offline-first workflows, selective sync, and conflict resolution, this module ensures that healthcare providers have access to up-to-date and accurate data, regardless of location.

## Key Features

- **Bidirectional Data Sync**: Upload and download updates across facilities and central servers.
- **Conflict Resolution**: Automatically detect and resolve data conflicts; supports manual resolution for complex cases.
- **Mobile Sync**: Enable field teams and mobile health workers to collect data offline and sync it upon returning to base or connecting to the network
- **Data Compression**: Minimize data size during transmission for faster sync operations.
- **Security & Privacy**: Encrypt data during transmission and enforce role-based access control.
- **Audit Logs**: Track all sync activities, including timestamps, data volumes, and success/failure statuses.

## System Requirements

### Prerequisites to Install
- IDE of choice (IntelliJ, Eclipse, etc.)
- Java 8+
- node.js
- React.js
## Run in Development Environment

### How to Install Dependencies
1. Install Java 8+
2. Install PostgreSQL 14+
3. Install node.js
4. Install React.js
5. Open the project in your IDE of choice.

### Update Configuration File
1. Update other Maven application properties as required.

### Run Build and Install Commands
1. Change the directory to `src`:
    ```bash
    cd src
    ```
2. Run Frontend Build Command:
    ```bash
    npm run build
    ```
3. Run Maven clean install:
    ```bash
    mvn clean install
    ```

## How to Package for Production Environment
1. Run Maven package command:
    ```bash
    mvn clean package
    ```

## Launch Packaged JAR File
1. Launch the JAR file:
    ```bash
    java -jar <path-to-jar-file>
    ```
2. Optionally, run with memory allocation:
    ```bash
    java -jar -Xms4096M -Xmx6144M <path-to-jar-file>
    ```

## Visit the Application
- Visit the application on a browser at the configured port:
    ```
    http://localhost:8080
    ```

## Access Swagger Documentation
- Visit the application at:
    ```
    http://localhost:8080/swagger-ui.html#/
    ```

## Access Application Logs
- Application logs can be accessed in the `application-debug` folder.

## Authors & Acknowledgments
### Main contributors
- Chukwuemeka Ilozue https://github.com/drjavanew
- Victor Ajor https://github.com/AJ-DataFI
- Abiodun Peter https://github.com/Asquarep
- Gboyegun Taiwo https://github.com/mechack2022

### Special contributors
- Aniwange Tertese Amos https://github.com/aniwange33
- Stomzy https://github.com/stomzy

  



