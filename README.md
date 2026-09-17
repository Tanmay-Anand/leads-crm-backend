# Leads CRM API

A reduced CRM backend covering three modules — **Leads**, **Projects** and **Channel Partners** —
built by adapting the existing Leadrat services rather than designing something new.

| Module | Adapted from |
| --- | --- |
| Lead, lead status, temperature, tag, source taxonomy | `builder-crm-pre-sales-api` |
| Project | `builder-crm-platform-api` (`project` package) |
| Channel Partner | `builder-crm-platform-api` (`channel/partner` package) |
| Tenancy, search, exceptions, core entity bases | `builder-crm-pre-sales-api` |

Spring Boot 4.1.0, Java 21, PostgreSQL, AWS Cognito.

## Running it

```bash
docker compose up -d
```

```bash
cp .env.example .env && set -a && source .env && set +a && ./mvnw spring-boot:run
```

- API: `http://localhost:8090/leads-crm`
- Swagger UI: `http://localhost:8090/leads-crm/swagger-ui.html`
- Health: `http://localhost:8090/leads-crm/actuator/health`

Port 8090 rather than 8080, so this can run alongside the existing services.

### Cognito

Every endpoint except the docs and actuator requires a Cognito ID token. Provision a pool with:

```bash
ADMIN_EMAIL=you@company.com AWS_PROFILE=<your-profile> ./scripts/provision-cognito.sh
```

Read the script first. It creates a user pool, the four role groups, a public app client, and one
`TENANT_ADMIN` user, then prints the values for both `.env` files. It never handles a password: the
first user gets a temporary one by email and sets a real one through the sign-in screen.

Until a pool is configured the app still starts and serves its docs; authenticated requests return
401.

### What the token must carry

| Claim | Why |
| --- | --- |
| `custom:tenantId` | `TenantAware` scopes every query by it. Without it a request fails with 412 unless it sends `x-tenant-id`. |
| `cognito:groups` | Mapped onto Spring authorities. One of `TENANT_ADMIN`, `TENANT_USER`, `PLATFORM_ADMIN`, `PLATFORM_USER`. |
| `sub` | Recorded as the acting user on every write. |

## Architecture

The conventions are the reference service's, not new ones.

**Multi-tenancy is structural.** Every tenant-scoped entity extends `TenantAwareAggregateRoot`,
which brings a `tenant` column, a `@SQLRestriction` that hides soft-deleted rows, and the
`tenantFilter` definition. `TenantFilterAspect` enables that filter before any call on a
`TenantAwareRepository`, so a repository method that forgets to name the tenant is still scoped.
`TenantFilter` establishes the tenant per request from `x-tenant-id` or the JWT, and clears it in a
`finally` block — a leaked `ThreadLocal` would serve one tenant another's rows on a pooled thread.

**Deletes are soft.** `TenantAwareRepositoryImpl` turns `delete` into an `is_active` flip and
refuses every bulk variant, because a bulk delete would bypass the per-entity checks.

**Search has two layers.** A free-text `q` across named fields (`SimpleSearchSpecification`), and a
structured filter registry (`search/advanced`). The registry is the interesting half: each module
declares its filterable fields as an enum implementing `FilterFieldSpec`, and
`GET /leads/filter-fields` publishes that metadata so the UI builds its filter drawer from the
server. Adding a field to `LeadFilterField` adds a control in the UI with no frontend change.

**Per-module layout** is `Entity`, `Repository`, `Service`, `ServiceImpl`, `Controller`, `dto/`.

## Deviations from the reference

Each of these was a deliberate call; they are the places where this repo and the originals differ.

### 1. Hibernate owns the schema; there is no Flyway

The reference runs `ddl-auto: validate` against 36 Flyway migrations. Here `ddl-auto: update`
generates the schema on boot (requested explicitly). Consequences worth knowing: the unique
constraints are declared on the entities (`uk_leads_tenant_project_mobile`,
`uk_channel_partner_tenant_email`, `uk_project_tenant_name`) rather than in SQL, there is no
migration history, and a column rename will leave the old column behind.

The reference's partial unique index over `COALESCE(project_id, ...)` cannot be expressed as a JPA
constraint, so a Postgres unique constraint over `(tenant, project_id, mobile_normalized)` is used
instead. Postgres treats NULLs as distinct in a unique constraint, so **the database does not stop
two project-less leads sharing a mobile**; the service-layer guard
(`findFirstByTenantAndProjectIdIsNullAndMobileNormalized...`) is what enforces it, and it is not
race-proof for that one case. Adding the partial index by hand restores the guarantee.

### 2. Tenant seeding is lazy, not provisioned

The reference seeds a tenant from an SQS provisioning message and self-heals at startup. There is
no queue here, so `TenantSeedingService.ensureSeeded` runs on the first master-data read and is
idempotent. A brand-new tenant gets six lead statuses, three temperatures, four tags and a source
taxonomy on first use.

### 3. Channel Partner is one entity, not two

`platform-api` splits this into a global `ChannelPartner` identity (unique by email across all
builders, with OTP sign-in for a partner portal) and a per-builder `ChannelPartnerEnrollment`. The
split exists to let one firm enrol with many builders and log into a portal. There is no portal
here, so the entity is modelled on the tenant-scoped `ChannelPartnerEnrollment` and named
`ChannelPartner`. Email is unique per tenant rather than globally.

### 4. Project references stay opaque

`Lead.projectId` and `Lead.channelPartnerId` are plain UUID columns with no JPA association and no
FK, exactly as in the reference — where those entities live in another service. Keeping the shape
means the filter and search layers port unchanged, and it is why deleting a project or partner that
still has leads is refused in the service layer (409) rather than by the database.

Display names (`channelPartnerName`, `assignedToUserName`, `telecallerName`) are denormalised onto
the lead, as in the reference: they appear on every list row and are searchable scopes.

### 5. There is no user module

The reference resolves owners from a user service. Here the owner is a name typed on the form and
stored on the lead, and the `users` filter dropdown is built from
`LeadRepository.findDistinctAssignees` — the names already on leads. A user who has never held a
lead is not offered as a filter value.

### 6. Trimmed from the lead module

Kept: CRUD, soft delete, lead-code sequence, pagination, free-text search, the advanced filter
registry, status transitions with the note requirement, notes, tags, temperature, source, summary
counts, duplicate checking.

Dropped: bulk CSV upload, PDF/Excel export, lead→buyer conversion and booking callbacks, site
visits, enquiries and campaigns, attribution windows and CP claims, Javers audit trails, custom
fields, webhooks, caching, SQS and S3.

### 7. Other simplifications

- No Redis cache, no `@Cacheable`; every read hits Postgres.
- No Javers, so there is no field-level audit trail. `created`/`modified`/`createdBy`/
  `lastModifiedBy` are still populated by JPA auditing.
- `MasterTemperature` / `MasterTag` / `MasterSourceCategory` (the global catalogues the reference
  copies into each tenant) are not reproduced; only the tenant-scoped `Custom*` entities exist, and
  the seeder writes them directly.
- `LeadSubStatus` is dropped; a lead has a status and no sub-status.
- The JWT decoder is built in `CognitoJwtDecoderConfig` from the JWKS endpoint rather than from
  `issuer-uri`. Boot resolves `issuer-uri` during startup, which means an unprovisioned pool takes
  the whole application down with a network error instead of a configuration message. Issuer
  validation is reattached explicitly.
- Boot 4 dropped `spring-boot-starter-aop`, so `aspectjweaver` is declared directly.

## Known environment issue

On this machine the JVM cannot open the loopback socket Tomcat needs, and startup fails with
`Unable to establish loopback connection`. Passing a writable socket directory fixes it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir=C:\jtmp"
```

## Verified

- `mvn compile` — clean.
- Boots against the Docker Postgres; Hibernate creates all 11 tables in schema `crm`.
- 42 endpoints across 9 tags published at `/v3/api-docs`.
- Unauthenticated `/leads`, `/projects`, `/channel-partners` all return 401; docs and actuator are
  public.

Business flows behind authentication have not been exercised end-to-end, because that needs a real
Cognito pool.
