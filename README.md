# Stock Reservation API

A REST API built with Java, Spring Boot, Spring Data JPA, and MySQL to manage product inventory and stock reservations.

The API supports creating reservations for multiple products, confirming reservations, cancelling active reservations, and maintaining stock consistency.

## Features

- Retrieve available product inventory.
- Create a reservation containing one or more products.
- Validate product IDs and requested quantities.
- Prevent reservations when stock is insufficient.
- Confirm active reservations.
- Cancel active reservations and release reserved stock.
- Prevent stock from being released more than once when a cancellation is repeated.
- Handle invalid reservation state transitions.
- Persist inventory and reservation data in MySQL.
- Use database transactions and pessimistic locking to help prevent concurrent requests from reserving the same stock.

## Technology Stack

- Java 21
- Spring Boot 3.3.4
- Spring Web MVC
- Spring Data JPA / Hibernate
- MySQL 8 / H2 persistent database
- Maven and Maven Wrapper
- Jakarta Bean Validation
- JUnit and Mockito

## Prerequisites

Install the following:

- Java 21
- MySQL 8
- Git (if cloning the repository)

Verify Java and Maven Wrapper availability:

```bash
java -version
./mvnw -version
```

## Database Setup

Create the development database in MySQL:

```sql
CREATE DATABASE stock_reservation;
```

Create a MySQL user for the application if you do not already have one. Grant it the necessary privileges on the development database.

Configure the database connection using environment variables.

macOS/Linux terminal:

```bash
export DB_USERNAME=reservation_app
export DB_PASSWORD='your_mysql_password'
```

Windows PowerShell:

```powershell
$env:DB_USERNAME="reservation_app"
$env:DB_PASSWORD="your_mysql_password"
```

Replace the example password with the password configured for your MySQL user.

The application uses these connection settings:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/stock_reservation}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
```

The default database URL can be overridden using `DB_URL`. The username defaults to `root` if `DB_USERNAME` is not set.

### Sample Inventory

After the application creates the `products` table, you can insert sample inventory:

```sql
INSERT INTO products (sku, name, total_stock, reserved_stock)
VALUES
('LAPTOP-001', 'Laptop', 10, 0),
('MOUSE-001', 'Wireless Mouse', 20, 0),
('KEYBOARD-001', 'Keyboard', 15, 0);
```

Run the insert statement only when these SKUs do not already exist.

## Run the Application

From the project root:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The application uses port `8080` by default.

## API Endpoints

### 1. List Products

```http
GET /api/products
```

Returns the products and their inventory information.

### 2. Create a Reservation

```http
POST /api/reservations
Content-Type: application/json
```

Example request:

```json
{
  "items": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 2,
      "quantity": 1
    }
  ]
}
```

The product IDs must exist in the database, and the requested quantities must be positive and available.

### 3. Confirm a Reservation

```http
POST /api/reservations/{reservationId}/confirm
```

Confirms an active reservation. Repeating confirmation for an already confirmed reservation is intended to be idempotent.

### 4. Cancel a Reservation

```http
POST /api/reservations/{reservationId}/cancel
```

Cancels an active reservation and releases its reserved stock. Repeating cancellation must not release stock a second time.

## Error Handling

The API validates request fields and checks product availability and reservation states.

Expected error scenarios include:

- Invalid request data.
- A product ID that does not exist.
- Insufficient inventory.
- A reservation ID that does not exist.
- An invalid reservation state transition.

Check the actual HTTP status codes and response bodies returned by the implementation when testing these scenarios.

## Run Tests

Run the full test suite:

```bash
# Windows
.\mvnw.cmd test

# macOS / Linux
./mvnw test
```

Build the project:

```bash
# Windows
.\mvnw.cmd clean package

# macOS / Linux
./mvnw clean package
```

The test suite includes unit tests and database-backed integration tests. The integration tests use a separate test database to avoid modifying development inventory.

### Test Database

Create the test database:

```sql
CREATE DATABASE stock_reservation_test;
```

Configure `src/test/resources/application.properties` to use `stock_reservation_test`. Ensure the configured MySQL user has the necessary privileges.

The current test configuration uses Hibernate schema generation. **Back up any important data in the test database before running tests**, because schema generation may recreate tables.

For the concurrency test, run:

```bash
./mvnw -Dtest=StockReservationApiIntegrationTest#testConcurrencyCompetingRequests test
```

## Example Demo Sequence

1. Start MySQL and configure the database credentials.
2. Start the Spring Boot application.
3. Retrieve inventory using `GET /api/products`.
4. Create a reservation using `POST /api/reservations`.
5. Confirm it using `POST /api/reservations/{reservationId}/confirm`, or cancel an active reservation using the cancellation endpoint.
6. Retrieve inventory again and verify the stock changes.
7. Run the test suite using `./mvnw test`.

## Project Structure

```text
stock-reservation-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/analyticalrx/inventory/
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── exception/
│   │   │       ├── model/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application-postgres.properties
│   └── test/
│       ├── java/
│       │   └── com/analyticalrx/inventory/
│       │       └── StockReservationApiIntegrationTest.java
│       └── resources/
├── output ss/
│   ├── 01-product-inventory.png
│   ├── 02-create-reservation.png
│   ├── 03-reservation-confirmation.png
│   └── 04-automated-test-results.png
├── .mvn/
├── .gitignore
├── pom.xml
├── mvnw
├── mvnw.cmd
├── README.md
├── PLAN.md
├── AI_USAGE.md
└── docker-compose.yml
```

The exact package and folder names may vary as the project evolves.

## Limitations and Future Improvements

- Add API documentation using OpenAPI/Swagger.
- Improve structured error responses and error status consistency.
- Add further integration tests for concurrent confirmation and cancellation scenarios.
- Consider database migration tooling for controlled schema changes.

Update this section to reflect the actual limitations and unfinished work in the submitted version.
