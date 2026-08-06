# CLAUDE.md

Guidance for AI assistants working in this repository. Keep it current when structure, conventions, or workflows change.

## Project overview

`abc_bank` is a Spring Boot **backend** for a banking application. The README describes a broader full-stack system (React SPA, Docker, CI/CD, AWS S3, notifications). Only the Spring Boot backend exists in this repo today — the frontend, containerization, and CI/CD pipelines are aspirational and not present. Treat the README as marketing copy; treat this file as the source of truth for what actually exists.

Implemented today:
- Auth: registration, login (JWT), fetch current user, admin get-user-by-id, change password, request/confirm password reset by emailed code.
- Domain models + JPA repositories for `User`, `Role`, `Account`, `Transaction`, `Notification`, `PasswordResetCode`.
- SMTP email notifications (async), persisted to a `notifications` table.
- JWT-based stateless security with method-level `@PreAuthorize`.
- Global exception handling with a uniform response envelope.

Not implemented yet (data model exists but no service/controller):
- Account create/close/list, deposit, withdrawal, transfer, transaction history endpoints.
- Anything frontend, Docker, CI/CD, or AWS S3 (the `software.amazon.awssdk:s3` dependency is on the classpath but unused).

## Tech stack

- Java 21, Spring Boot **4.0.1** (uses Jakarta EE 11 + Jackson 3 — imports like `tools.jackson.databind.ObjectMapper` are correct, not typos).
- Maven via the wrapper (`./mvnw`).
- MySQL (JDBC) via `spring-boot-starter-data-jpa`, Hibernate DDL `update`.
- Spring Security 6 + `io.jsonwebtoken:jjwt` 0.13.0 for JWT.
- Thymeleaf (for email templates), Spring Mail (SMTP).
- Lombok, ModelMapper 3.2.6.
- AWS SDK v2 `s3` on the classpath (unused).
- Spring Boot Actuator enabled.

## Layout

```
src/main/java/com/abc_bank/abc_bank/
├── AbcBankApplication.java     # @SpringBootApplication, @EnableAsync
├── config/AppConfig.java       # ModelMapper, Thymeleaf template engine beans
├── res/Response.java           # Generic <T> response envelope for all endpoints
├── security/                   # JWT filter, TokenService, UserDetailsService, CORS, SecurityFilter (chain)
├── exceptions/                 # Custom exceptions + GlobalExceptionHandler + auth entry point/denial handler
├── enums/                      # AccountType, AccountStatus, Currency, TransactionType, TransactionStatus, NotificationType
├── auth_users/                 # {controller, services, repo, entity, dtos}  — fully wired
├── role/                       # {repo, entity}                              — used by auth
├── accounts/                   # {repo, entity, dtos}                        — no service/controller yet
├── transaction/                # {repo, entity, dtos}                        — no service/controller yet
└── notification/               # {services, repo, entity, dtos}              — async email sender
src/main/resources/application.properties
src/test/java/.../AbcBankApplicationTests.java  # contextLoads only
```

Every feature package follows the same subpackage layout: `controller/`, `services/` (interface + `*Impl`), `repo/` (Spring Data JPA), `entity/`, `dtos/`. Follow this shape when adding new features (e.g. the missing accounts/transaction layers).

## Conventions

**Responses.** All controllers return `ResponseEntity<Response<T>>` where `Response<T>` (`res/Response.java`) is `{statusCode, message, data, meta}` with `@JsonInclude(NON_NULL)`. Build it via `Response.<T>builder()…build()`. Do not return raw domain objects.

**Errors.** Throw one of the custom exceptions in `exceptions/`:
- `BadRequestException` → 400
- `NotFoundException` → 404
- `InsufficientBalanceException` → 400
- `InvalidTransactionException` → 400
`GlobalExceptionHandler` maps them; any uncaught `Exception` becomes 500. Auth failures/access denials are handled by `CustomAuthenticationEntryPoint` (401) and `CustomAccessDenialHandler` (403) — note the misspelling in the class name; keep it or plan a rename PR, don't silently introduce a second variant.

**DTOs vs entities.** Entities are persistence-only; controllers/services deal in DTOs. `UserDTO` uses `@JsonManagedReference`/`@JsonBackReference` to break the User↔Account↔Transaction cycle — preserve those annotations when editing. Passwords are always nulled out of `UserDTO` before returning (`UserServiceImpl.toDto`), plus `UserDTO.password` is `@JsonIgnore` — belt-and-suspenders, keep both.

**Dependency injection.** Use `@RequiredArgsConstructor` on `final` fields — no field or setter injection. Existing services follow this pattern.

**Lombok.** `@Data @Builder @NoArgsConstructor @AllArgsConstructor` on entities/DTOs; `@Slf4j` for logging. The Spring Boot plugin already excludes Lombok from the built jar (`pom.xml`).

**Validation.** Put `jakarta.validation` constraints on DTOs and use `@Valid` on controller `@RequestBody`. See `RegistrationRequest`, `LoginRequest`, `UpdatePasswordRequest`.

**Transactions.** Use `jakarta.transaction.Transactional` on service methods that write (see `UserServiceImpl.registerUser`, `resetPassword`).

**Authorization.** Use `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` at the controller method for role gates (see `UserController.getUserById`). `@EnableMethodSecurity` is on. Default role assigned at registration is `ROLE_CUSTOMER` (see `UserServiceImpl.DEFAULT_ROLE`); roles are auto-created on first use.

**Naming.** Repositories end in `Repo` (`UserRepo`, not `UserRepository`). Service interface + `Impl` class (e.g. `UserService`/`UserServiceImpl`). Message strings in exceptions/responses are lowercase (`"user not found"`, `"login successful"`) — match that voice.

## Security model

- Stateless (`SessionCreationPolicy.STATELESS`), CSRF disabled, CORS wired in (`CorsConfig` allows all origins today — dev-only; tighten before shipping).
- `AuthFilter` runs before `UsernamePasswordAuthenticationFilter`, pulls `Authorization: Bearer <jwt>`, resolves the user via `CustomerUserDetailsService`, and populates `SecurityContext`.
- `/api/auth/**` is `permitAll()`; everything else requires authentication.
- JWT secret and expiration are read from `jwt.secret.string` / `jwt.expirtation.time` (yes, misspelled — the property key is `expirtation`; keep that spelling or migrate everywhere at once). Signing is HS256.
- Password hashing: BCrypt.

## Config

`src/main/resources/application.properties` — server on **:8090**, MySQL at `localhost:3306/bank`, JWT and SMTP settings.

**Warning:** the checked-in `application.properties` contains real-looking secrets (DB password, Gmail app password, JWT secret). Do not commit further secrets. If touching this file, prefer moving values to environment variables (`${DB_PASSWORD}` etc.) and updating the property references — but confirm with the user before rotating anything, since these values may already be in use.

## Known gotchas

- **`AbcBankApplication` sends a test email on every startup** via a `CommandLineRunner` bean that hardcodes recipient `tezziconic@gmail.com`. This is dev scaffolding — expect it to fire against real SMTP whenever the app boots. Consider removing before touching the boot sequence.
- **Account & Transaction have no service/controller layer.** If a task asks you to implement deposit/withdraw/transfer or account CRUD, you are building it from scratch on top of the existing entities/repos. Follow the auth_users package as the template.
- **`CorsConfig` uses `addAllowedOrigin("*")`.** Comment in the file already flags this as dev-only.
- **Spring Boot 4 / Jackson 3.** `ObjectMapper` lives at `tools.jackson.databind.ObjectMapper` in this project (see `CustomAccessDenialHandler`, `CustomAuthenticationEntryPoint`) — do not "fix" this to `com.fasterxml.jackson.databind`.
- **`Account.BALANCE` is uppercase.** The field is `BigDecimal BALANCE` (not `balance`). Lombok generates `getBALANCE()`/`setBALANCE(...)`. Keep the name unless you're doing a deliberate rename with a migration.
- **`Notification.createdAt` is `final`** with a field initializer — it's stamped at object construction, not on persist. If you need on-persist behavior, switch to `@PrePersist`.
- **`spring.jpa.hibernate.ddl-auto=update`** — schema drifts from entity changes; no migration tool (Flyway/Liquibase) is set up.

## Build, run, test

Use the Maven wrapper — do not assume `mvn` is installed.

```bash
./mvnw clean package        # build (skips tests? no — runs them)
./mvnw spring-boot:run      # run against localhost MySQL on :8090
./mvnw test                 # run tests (currently only contextLoads)
./mvnw test -Dtest=AbcBankApplicationTests   # single test
```

Running the app locally needs a MySQL instance matching `application.properties` and outbound SMTP to Gmail (or the startup `CommandLineRunner` will error out on the async send — it's caught and logged, not fatal, but noisy).

## Working with git in this repo

- Active development branch: `claude/add-claude-documentation-AoNHe` (per session instructions). Push here, not to `main`.
- Do **not** open a PR unless the user explicitly asks. Commit + push is the default deliverable.
- Commit messages in history are terse and lowercase (`"add user auth service and controllers"`, `"connected to database"`). Match that style.
- `.gitignore` covers `target/`, `.idea/`, `*.class`, `HELP.md`, and various binaries — do not check in build output.

## When adding a new feature

1. Create a package under `com.abc_bank.abc_bank.<feature>/` with `entity/`, `repo/`, `dtos/`, `services/`, `controller/`.
2. Entity: `@Entity @Data @Builder @Table(name="…") @AllArgsConstructor @NoArgsConstructor`, `Long id` with `GenerationType.IDENTITY`.
3. Repo: extend `JpaRepository<Entity, Long>`; add query-method finders.
4. DTO: mirror the entity, `@JsonInclude(NON_NULL)`, use `@JsonManagedReference`/`@JsonBackReference` if it references another DTO that references back.
5. Service interface + `@Service @Slf4j @RequiredArgsConstructor` impl; wrap writes in `@Transactional`; return `Response<T>`.
6. Controller: `@RestController @RequestMapping("/api/<feature>") @RequiredArgsConstructor`; `@Valid` on bodies; `@PreAuthorize` for role gates.
7. Throw domain exceptions from `exceptions/`; don't invent new HTTP-mapping code — extend `GlobalExceptionHandler` if a new exception type is needed.
