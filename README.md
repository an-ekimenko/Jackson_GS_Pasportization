# Jackson_GS_Pasportization

**Extracts, parses, and stores data about gas station attributes from a web API into an MS SQL staging environment for SAP BW/4HANA.**

## Description

The **Jackson_GS_Pasportization** application automates the extraction, parsing, and storage of gas station complex attributes (such as latitude, longitude, address, available fuel brands, and service request data for station equipment). The application retrieves data in JSON format from a web service, processes it, and writes the records to a target MS SQL database. This database serves as an intermediate staging area for subsequent ETL loading into **SAP BW/4HANA**.

## Features

* **Resilient Enterprise Networking:** Utilizes **Apache HttpClient 5** with a built-in connection pool (`PoolingHttpClientConnectionManager`), providing a solid foundation for robust, high-performance web service interactions.
* **Automated Fault-Tolerance:** Replaces fragile manual loops with a declarative `DefaultHttpRequestRetryStrategy` executing an exponential backoff algorithm (up to 5 attempts, starting at 30 seconds) to survive temporary network failures.
* **On-The-Fly SSL Certificate Management:** Features a custom KeyStoreManager that programmatically intercepts remote certificate chains during SSL handshakes and updates the local JVM `cacerts` file. It hot-swaps the memory-resident `SSLContext` dynamically without requiring an application restart.
* **Robust JSON Deserialization:** Configured via Jackson's `ObjectMapper` to gracefully ignore undeclared or unknown API properties (`FAIL_ON_UNKNOWN_PROPERTIES`), preventing runtime crashes during upstream schema updates.
* **Defensive Parsing & Stream Transformation:** Employs a custom `GSP_DataDeserializer` registered via a `SimpleModule` for type-safe mapping. Uses Java Streams to map raw DTO payload arrays into persistent Hibernate entities.
* **High-Performance Staging Persistence:** Executes optimized, transaction-aware batch operations (`saveOrUpdateBatch`) directly through the DAO layer to minimize round-trips to the MS SQL database.
* **Pluggable Multi-Schema Authentication:** Out-of-the-box support for `basic`, `token`, or `none` API authorization schemes, dynamically driven by external configuration files.

## Project Structure & Configuration

The project follows a clean, layered architecture designed for deterministic batch processing:

### Configuration
* `gs_passportization.config.GSPApiConfig` — A DTO class for deserializing API connection parameters from the YAML configuration file using Jackson. Includes a nested structure for flexible authentication handling.
* `gs_passportization.LogConfigUtil` — A utility class responsible for dynamic runtime initialization of the Log4j2/SLF4J logging subsystem. It automatically evaluates the execution path (IDE context vs. standalone JAR) to decouple log generation from internal deployment artifacts.

### Domain Model & Data Transfer (DTO)
* `gs_passportization.model.GSP_Entity` — A Hibernate entity class representing the target `GS_Pasportization` database table.
* `gs_passportization.model.GSP_Data` — A DTO class representing a gas station complex object mapped directly from the raw JSON structure.

### JSON Processing & Orchestration
* `gs_passportization.jackson_json.GSP_DataDeserializer` — A custom Jackson deserializer designed for defensive extraction of API fields, safely mapping missing or empty JSON nodes into appropriate Java objects and handling numerical overflows.
* `gs_passportization.GSP_App` — The main entry point of the application containing the core execution orchestration flow, integrated Apache HttpClient logic, and the `main()` method.

### Persistence Layer
* `gs_passportization.hibernate.GSP_DAO` — A helper DAO class that manages transactional batch database operations (`merge`, `flush`, `clear`, `saveOrUpdateBatch`) to safely persist data without memory leaks.
* `gs_passportization.util.HibernateUtil` — A utility class for managing the thread-safe Hibernate `SessionFactory` singleton instance using safe lazy initialization.

### Generated Artifacts
* `logs/` — Created automatically in the application root directory during runtime. Contains standalone execution logs (`commons.log`) and database interaction telemetry (`p6spy.log`).

---

## Environment Setup & Configuration Files

The application dynamically adjusts its behavior based on external configuration profiles. You must configure the environment before deployment:

### 1. API Authentication (`properties.yaml`)
Configure your target authentication strategy using one of the structural schemas below:

#### Basic Authentication
```yaml
auth:
  type: "basic"
  username: "your_username"
  password: "your_password"
```

#### Token Authentication
```yaml
auth:
  type: "token"
  token: "your_bearer_token_here"
```

#### Unauthenticated Access
```yaml
auth:
  type: "none"
```

### 2. Database Connection (`Hibernate.cfg.xml`)
The repository includes a template file named `Hibernate.cfg2.xml` containing the configuration schema for database connection pooling and P6Spy integration.

To provision your target environment:
1. Duplicate `Hibernate.cfg2.xml` and rename it to `Hibernate.cfg.xml`.
2. Replace credentials placeholders (`YOUR_DB_HOST`, `YOUR_DB_NAME`, `YOUR_USERNAME`, and `YOUR_PASSWORD`) with your actual staging infrastructure parameters.

---

## Prerequisites

* **Java:** JDK 8 or higher
* **Database:** MS SQL Server
* **Build System:** Maven (configured via `pom.xml`)

## Deployment & Running

1. **Configure Infrastructure Properties:** Set up your target database credentials, API endpoint parameters, authentication metadata, and P6Spy properties within the configuration files as described in the configuration section above.
2. **Build the Application:** Execute the Maven package phase to compile the code and assemble the executable fat JAR asset:
   ```bash
   mvn clean package
   ```
3. **Execute the Application:** Run the compiled JAR file using the Java CLI. The internal logging engine automatically calculates safe, non-nested storage destinations relative to your current execution scope:
   ```bash
   java -jar target/JacksonJson_RepairRequests-0.0.1-SNAPSHOT.jar
   ```

   
