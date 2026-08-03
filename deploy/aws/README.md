# AWS EC2 Deployment

Deploys the Spring Boot backend to one EC2 host:

- Java 25
- AWS RDS PostgreSQL (managed outside this host — no local database container)
- systemd units named `repair-shop-saas@<service>`
- nginx terminating TLS for **https://api.ggfix.in**, path-routed to ports 8081-8092
- GitHub Actions SSH deployment with a post-deploy health gate

## Layout

| File | Purpose |
| --- | --- |
| `install-ec2.sh` | One-time host prep: Java 25, app user, `/opt/repair-shop-saas` |
| `deploy-from-artifact.sh` | Installs jars from the bundle, writes `.env` (RDS), restarts units |
| `setup-nginx-tls.sh` | Installs nginx + certbot, publishes the vhost, obtains/renews the cert |
| `nginx-ggfix-api.conf` | The vhost: `/auth/`, `/ticket/`, … → `127.0.0.1:80xx`, plus `/health` |
| `health-check.sh` | Post-deploy gate — probes every service through the public domain |
| `repair-shop-saas@.service` | systemd template unit |
| `TLS-SETUP.md` | Background on why TLS matters here + the manual equivalent |

## Routing

nginx maps one path prefix per service, so every app talks to a single HTTPS origin:

| Prefix | Port | Service | Prefix | Port | Service |
| --- | --- | --- | --- | --- | --- |
| `/auth/` | 8081 | auth-service | `/marketplace/` | 8087 | marketplace-service |
| `/ticket/` | 8082 | ticket-service | `/pickup/` | 8088 | pickup-service |
| `/user/` | 8083 | user-service | `/notification/` | 8089 | notification-service |
| `/shop/` | 8084 | shop-service | `/subscription/` | 8090 | subscription-service |
| `/technician/` | 8085 | technician-service | `/master/` | 8091 | master-data-service |
| `/inventory/` | 8086 | inventory-service | `/order/` | 8092 | order-service |

## Health checks

Every service inherits `spring-boot-starter-actuator` from `services/pom.xml`. Only
the health endpoint is web-exposed and details are hidden, so the public surface is
just `UP`/`DOWN`:

```
https://api.ggfix.in/health                    -> nginx edge   {"status":"UP","component":"nginx"}
https://api.ggfix.in/auth/actuator/health      -> auth-service {"status":"UP"}
https://api.ggfix.in/<prefix>/actuator/health  -> …one per service
```

The workflow fails the deploy if any required service is not `UP`. Run the same
gate by hand at any time:

```bash
bash deploy/aws/health-check.sh                                  # https://api.ggfix.in
API_BASE=http://15.206.175.125 bash deploy/aws/health-check.sh   # direct IP, pre-TLS
OPTIONAL_SERVICES="technician-service inventory-service" bash deploy/aws/health-check.sh
```

| Env | Default | Meaning |
| --- | --- | --- |
| `API_BASE` | `https://api.ggfix.in` | Origin to probe |
| `RETRIES` / `RETRY_DELAY` | `30` / `10` | Edge retry budget |
| `SERVICE_RETRIES` | `12` | Per-service retry budget once the edge is up |
| `OPTIONAL_SERVICES` | *(empty)* | Services allowed to be down without failing |

## DNS + firewall prerequisites

Before the first TLS deploy:

1. **A record** — `api.ggfix.in` → the EC2 public IP, TTL 300. certbot's HTTP-01
   challenge resolves this name, so it must already point at the deploy host.
   The workflow verifies this and stops early if the record and `EC2_HOST`
   disagree.
2. **Security group** — allow inbound **tcp/80** and **tcp/443** from `0.0.0.0/0`.
   Port 80 must stay open for certificate renewal.

Once TLS is live the public `8081-8092` ingress rules can be dropped; nginx
reaches the services over loopback.

## One-time EC2 setup

```bash
bash deploy/aws/install-ec2.sh
```

`deploy-from-artifact.sh` rewrites `/opt/repair-shop-saas/.env` on every deploy so
the services point at RDS, preserving the JWT secret and Cloudinary values across
deploys. Edit it to trim `SERVICES`:

```bash
sudo nano /opt/repair-shop-saas/.env
```

Each service needs roughly 300 MB of heap, so all twelve want a ~4 GB instance. On
a smaller box shorten `SERVICES`, and mirror that list in `OPTIONAL_SERVICES` so
the health gate agrees.

## GitHub secrets

| Secret | Required | Notes |
| --- | --- | --- |
| `EC2_HOST` | yes | Public IP or DNS of the deploy target |
| `EC2_USER` | yes | `ec2-user` on Amazon Linux, `ubuntu` on Ubuntu AMIs |
| `EC2_SSH_PRIVATE_KEY_B64` | yes | Base64 of the private key: `base64 -w0 < key` |
| `EC2_KNOWN_HOSTS` | yes | Output of `ssh-keyscan -H <EC2_HOST>` |
| `DB_PASSWORD` | first deploy | RDS password. Not defaulted in the scripts — this repo is public. Reused from the host's `.env` on later deploys |
| `CERTBOT_EMAIL` | no | Expiry notices; defaults to `admin@ggfix.in` |

`EC2_KNOWN_HOSTS` is host-specific — regenerate it whenever `EC2_HOST` changes, or
the deploy fails with `Host key verification failed`.

Run **Deploy Backend to AWS EC2** from the Actions tab, or push to `main`.

Manual-run inputs: `api_domain`, `skip_tls` (port 80 only, no certbot),
`skip_health_check` (deploy without gating).

## Troubleshooting

```bash
sudo systemctl status repair-shop-saas@auth-service
sudo journalctl -u repair-shop-saas@auth-service -f
sudo nginx -t && sudo systemctl reload nginx
sudo certbot certificates
```

A deploy that fails the health gate automatically prints unit status,
failed-unit logs, the nginx config test, certificate state, and disk usage from
the host.
