# TLS / HTTPS setup for the ggfix backend

**Problem this fixes:** every app currently talks to `http://13.205.198.41:80xx` in
cleartext, and the mobile apps force-allow cleartext (`usesCleartextTraffic` /
`NSAllowsArbitraryLoads`). On any shared/hostile network a MITM can read and
alter traffic — JWTs (valid ~365 days), login passwords/OTPs, Aadhaar/PAN KYC
uploads, GPS, and customer device-unlock PINs. TLS is **server/infra work** — it
cannot be fixed in the app alone.

You cannot get a certificate for a bare IP, so **step 0 is a DNS name.**

---

## Recommended: nginx reverse proxy on the EC2 box (path-based, single cert)

One domain, TLS terminated by nginx, requests proxied to each service port by URL
path prefix. The apps already support path-prefixed bases (the client resolves
`https://api.example.com/ticket/…` correctly), so only `.env` changes on the app
side.

### 0. DNS
Point an A record at the EC2 IP, e.g. `api.ggfix.example.com → 13.205.198.41`.
Open ports **80** and **443** in the EC2 security group (you can later close the
raw `80xx` service ports to the public and bind them to `127.0.0.1` only).

### 1. Install nginx + certbot
```bash
sudo apt-get update
sudo apt-get install -y nginx
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/bin/certbot
```

### 2. nginx site — TLS + path-based proxy to each service
`/etc/nginx/sites-available/ggfix-api` (certbot fills in the TLS lines in step 3):
```nginx
map $http_upgrade $connection_upgrade { default upgrade; '' close; }

server {
    listen 80;
    server_name api.ggfix.example.com;
    # certbot adds the HTTPS server + redirect; this block is the starting point.

    client_max_body_size 25m;   # KYC / media uploads

    # One location per service → its localhost port. Keep the trailing slashes.
    location /auth/         { proxy_pass http://127.0.0.1:8081/; }
    location /ticket/       { proxy_pass http://127.0.0.1:8082/; }
    location /user/         { proxy_pass http://127.0.0.1:8083/; }
    location /shop/         { proxy_pass http://127.0.0.1:8084/; }
    location /technician/   { proxy_pass http://127.0.0.1:8085/; }
    location /inventory/    { proxy_pass http://127.0.0.1:8086/; }
    location /marketplace/  { proxy_pass http://127.0.0.1:8087/; }
    location /pickup/       { proxy_pass http://127.0.0.1:8088/; }
    location /notification/ { proxy_pass http://127.0.0.1:8089/; }
    location /subscription/ { proxy_pass http://127.0.0.1:8090/; }
    location /order/        { proxy_pass http://127.0.0.1:8092/; }

    # master-data-service also serves @RequestMapping("/media") and the actuator,
    # so /master/ cannot simply stop stripping. Longest-prefix wins:
    #   /master/brands          -> :8091/master/brands   (clean, preferred)
    #   /master/master/brands   -> :8091/master/brands   (legacy, shipped APKs)
    #   /master/media/upload    -> :8091/media/upload
    #   /master/actuator/health -> :8091/actuator/health
    location /master/master/   { proxy_pass http://127.0.0.1:8091/master/; }
    location /master/media/    { proxy_pass http://127.0.0.1:8091/media/; }
    location /master/actuator/ { proxy_pass http://127.0.0.1:8091/actuator/; }
    location /master/          { proxy_pass http://127.0.0.1:8091/master/; }

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
}
```
```bash
sudo ln -s /etc/nginx/sites-available/ggfix-api /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### 3. Get the certificate (auto-renews)
```bash
sudo certbot --nginx -d api.ggfix.example.com
# certbot edits the server block to listen 443 ssl + redirects 80→443.
systemctl list-timers | grep certbot   # confirm the renew timer exists
```

### 4. Point the apps at HTTPS and remove cleartext
In each app's `.env` (customer / shop / employee):
```
EXPO_PUBLIC_AUTH_BASE=https://api.ggfix.example.com/auth
EXPO_PUBLIC_TICKET_BASE=https://api.ggfix.example.com/ticket
EXPO_PUBLIC_USER_BASE=https://api.ggfix.example.com/user
EXPO_PUBLIC_SHOP_BASE=https://api.ggfix.example.com/shop
EXPO_PUBLIC_MASTER_DATA_BASE=https://api.ggfix.example.com/master
EXPO_PUBLIC_ORDER_BASE=https://api.ggfix.example.com/order
# …one per service, all https, no ports
```
Then in each app's `app.config.js` **remove** the cleartext opt-ins:
- iOS: drop `NSAppTransportSecurity: { NSAllowsArbitraryLoads: true }`.
- Android: drop `usesCleartextTraffic: true` from the expo-build-properties plugin.
Rebuild the apps (native change) and re-point the admin site (`repair-shop-admin`
`.env.local`) at the HTTPS base too.

### 5. (defense in depth)
- Shorten the JWT lifetime + add refresh-token rotation (a captured token is
  valid ~1 year today).
- Rotate `JWT_SECRET` after TLS is live (tokens have been flowing in cleartext).
- Optionally add certificate pinning in the apps.

---

## Alternative: AWS ALB + ACM (managed TLS, no box maintenance)
Put an Application Load Balancer in front, attach a free **ACM** cert for the
domain, and add one HTTPS listener rule per path prefix → a target group on each
service port. No certbot/renewal to manage. More AWS setup, same app-side `.env`
change as step 4.

---

## Verify
```bash
curl -sS https://api.ggfix.example.com/master/media/ping        # 200 over TLS
curl -sSv http://13.205.198.41:8081/ 2>&1 | grep -i 'refused\|timed out'  # raw ports closed
```
