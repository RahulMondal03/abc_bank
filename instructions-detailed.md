# abc_bank — Detailed Instructions with Thought Process

This guide expands on the basic setup with the **reasoning** behind each step,
the **trade-offs** considered, and the **design decisions** baked into the
abc_bank project. Use this as a learning reference, not just a runbook.

---

## 1. Prerequisites — Why These Specific Tools?

| Tool | Version | Why this choice |
|------|---------|-----------------|
| Java JDK 21 | LTS | Java 21 is an LTS release with virtual threads (Project Loom), pattern matching, and record patterns — relevant for high-throughput banking workloads. Spring Boot 4.x requires Java 17+, so 21 future-proofs the project. |
| Maven | 3.8+ | Maven is declarative and integrates cleanly with the Spring ecosystem. The wrapper (`mvnw`) is bundled so contributors don't need a system Maven install — this avoids "works on my machine" version drift. |
| MySQL | 8.0+ | Chosen over PostgreSQL here for familiarity and broad hosting support. For a banking context, the **real** requirement is ACID compliance and row-level locking, both of which MySQL InnoDB satisfies. |
| Git | Latest | Required for branch-based workflow and CI/CD triggers. |

> **Thought process:** The stack favors **maturity over novelty**. Banking apps
> prioritize correctness, auditability, and operational tooling — all areas
> where this stack is battle-tested.

---

## 2. Cloning — Branch Strategy

```bash
git clone https://github.com/RahulMondal03/abc_bank.git
cd abc_bank
```

**Why a single repo (monorepo-lite)?**
The backend lives here while the React frontend may live elsewhere. Keeping the
Spring Boot service isolated lets backend CI run independently — faster feedback,
smaller blast radius on deploys.

---

## 3. Database Setup — Design Decisions

```sql
CREATE DATABASE bank;
```

**Why `ddl-auto=update` in dev?**
`spring.jpa.hibernate.ddl-auto=update` lets Hibernate evolve the schema as
entities change. This is **convenient in dev** but **dangerous in prod** because:

- It can silently drop or alter columns based on entity changes.
- It bypasses migration history — no rollback story.

> **Production recommendation:** Switch to `validate` and use **Flyway** or
> **Liquibase** for explicit, versioned migrations. Banking compliance
> typically requires a full audit trail of schema changes.

**Why MySQL InnoDB specifically?**
- Row-level locking → concurrent transfers don't block each other unnecessarily.
- Foreign key enforcement → referential integrity for `User → Account → Transaction`.
- Transactional DDL is limited, but DML transactions are ACID — critical for
  money movement.

---

## 4. Application Properties — Security Thought Process

The default `application.properties` contains **plaintext credentials** for
convenience. In production, this is a critical vulnerability.

### Layered approach to secrets

| Environment | Where secrets live |
|-------------|-------------------|
| Local dev | `application-local.properties` (gitignored) |
| CI | GitHub Actions encrypted secrets |
| Staging/Prod | AWS Secrets Manager / Parameter Store, injected as env vars |

**Why externalize?**
1. **Rotation** — secrets can be rotated without rebuilding the JAR.
2. **Least privilege** — devs don't see prod credentials.
3. **Audit** — secret access is logged separately.

### JWT secret sizing

```properties
jwt.secret.string=YOUR_JWT_SECRET   # MUST be ≥32 chars for HS256
jwt.expirtation.time=2592000000     # 30 days in ms
```

> **Trade-off:** A 30-day token simplifies UX but **expands the blast radius**
> if a token is stolen. For banking, consider:
> - Short access tokens (15 min) + refresh tokens (7 days)
> - Token revocation list (Redis) for instant logout
> - Device fingerprinting to detect stolen tokens

---

## 5. Build — Why Use the Wrapper?

```bash
./mvnw clean install
```

**`clean install` thought process:**
- `clean` removes `target/` to avoid stale class files (a real source of bugs
  after refactors).
- `install` runs tests *and* installs the artifact to the local `~/.m2`
  repository — useful if other local projects depend on this one.

> **CI shortcut:** Use `./mvnw -DskipTests package` only when you've **already**
> verified tests pass in a separate step. Skipping tests during release builds
> is a classic source of regressions.

---

## 6. Run — Profile-Driven Configuration

```bash
./mvnw spring-boot:run
```

**Why Spring profiles matter:**
Spring Boot supports `application-{profile}.properties`. Run with:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

This lets you swap DB URLs, log levels, and SMTP settings without code changes.

---

## 7. Tests — What to Cover

```bash
./mvnw test
```

**Banking test priorities (in order):**
1. **Transaction integrity** — money never disappears or duplicates. Test
   concurrent transfers, rollback on failure, idempotency keys.
2. **Authorization** — every endpoint enforces role checks; tested with
   `@WithMockUser` and forged JWTs.
3. **Input validation** — negative amounts, overflow, currency mismatches.
4. **Audit logging** — every state-changing operation produces an immutable log.

> **Coverage is not correctness.** 100% line coverage with weak assertions is
> worse than 60% coverage with strong invariants. Use mutation testing (PIT)
> if you want to measure assertion quality.

---

## 8. Project Structure — Why This Layout?

```
src/main/java/com/abc_bank/abc_bank/
├── AbcBankApplication.java
├── config/         # Cross-cutting beans
├── security/       # Auth filters, JWT, CORS
├── auth_users/     # User domain
├── notification/   # SMTP / event-driven notifications
├── exceptions/     # Custom exception types + global handler
├── enums/          # Shared enums (AccountType, TransactionStatus, etc.)
└── res/            # Response wrappers
```

**Why feature-based (not layer-based) packaging?**
A layer-based layout (`controllers/`, `services/`, `repositories/`) **scales
poorly**: every feature touches every package, making PRs sprawl. Feature-based
packaging (`auth_users/`, `notification/`) keeps related code together and
makes it easier to extract modules later.

**Why a separate `enums/` package?**
Enums are shared across features (a transaction has a `TransactionType`, a
notification references an `AccountType`). Putting them in a shared package
avoids circular dependencies between feature modules.

---

## 9. Key Features — Design Rationale

### JWT vs. Session Cookies
- **JWT chosen** because the frontend is a React SPA on a different origin —
  stateless tokens work better than cookies + CSRF tokens.
- **Trade-off:** JWTs can't be revoked without a denylist. Mitigation: short
  expiry + refresh tokens.

### Global Exception Handler
- Centralizes error responses → consistent JSON shape for the frontend.
- Hides internal stack traces from clients (security: avoid information leakage).

### SMTP Notifications
- **Synchronous SMTP in service methods is risky** — a slow mail server can
  block a transfer. Future improvement: publish a domain event, consume
  asynchronously (Spring `@Async` or a real message broker).

### AWS S3 Integration
- Used for documents (e.g., KYC uploads). S3 chosen for:
  - 11-nines durability
  - Server-side encryption (SSE-KMS)
  - Lifecycle policies for compliance retention

---

## 10. Common Issues — Root-Cause Lens

| Symptom | Surface fix | Root cause to investigate |
|---------|-------------|---------------------------|
| Port 8090 in use | Change port | A previous instance didn't shut down — check process tree |
| MySQL connection refused | Restart MySQL | Connection pool exhausted? Check `maxActive` |
| JWT signature errors | Regenerate secret | Secret rotated mid-deploy without grace period |
| SMTP auth failure | Use app password | Don't store passwords in repo — use OAuth2 for Gmail |

> **Debugging thought process:** When something fails, ask **"what changed?"**
> first (deploy, config, dependency), then **"what's the actual error?"**
> (full stack trace, not just the message), then **"what's the simplest
> reproduction?"**.

---

## 11. Production Readiness Checklist

Before deploying anything money-handling:

- [ ] Secrets externalized (no plaintext in repo)
- [ ] HTTPS enforced (HSTS header, redirect from HTTP)
- [ ] Rate limiting on auth endpoints (prevent credential stuffing)
- [ ] Database backups verified by restore drill
- [ ] Audit log immutable and retained per regulation (e.g., 7 years)
- [ ] Monitoring: latency, error rate, saturation (Actuator + Prometheus)
- [ ] Alerting on: failed transfers, auth anomalies, unusual transaction volumes
- [ ] Disaster recovery RTO/RPO defined and tested
- [ ] Penetration test completed and findings remediated

---

## 12. License

See repository for license details.
