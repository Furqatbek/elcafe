# Deploying the demo (demo.restos.uz)

A public box, seeded with invented data, for showing the product to ZBR and to prospective venues.
Separate from production in every way that matters: its own database, its own containers, its own
origin.

**Why a subdomain and not `restos.uz/demo/...`.** Tenant identity here comes from the logged-in
user's token, not from the URL — there is no hostname or path that selects a restaurant anywhere in
the codebase, and customer URLs carry a numeric venue id (`/order/menu/3/T1`), not a name. A path
like `/demo/{restaurantName}` would therefore be decorative. It would also mean re-nesting the whole
site (`/`, `/admin`, `/order`, `/api`, `/uploads`, both WebSocket routes) under a prefix, rebuilding
both SPAs with changed basenames, and putting demo sessions on the same origin as the real site,
sharing cookies and localStorage with it. A subdomain needs none of that: the app already serves
exactly this layout at a hostname root.

---

## 0. Which box

**The demo needs a host of its own.** This is the same `docker-compose.yml` production uses: it binds
host ports `80` and `443` and its containers have fixed names (`elcafe-backend`, `elcafe-nginx-proxy`,
…). Pointing `demo.restos.uz` at a server that is already running the stack does not add a virtual
host — it collides on both the ports and the names, and the second `up` fails or takes the first one
down.

A small VPS is enough: one venue, no traffic, no backups.

## 1. DNS

One A record, `demo.restos.uz` → the demo VPS IP. Nothing else; no wildcard is needed.

Wait for it to resolve before step 2 — certbot validates over HTTP and will fail against a name that
does not yet point anywhere.

```bash
dig +short demo.restos.uz
```

## 2. Certificate

```bash
sudo mkdir -p /var/www/certbot
sudo certbot certonly --standalone -d demo.restos.uz
```

This writes `/etc/letsencrypt/live/demo.restos.uz/`, which is the path
`nginx-proxy/nginx-demo.conf` expects. Compose already mounts `/etc/letsencrypt` read-only, and
`/var/www/certbot` alongside it for renewals.

Port 80 must be free while certbot runs — stop the stack first if it is already up.

**Then switch renewal to webroot, once, after the stack is up (step 4).** Issuing with `--standalone`
records *standalone* as the renewal method, and every unattended `certbot renew` after that will fail
against an nginx holding port 80 — quietly, until the certificate expires in front of whoever you are
demoing to.

```bash
sudo certbot certonly --webroot -w /var/www/certbot -d demo.restos.uz \
  --cert-name demo.restos.uz --force-renewal
```

One extra issuance, well inside Let's Encrypt's limits, and renewal is unattended from then on.

## 3. Configure

```bash
git clone <repo> && cd elcafe
cp .env.demo.example .env.demo
nano .env.demo
```

Fill the five required values. **Generate fresh secrets** — do not reuse production's. A demo box is
reached by people you do not control, and its credentials should be worth nothing anywhere else.

The template already sets what makes it a demo: `SPRING_PROFILES_ACTIVE=prod,demo`, the demo nginx
config, and the demo's public URLs.

Worth thirty seconds before starting the stack, because a bad proxy config otherwise shows up as a
container restarting in a loop:

```bash
docker run --rm -v "$PWD/nginx-proxy/nginx-demo.conf":/etc/nginx/conf.d/default.conf:ro \
  nginx:alpine nginx -t
```

It will complain that the certificate is missing if you skipped step 2 — that is the check working.

## 4. Start

```bash
docker compose --env-file .env.demo up -d --build
```

First boot runs migrations, creates the platform operator from `ADMIN_EMAIL`/`ADMIN_PASSWORD`, then
seeds the demo venue: a menu with variants and recipes, a venue admin, and a ZBR partner with a 15%
channel markup and a menu-only grant (step 7).

## 5. Take the partner key

The seeder prints it **once**. It is hashed the moment it is stored and cannot be shown again — a
demo box whose key nobody wrote down needs the partner deleted and recreated in the admin panel.

```bash
docker compose --env-file .env.demo logs backend | grep "Demo partner key"
```

Keep it with the venue login. Both go to ZBR privately.

## 6. Check before showing anyone

```bash
curl -s https://demo.restos.uz/health                       # → OK
curl -s https://demo.restos.uz/api/v1/actuator/health       # → {"status":"UP",...}

# The menu ZBR will pull, at channel prices (32000 base → 36800 at +15%, rounded to 500)
curl -s -H "X-Partner-Key: <the key>" \
  https://demo.restos.uz/api/v1/partner/menu/1 | head -40
```

Then open `https://demo.restos.uz/admin` and log in as the venue admin. The dashboard is empty until
orders exist.

## 7. Order push is off, deliberately

The seeded grant is **menu-only**. A `POST /api/v1/partner/orders` against this box answers `403`
until you turn it on, and that is what we told ZBR the staging grant would do: read and write a menu
now, live orders after one has gone end to end with somebody watching.

It is also refused at the source. A partner whose `paymentMode` we have not confirmed cannot be
granted order push at all (V194) — ZBR send `PREPAID` as a constant today, and `PREPAID` books the
order as paid.

To demo an order, or when ZBR are ready, turn both on as the platform operator:

```bash
# 1. Say the partner's paymentMode is real (or, for a demo, that you accept it is not)
curl -X PATCH "https://demo.restos.uz/api/v1/partners/1/payment-mode-confirmed?confirmed=true" \
  -H "Authorization: Bearer <operator token>"

# 2. Re-grant the venue with order push
curl -X PUT "https://demo.restos.uz/api/v1/partners/1/restaurants/1" \
  -H "Authorization: Bearer <operator token>" -H "Content-Type: application/json" \
  -d '{"canReadMenu":true,"canPushOrders":true,"priceAdjustmentType":"PERCENT","priceAdjustmentValue":15,"priceRounding":500}'
```

Do them in that order — the second is refused while the first has not been done, which is the whole
point of it.

---

## What is on the box

| | |
|---|---|
| Venue | Qahvoon Demo |
| Admin panel | `https://demo.restos.uz/admin` — `demo@restos.uz` / `DemoPass123!` |
| Partner API | `https://demo.restos.uz/api/v1/partner` — key from step 5, **menu-only** until step 7 |
| Menu | Osh (Regular / Large), Lagman, Somsa, Green tea |
| Channel markup | ZBR pays +15%, rounded to the nearest 500 |

**Somsa sells itself out after three orders.** It needs two pastry sheets and six are in stock. That
is deliberate: it makes auto sold-out a thing you can show in a minute rather than describe. Restock
the ingredient in the admin panel to put it back.

**Green tea has no recipe**, and stays on sale whatever the stock. Also deliberate — it is the
honest answer for an item nobody has entered ingredients for, and worth pointing at when someone
asks what happens to items without recipes.

## What is deliberately not there

- **Tables and QR codes.** Two minutes in the admin panel, and walking through creating one is a
  better demo than finding it already done.
- **Order history.** Fabricated history shown as if it were real is not something a demo should
  teach anyone to trust. Push a few orders live instead, once step 7 has switched push on.
- **The customer ordering app.** It needs an SMS to log a customer in, and the prod profile refuses
  to start with OTP development mode on — that mode accepts any code, which on a public box is
  account takeover. Configuring a real SMS provider is the only way, and means demo traffic sends
  real messages. Decide that deliberately rather than by default.
- **Payments.** Payme and Click are left blank. A demo must not be able to take money.

## Day 2

| What | How |
|---|---|
| Update | `git pull && docker compose --env-file .env.demo up -d --build` |
| Logs | `docker compose --env-file .env.demo logs -f backend` |
| Reset the demo | `docker compose --env-file .env.demo down -v && ... up -d` — drops the volume, reseeds from scratch, **issues a new partner key** |
| Certificate renewal | Unattended, via the webroot switch in step 2. Check it works before you need it: `sudo certbot renew --dry-run` |

Restarting is safe: the seeder looks for its own venue by name and leaves everything alone if it
finds it. Only `down -v` reseeds, and that invalidates the partner key ZBR is holding.

## Before this becomes a second production

It is not one, and a few things would need saying out loud if it started being treated as one: no
Sentry, no SMTP, no backups configured, and one replica with the WebSocket and rate-limit caveats in
[DEPLOYMENT_TOPOLOGY.md](./DEPLOYMENT_TOPOLOGY.md). For a demo none of that matters. For anything
real, [LAUNCH.md](./LAUNCH.md) is the document.
