# Jackson_GS_Pasportization

**Extracts, parses, and stores data about gas station attributes from a web API into an MS SQL staging environment for SAP BW/4HANA.**

## 📋 Description

The **Jackson_GS_Pasportization** application automates the extraction, parsing, and storage of gas station complex attributes (such as latitude, longitude, address, available fuel brands, and service request data for station equipment). The application retrieves data in JSON format from a web service, processes it, and writes the records to a target MS SQL database. This database serves as an intermediate staging area for subsequent ETL loading into **SAP BW/4HANA**.

## 🚀 Features

* **Resilient Enterprise Networking:** Utilizes **Apache HttpClient 5** with a built-in connection pool (`PoolingHttpClientConnectionManager`), providing a solid foundation for robust, high-performance web service interactions.
* **Automated Fault-Tolerance:** Replaces fragile manual loops with a declarative `DefaultHttpRequestRetryStrategy` executing an exponential backoff algorithm (up to 5 attempts, starting at 30 seconds) to survive temporary network failures.
* **On-The-Fly SSL Certificate Management:** Features an advanced `KeyStoreManager` that programmatically intercepts remote certificate chains during SSL handshakes and updates the local JVM `cacerts` file. It hot-swaps the memory-resident `SSLContext` dynamically without requiring an application restart.
* **Robust JSON Deserialization:** Configured via Jackson's `ObjectMapper` to gracefully ignore undeclared or unknown API properties (`FAIL_ON_UNKNOWN_PROPERTIES`), preventing runtime crashes during upstream schema updates.
* **Defensive Parsing & Stream Transformation:** Employs a custom `GSP_DataDeserializer` registered via a `SimpleModule` for type-safe mapping. Uses Java Streams to map raw DTO payload arrays into persistent Hibernate entities.
* **High-Performance Staging Persistence:** Executes optimized, transaction-aware batch operations (`saveOrUpdateBatch`) directly through the DAO layer to minimize round-trips to the MS SQL database.
* **Pluggable Multi-Schema Authentication:** Out-of-the-box support for `basic`, `token`, or `none` API authorization schemes, dynamically driven by external configuration files.

## 🏗️ Shared Dependency Architecture

To eliminate library redundancy and optimize infrastructure disk usage across multiple similar integration applications, this project adopts a decoupled shared-dependency deployment model:

* **Centralized Dependencies:** All required external runtime JAR libraries are compiled and isolated into a dedicated `dependencies` directory.
* **Directory Layout:** The `dependencies` folder is located **one level above** the application root directory (`Jackson_GS_Pasportization`), serving as a common binary store for neighboring integration modules:
  ```text
  ├── shared-environment-root/
  │   ├── dependencies/                 <-- Shared runtime external libraries (.jar)
  │   └── Jackson_GS_Pasportization/    <-- This application root directory
  │       ├── src/
  │       ├── target/
  │       └── pom.xml
  ```
* **Classpath Decoupling:** The compiled application artifact excludes third-party binaries inside its own structure, dynamically linking against the shared directory at runtime via classpath definitions.
  
## 📂 Project Structure & Configuration

The project follows a clean, layered architecture designed for deterministic batch processing:

### ⚙️ Configuration & Security Management
> ⚠️ **Security Note:** Real production configuration files containing plain-text credentials (`properties.yaml` and `hibernate.cfg.xml`) are strictly tracked by `.gitignore` and excluded from the Git repository to prevent accidental credential leakage. Instead, public safe templates are provided.

* `properties2.yaml` — A safe template for API authorization modes, targets, and environment settings. Must be copied and renamed to `properties.yaml` locally.
* `hibernate.cfg2.xml` — A safe template for MS SQL connection strings and Hibernate parameters. Must be copied and renamed to `hibernate.cfg.xml` locally.
* `gs_passportization.config.GSPApiConfig` — A DTO class for deserializing API connection parameters from the YAML configuration file using Jackson. Includes a nested structure for flexible authentication handling.
* `gs_passportization.LogConfigUtil` — A utility class responsible for dynamic runtime initialization of the Log4j2/SLF4J logging subsystem. It automatically evaluates the execution path (IDE context vs. standalone JAR) to decouple log generation from internal deployment artifacts.

### 🗃️ Domain Model & Data Transfer (DTO)
* `gs_passportization.model.GSP_Entity` — A Hibernate entity class representing the target `GS_Pasportization` database table.
* `gs_passportization.model.GSP_Data` — A DTO class representing a gas station complex object mapped directly from the raw JSON structure.

### 🔄 JSON Processing & Orchestration
* `gs_passportization.jackson_json.GSP_DataDeserializer` — A custom Jackson deserializer designed for defensive extraction of API fields, safely mapping missing or empty JSON nodes into appropriate Java objects and handling numerical overflows.
* `gs_passportization.GSP_App` — The main entry point of the application containing the core execution orchestration flow, integrated Apache HttpClient logic, and the `main()` method.

### 💾 Persistence Layer
* `gs_passportization.hibernate.GSP_DAO` — A helper DAO class that manages transactional batch database operations (`merge`, `flush`, `clear`, `saveOrUpdateBatch`) to safely persist data without memory leaks.
* `gs_passportization.util.HibernateUtil` — A utility class for managing the thread-safe Hibernate `SessionFactory` singleton instance using safe lazy initialization.

### 📊 Generated Artifacts
* `logs/` — Created automatically in the application root directory during runtime. Contains standalone execution logs (`commons.log`) and database interaction telemetry (`p6spy.log`).

---

## Prerequisites

* **Java:** JDK 8 or higher
* **Database:** MS SQL Server
* **Build System:** Maven (configured via `pom.xml`)
## 💻 Deployment & Running

1. **Prepare Environment Configurations:**
   Since production credentials are safe from tracking via `.gitignore`, initialize your active operational profiles from the provided templates:
   * Copy `properties.example.yaml` to a new file named `properties.yaml`, then update the target API endpoints and active authorization credentials.
   * Copy `hibernate.cfg2.xml.example` to a new file named `hibernate.cfg.xml`, then populate it with your actual MS SQL database endpoint parameters.

2. **Build the Application:** Execute the Maven package phase. This compiles the code, builds the decoupled core application JAR, and automatically runs the `maven-dependency-plugin` to isolate external dependencies into the external centralized directory:
   ```bash
   mvn clean package
   ```
3. **Execute the Application:** Run the compiled JAR file natively using the Java CLI. Thanks to the automated manifest configuration in `pom.xml`, the classpath linking to the shared `../dependencies/` folder is resolved out of the box, and the internal logging engine dynamically manages its output file storage targets relative to your current execution scope:
   ```bash
   java -jar target/Jackson_GS_Passportization-0.0.1-SNAPSHOT.jar
   ```
   
