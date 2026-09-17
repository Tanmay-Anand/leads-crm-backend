# Deployment

One environment, deploying from `main`. The API runs as a container on a single EC2 host behind
nginx, against managed Postgres on RDS; the frontend is a static build on AWS Amplify Hosting.

```
GitHub push to main
  └─ Actions: build jar → Jib → push image to ECR
       └─ SSM send-command → host pulls image, docker compose up -d
            └─ poll /actuator/health until UP

Browser ──HTTPS──> Amplify Hosting ──┬──> static React bundle
                                     └──> /leads-crm/* proxied ──> EC2 (nginx :80 → api :8090)
                                                                          └──> RDS Postgres
```

## Why it is shaped this way

**EC2 and docker compose, not ECS** — this is the house pattern. `platform-api`'s
`build-deploy.sh` runs `docker compose up -d` from `infra/<env>/`, and the reference workflows SSH
to a host and call a deploy script. Fargate would have been a different architecture, not a
smaller one, and an ALB alone costs more per month than the whole of this.

**OIDC, not an SSH private key.** The reference stores `SSH_PRIVATE_KEY_*` in repository secrets.
This uses GitHub's OIDC provider to assume an IAM role scoped to
`repo:Tanmay-Anand/leads-crm-backend:ref:refs/heads/main`, so there are no long-lived AWS
credentials anywhere in GitHub, and a leaked secret cannot be replayed. Shell access to the host is
SSM Session Manager, so **port 22 is never opened and there is no key pair**.

**Built in CI, not on the host.** The reference pulls source onto the box and runs Maven there.
A t3.small carrying a JDK, a Maven cache and a git checkout is a waste of a small box, and it means
the host needs repository credentials. Building in Actions and shipping an image to ECR removes
both problems, and tagging by commit SHA makes rollback a tag change rather than a rebuild.

**Amplify proxies the API rather than CloudFront fronting it.** Amplify serves the frontend over
HTTPS, and a browser blocks an HTTPS page calling an HTTP API as mixed content. The original plan
was CloudFront terminating TLS in front of the instance, but a new AWS account cannot create
distributions until AWS verifies it. Amplify rewrites `/leads-crm/*` through to the host instead,
which turns out to be better: the browser only ever talks to its own origin, so there is no mixed
content **and no CORS at all** — the single most error-prone line in the whole deployment stops
existing. `provision-amplify.sh` keeps an `API_MODE=direct` path for when CloudFront is available.

**RDS rather than a Postgres container on the app host.** The data survives the instance being
terminated, which `destroy-infra.sh` does routinely; it brings automated backups and
point-in-time recovery; and it returns ~200MB of RAM on a 2GB box where the JVM was competing with
Postgres. The master password is created with `--manage-master-user-password`, so RDS generates it
into Secrets Manager and **no human ever sees or types it** — `infra/prod/start.sh` resolves it at
container start using the instance role, and it never touches the filesystem.

## First-time setup

Needs an AWS profile with permission for EC2, ECR, IAM, SSM and CloudFront.

**1. Cognito** (already done if you followed the README):

```bash
ADMIN_EMAIL=you@example.com AWS_PROFILE=personal ./scripts/provision-cognito.sh
```

**2. Compute, registry and roles:**

```bash
AWS_PROFILE=personal ./scripts/provision-infra.sh
```

Creates the ECR repository with a lifecycle policy, the instance role, the GitHub OIDC provider
and deploy role, a security group, a t3.small running Amazon Linux 2023 with Docker installed, and
an Elastic IP. Prints the values you need next.

**3. Database:**

```bash
AWS_PROFILE=personal ./scripts/provision-rds.sh
AWS_PROFILE=personal ./scripts/init-rds-schema.sh
```

The first creates the instance (5–10 minutes) and grants the host read access to its secret. The
second creates the `crm` schema, which Hibernate needs but will not create itself.

**4. Host configuration:**

```bash
AWS_PROFILE=personal ./scripts/configure-host.sh
```

Copies the compose stack to `/opt/leads-crm` and writes its `.env`, discovering every value from
the resources already provisioned. Nothing secret is written — `.env` names the Secrets Manager
ARN rather than a password.

For a shell on the box, without SSH:

```bash
aws ssm start-session --target <instance-id> --profile personal --region ap-south-1
```

**5. GitHub repository secrets** — Settings → Secrets and variables → Actions:

| Secret | Value |
| --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | printed by `provision-infra.sh` |
| `EC2_INSTANCE_ID` | printed by `provision-infra.sh` |
| `SERVICE_URL` | the CloudFront URL, printed by `provision-cloudfront.sh` |

**6. Amplify Hosting** — see [the frontend README](../leads-crm-frontend/README.md#deployment),
or `scripts/provision-amplify.sh` for the CLI path. Environment variables to set on the app:

| Variable | Value |
| --- | --- |
| `VITE_SERVER_URL` | the CloudFront URL |
| `VITE_DOMAIN` | the Amplify URL |
| `VITE_AWS_COGNITO_USER_POOL_ID` | from `provision-cognito.sh` |
| `VITE_AWS_COGNITO_USER_POOL_CLIENT_ID` | from `provision-cognito.sh` |
| `VITE_AWS_REGION` | `ap-south-1` |

**7. Close the loop on CORS.** Set `APP_CORS_ORIGINS` in the host `.env` to the Amplify origin,
exactly, scheme included, then `docker compose up -d`. This is the step most likely to be
forgotten, and it fails only in the browser: the API answers `curl` perfectly while every request
from the deployed UI is blocked.

## Deploying

Push to `main`. The workflow builds, pushes, deploys and then polls health for up to five minutes,
failing if the service never reports `UP`.

Roll back by re-running the workflow from an earlier commit, or on the host:

```bash
sed -i 's|^API_IMAGE=.*|API_IMAGE=<registry>/leads-crm-backend:<older-sha>|' /opt/leads-crm/.env
docker compose --env-file .env up -d
```

## Cost

Roughly **$30/month** at list price: EC2 t3.small ~$15, RDS db.t4g.micro ~$12, EBS 20GB ~$1.80,
Secrets Manager $0.40, ECR pennies. Elastic IP, SSM and IAM are free, and Amplify Hosting is free
at this scale.

On the AWS Free Plan this comes out of credits rather than a card, and the plan itself is the
spending guardrail: it blocks chargeable operations at the API — attempting to resize the instance
to `t3.medium` returns `FreeTierRestrictionError` rather than quietly costing money. That is a
harder guarantee than any budget alert, which is evaluated from billing data hours after the fact.

Tear everything down with `./scripts/destroy-infra.sh`. It asks for confirmation, and it leaves the
Cognito pool alone because that holds your users and costs nothing.

## Things worth knowing before this carries real data

- **RDS is single-AZ with one day of backups.** No failover, and a 24-hour recovery window.
  `--multi-az` and a longer retention are one flag each, and both cost more.
- **The API is reachable over plain HTTP by IP.** Port 80 is open because Amplify's edge has no
  fixed range to narrow the security group to. Every endpoint requires a Cognito token, but the
  traffic between Amplify and the host is unencrypted. CloudFront fixes both once the account is
  verified.
- **`ddl-auto: update` runs in production.** An entity change rewrites the schema on the next
  deploy with no review step, and it will not drop or rename anything. This follows from the
  no-Flyway decision; see the README.
- **One instance, so a deploy is a short outage.** `docker compose up -d` stops the old container
  before the new one is healthy. Zero-downtime needs a second instance and a load balancer.
- **No alerting.** CloudWatch collects the logs; nothing is watching them.
