# abc_bank — Setup & Usage Instructions

**abc_bank** is a full-stack FinTech banking application built with a **Spring Boot** backend and a **React** single-page client. It supports core banking operations such as deposit, withdrawal, and fund transfers, with JWT-based authentication, role-based access control, and SMTP notifications.

---

## 1. Prerequisites

Before running the project, install the following:

| Tool      | Version                              | Purpose                                  |
|-----------|--------------------------------------|------------------------------------------|
| Java JDK  | 21 or higher                         | Compile & run the Spring Boot backend    |
| Maven     | 3.8+ (or use the bundled `mvnw`)     | Build & dependency management            |
| MySQL     | 8.0+                                 | Application database                     |
| Git       | Latest                               | Source control                           |

---

## 2. Clone the Repository

```bash
git clone https://github.com/RahulMondal03/abc_bank.git
cd abc_bank
```

---

## 3. Database Setup

1. Start your MySQL server.
2. Create the application database:
   ```sql
   CREATE DATABASE bank;
   ```
3. By default, the application connects with username `root`. Update credentials in `src/main/resources/application.properties` if your local setup differs.

> **Security Note:** Do not commit real credentials, JWT secrets, or mail passwords. Use environment variables or a local secrets file for production deployments.

---

## 4. Configure Application Properties

Open `src/main/resources/application.properties` and verify the following settings:

```properties
server.port=8090

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/bank
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD

# JPA
spring.jpa.hibernate.ddl-auto=update

# JWT
jwt.secret.string=YOUR_JWT_SECRET
jwt.expirtation.time=2592000000

# Mail (SMTP)
spring.mail.username=YOUR_SMTP_USERNAME
spring.mail.password=YOUR_SMTP_APP_PASSWORD
spring.mail.host=smtp.gmail.com
spring.mail.port=587
```

---

## 5. Build the Project

Use the bundled Maven wrapper:

```bash
# Linux / macOS
./mvnw clean install

# Windows
mvnw.cmd clean install
```

---

## 6. Run the Application

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Once started, the API is available at `http://localhost:8090`.

---

## 7. Run Tests

```bash
./mvnw test
```

---

## 8. Project Structure

```
abc_bank/
├── pom.xml                          # Maven build configuration
├── mvnw, mvnw.cmd                   # Maven wrapper scripts
└── src/
    ├── main/
    │   ├── java/com/abc_bank/abc_bank/
    │   │   ├── AbcBankApplication.java   # App entry point
    │   │   ├── config/                   # App configuration
    │   │   ├── security/                 # JWT, filters, auth
    │   │   ├── auth_users/               # User entity & auth
    │   │   ├── notification/             # SMTP / notifications
    │   │   ├── exceptions/               # Custom exceptions
    │   │   ├── enums/                    # Account, Transaction enums
    │   │   └── res/                      # Response wrappers
    │   └── resources/
    │       └── application.properties
    └── test/                             # JUnit test sources
```

---

## 9. Key Features

- Account management (deposit, withdrawal, transfer)
- JWT-based authentication & role-based access control
- Global exception handling with custom error responses
- SMTP email notifications for transactional events
- AWS S3 integration for file storage
- Spring Boot Actuator for monitoring

---

## 10. Common Issues

| Issue                          | Solution                                                         |
|--------------------------------|------------------------------------------------------------------|
| Port 8090 already in use       | Change `server.port` in `application.properties`                 |
| MySQL connection refused       | Verify MySQL is running and credentials match the config         |
| JWT signature errors           | Ensure `jwt.secret.string` is at least 32 characters             |
| SMTP authentication failure    | Use an app-specific password (Gmail requires 2FA + app password) |

> **Tip:** For development, set `spring.jpa.show-sql=true` in `application.properties` to see generated SQL queries in the console.

---

## 11. License

See repository for license details.
