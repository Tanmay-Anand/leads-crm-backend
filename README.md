# Leads CRM API

A CRM backend covering **Leads**, **Projects**, **Channel Partners**, **Meetings**, and **User
Management with two-layer RBAC** — built by adapting the existing Leadrat services rather than
designing something new.

| Module | Adapted from |
| --- | --- |
| Lead, lead status, temperature, tag, source taxonomy | `builder-crm-pre-sales-api` |
| Project | `builder-crm-platform-api` (`project` package) |
| Channel Partner | `builder-crm-platform-api` (`channel/partner` package) |
| User, Role, permission catalogue, Cognito provisioning | Ported from `builder-crm`'s RBAC model - see [Authorization](#authorization-rbac) below |
| Lead meetings, reminder emails | New for this project |
| Tenancy, search, exceptions, core entity bases | `builder-crm-pre-sales-api` |

Spring Boot 4.1.0, Java 21, PostgreSQL, AWS Cognito. Also embeds `sales-sdk` (the AI Query SDK) as
a library dependency - see [AI Query SDK](#ai-query-sdk-sales-sdk) below.

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

## Authorization (RBAC)

Two layers, not one:

- **Layer 1 - the Cognito group.** `UserRole` (`PLATFORM_ADMIN`, `PLATFORM_USER`, `TENANT_ADMIN`,
  `TENANT_USER`) is the hard ceiling, resolved from the JWT's `cognito:groups` claim.
- **Layer 2 - an optional tenant-defined custom `Role`.** A flat JSONB list of `action:resource`
  strings (`api/rbac/CrmPermission` is the full catalogue). A `Role` can only **subtract** from
  the ceiling Layer 1 already grants, never add to it - `PermissionService.deriveDefaultPermissions`
  computes the ceiling, and `EffectivePermissionLoader` intersects a custom role's permissions
  against it. A user with no custom role gets the full ceiling for their group.

`User.id` **is** the Cognito `sub`, not a separate column - existing `Lead.assignedTo` values and
`LeadScope.MINE` need no migration, and "is this me?" guards need no query. Users are provisioned
in Cognito via `AdminCreateUser`/`AdminSetUserPassword`/`AdminEnableUser`/`AdminDisableUser`
(`api/cognito/DefaultCognitoService`, `NoopCognitoService` as the local-dev fallback when no pool
is configured) and mirrored in a local `crm."user"` row on the same write path.

**Every controller carries exactly one guard** - either a role annotation
(`@AuthenticatedOnly`/`@TenantAdminOnly`/etc., for endpoints with no resource permission of their
own) or `@PreAuthorize("@permissionService.check('action', 'resource')")`. Stacking both on one
method throws `AnnotationConfigurationException` *at first request*, not at startup - see
`PermissionService`'s class doc. `ControllerGuardCoverageTest` reflects over every
`@RestController` and fails the build if that invariant, or a `check(...)` call naming a
permission absent from the catalogue, is ever violated.

Six job-function system roles (Sales Agent, Presales Executive, Relationship Manager, Customer
Service Representative, Channel Partner Representative, Team Manager) are seeded per tenant
alongside the two ceiling roles (Tenant Admin, Tenant User) - **not** Platform Admin/Platform User,
which are Leadrat's own cross-tenant staff identities and have no business being offered as an
assignable "custom role" for a tenant's own employees.

`view:ai-briefing` exists in the catalogue for the AI pre-meeting briefing feature
(`pre-meeting-briefing-assistant`), modelling which roles are meant to see it - it is not yet
enforced anywhere, since that feature lives in a separate app that only checks a shared bearer
token today.

## AI Query SDK (`sales-sdk`)

`pom.xml` pulls in [`sales-sdk`](https://github.com/anshikleadrat/sales-sdk) (published as
`ai-query-sdk`) as a library dependency, mounted directly inside this app rather than run
separately. It adds:

- A natural-language query endpoint (`/ai-sdk/query`) over this app's own JPA entities.
- Meeting capture: a Google Calendar link plus a Recall.ai notetaker bot, with the transcript
  stored against the lead.
- WhatsApp chat context (Engageto), surfaced through `/ai-sdk/...` and consumed by the frontend's
  WhatsApp Chat tab on a lead.

It runs entirely in this process against this app's own `DataSource` (a private, enforced-read-only
`EntityManagerFactory` - no separate database credentials), with its own tiny admin UI at
`/ai-sdk/setup` → `/ai-sdk/auth` → `/ai-sdk/settings` → `/ai-sdk/configure` → `/ai-sdk/meetings` →
`/ai-sdk/console`.

**Dependency coordinate gotcha, already hit once:** `sales-sdk`'s own published Maven coordinates
(`com.leadrat:ai-query-sdk`) point at a `distributionManagement` URL its publish workflow has never
actually used - no version has ever been tagged/published there, and resolving it fails the build
with "could not be found". The working coordinate is JitPack's, which builds straight from the
source repo with no auth and no publish step:

```xml
<dependency>
    <groupId>com.github.anshikleadrat</groupId>
    <artifactId>sales-sdk</artifactId>
    <version><!-- a full commit SHA - no tag exists yet --></version>
</dependency>
```

Pin to a tag once one exists; a commit SHA is the safe default until then, since (unlike a floating
branch) it cannot be redefined out from under this build.

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

### 5. The user module now exists, and is not a straight port

`README.md` used to say outright that this CRM had no concept of a user - the lead owner was a
name typed on the form, and the assignee filter dropdown was built from
`LeadRepository.findDistinctAssignees` (the names already on leads, nothing more). That's been
replaced by a real `User` module and the two-layer RBAC model described above, ported from
`builder-crm`'s model but not copied as-is - see the porting spec's "Deviations from builder-crm"
for the full list, the two structural ones being:

- **`User.id` is the Cognito `sub`** (`builder-crm` has a separate id column plus a `cognitoSub`
  column). Load-bearing for the reasons in [Authorization](#authorization-rbac).
- **A custom role can only subtract from its holder's Cognito-group ceiling, never add to it.**
  `builder-crm`'s reference model has no equivalent invariant, and its `TENANT_USER` default
  includes `add:users` - letting a `TENANT_USER` create a `TENANT_ADMIN` and hand themselves the
  keys. That default is not replicated here.

The lead assignee dropdown (`LeadServiceImpl`'s `"users"` filter-option case) is now backed by
real `User` rows instead of `findDistinctAssignees`, which has been deleted.

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

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for the full picture: GitHub Actions builds and pushes to ECR,
then deploys to a single EC2 host over SSM (`infra/prod/start.sh`). Two real bugs already hit here,
worth knowing before touching either file again:

- The `sales-sdk` dependency coordinate trap described above - it silently fails the *build*, not
  the deploy, so it shows up as a `DependencyResolutionException` rather than an unhealthy
  container.
- An unconfigured mail sender previously failed Spring Boot's own health indicator, which made
  `start.sh`'s health poll never succeed even though the app had booted fine and was actually
  serving requests - fixed, but a reminder that "container won't report healthy" and "container
  crashed" are different failure modes worth telling apart from the (deliberately short,
  last-50-lines) logs `start.sh` prints on a failed deploy.

## Known environment issue

On this machine the JVM cannot open the loopback socket Tomcat needs, and startup fails with
`Unable to establish loopback connection`. Passing a writable socket directory fixes it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir=C:\jtmp"
```

## Verified

- `mvn compile` — clean.
- Boots against the Docker Postgres; Hibernate creates the full schema, including `crm."user"` and
  `crm.role`, on `ddl-auto: update`.
- Unauthenticated requests to every business endpoint return 401; docs and actuator stay public.
- Cognito pool provisioned in `ap-south-1` by `scripts/provision-cognito.sh` and wired into `.env`
  (which is gitignored). The pool has the `custom:tenantId` attribute, the four role groups, a
  public app client with SRP and no secret, and one `TENANT_ADMIN` user carrying
  `custom:tenantId`.
- CORS preflight from the Vite dev origin returns 200 with the matching
  `Access-Control-Allow-Origin`.
- `ControllerGuardCoverageTest` passes: every `@RestController` handler has exactly one guard, and
  every `@PreAuthorize("@permissionService.check(...)")` names a permission the catalogue actually
  has.
- End-to-end, locally: sign in as an existing `TENANT_ADMIN` with no local row yet → `/users/me`
  JIT-provisions it; a tenant not yet seeded gets the two ceiling roles plus the six job-function
  roles on the next master-data read; role/permission changes apply within the cache's 60s window
  (immediately after the eviction event, in practice); a disabled action re-enabled in devtools
  still gets a real 403 from the backend, not just a hidden frontend button.

**Known gap, not yet fixed:** creating a user, resetting a password, or enabling/disabling one all
call Cognito Admin APIs, which need AWS credentials with access to the pool in `.env`
(`AWS_COGNITO_USER_POOL_ID`). Whoever's credentials are active on the machine running this app -
locally via the default credential chain, or the EC2 instance role in production - must actually
have permission to call `AdminCreateUser`/`AdminSetUserPassword`/`AdminEnableUser`/
`AdminDisableUser`/`AdminAddUserToGroup`/`AdminRemoveUserFromGroup` scoped to that pool.
`scripts/grant-cognito-admin.sh` grants exactly that to the production instance role; the
equivalent for local dev is making sure whoever's running this has a working AWS profile for the
account that owns the pool - a `ResourceNotFoundException: User pool ... does not exist` on any of
those calls means the active credentials belong to the wrong AWS account, not that the pool ID is
wrong.
