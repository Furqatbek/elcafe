# Instagram Integration — Setup & Operator Guide

This is the operator's guide to connecting a restaurant's own Instagram Business account to
Qahvoon: creating the Meta app, wiring the webhook, filling in the Settings tab, and running the
integration day to day. It is grounded directly in the backend implementation
(`com.elcafe.modules.instagram`) and the admin frontend (`frontend/src/pages/InstagramMarketing.jsx`)
— every endpoint, config key and behavior below is what the code actually does, not what Meta's
generic docs describe in the abstract.

**Last Updated:** 2026-07-26

## Table of Contents

1. [Overview](#overview)
2. [Meta App Setup](#meta-app-setup)
3. [Generating the Long-Lived Page Access Token](#generating-the-long-lived-page-access-token)
4. [Webhook Configuration](#webhook-configuration)
5. [Filling In the Settings Tab](#filling-in-the-settings-tab)
6. [Private Replies](#private-replies)
7. [Security & Operations](#security--operations)
8. [Configuration Reference](#configuration-reference)
9. [Campaigns](#campaigns)
10. [Consent & Opt-out (STOP)](#consent--opt-out-stop)
11. [Troubleshooting](#troubleshooting)
12. [Go-Live Checklist](#go-live-checklist)
13. [Source](#source)

---

## Overview

Instagram is a **per-tenant channel**: every restaurant connects its *own* Meta app and its *own*
Instagram Business/Creator account. There is no shared platform-wide Instagram account — each
restaurant's credentials, subscribers and campaigns are isolated from every other restaurant's
(`instagram_bot_config`, `instagram_subscribers` and friends all carry a `restaurant_id`, enforced by
migration `V163__instagram_tenant_scoping.sql`). This replaced an earlier single-account design; if
you find old references to "the platform's Instagram bot," they predate V163.

Once connected, the integration does several things:

1. **DM registration wizard.** A stateful conversation (`InstagramBotService`) that greets a new
   Instagram DM contact, collects their name, phone, birthday (skippable) and one or more delivery
   addresses, and — if the phone number matches an existing `Customer` for that restaurant — links
   the subscriber to that customer record automatically.

   | Conversation state | Meaning |
   |---|---|
   | `AWAITING_NAME` | Initial state for a new contact, or after "hi" / "start" restarts the wizard |
   | `AWAITING_PHONE` | Name captured, waiting for a phone number |
   | `AWAITING_BIRTHDAY` | Phone captured, waiting for a birth date (`DD.MM.YYYY`) or `/skip` |
   | `AWAITING_ADDRESS` | Waiting for a delivery address (text) or `/skip` |
   | `AWAITING_MORE_ADDRESSES` | One address saved; quick-reply buttons ask "add another?" |
   | `REGISTERED` | Wizard complete — further messages just get a short menu prompt |

   This state is visible per-subscriber in the Subscribers tab's "Registration" column. Only the
   **welcome message** (the greeting before "what's your name?") is configurable per restaurant — the
   wizard's own prompts (name/phone/birthday/address questions) are fixed Uzbek-language strings in
   `InstagramBotService`, not translated or configurable today.

2. **Comment auto-reply.** When enabled, a templated public reply is posted to new comments on the
   business's posts (`InstagramWebhookService.processChangeEvent` → `InstagramApiClient.replyToComment`).

3. **Marketing campaigns.** Async, paced, resumable broadcast DMs to a restaurant's own subscribers
   (all active ones, or only fully-registered ones), respecting Meta's 24-hour customer-initiated
   messaging window. See [Campaigns](#campaigns).

4. **Order & reservation DMs.** When a subscriber is linked to a `Customer` (the wizard links by phone
   automatically), that customer's order-status changes and reservation confirmed/reminder/cancelled
   events are also delivered as plain-text Instagram DMs — the same four touchpoints Telegram already
   served, now fanned out to every channel the customer is reachable on (`CustomerNotificationService`
   is a channel-neutral orchestrator over `CustomerMessagingChannel`; Telegram keeps its rich HTML,
   Instagram sends plain text). Best-effort and isolated: an Instagram outage never blocks the Telegram
   send, or vice-versa.

5. **Private replies from comments.** A comment containing a configured keyword gets a *private* DM via
   Meta's private-reply endpoint — the "comment MENU and we'll DM you" growth loop — optionally carrying
   a one-time coupon code. See [Private Replies](#private-replies).

6. **Statistics.** A tenant-scoped `GET /api/v1/instagram/subscribers/statistics` (surfaced as stat
   cards across the top of the Subscribers tab) reports subscriber counts (total / active / registered /
   new-this-week) and, from `instagram_logs`, the message send breakdown (sent / delivered / failed).

7. **First-contact UX.** When a config is activated, the bot pushes a default persistent menu (📋 Menyu /
   📍 Manzil / 📞 Aloqa) and ice-breaker questions to Meta's messaging profile, so a first-time visitor
   sees tappable options and suggested questions before typing anything — no messaging window needed. The
   push is best-effort: a failure (bad token, Meta down, kill switch off) is logged and never blocks the
   activation itself.

Everything is managed from the **Instagram Marketing** admin page (Subscribers / Broadcast / Settings
tabs), backed by REST endpoints under `/api/v1/instagram/*`. Managing the Settings tab and running
campaigns requires the `ADMIN`, `OWNER`, or `MANAGER` role for that restaurant; permanently erasing a
subscriber's personal data is restricted to `ADMIN`/`OWNER`.

---

## Meta App Setup

### 1. Create a Meta app

At [developers.facebook.com](https://developers.facebook.com), create a new app of type **Business**.

### 2. Add the Instagram product and link the account

Add the **Instagram** product to the app (the Messenger-platform-style "Instagram API with Facebook
Login" flow — this codebase uses a **Page Access Token** and calls `graph.facebook.com`, not the
newer standalone Instagram-Login flow). This requires:

- An Instagram **Business or Creator** (professional) account.
- That account linked to a **Facebook Page** the restaurant (or you, during setup) administers —
  link it from the Instagram app: Settings → Account → Linked Accounts → Facebook, or from the Page's
  own settings.
- A Facebook Developer account with at least **Moderate** access on that Page.

### 3. Request the permissions the code actually uses

The webhook and send paths in this codebase exercise specific Graph API edges — grant exactly the
scopes those edges need:

| Scope | Why it's needed | Where it's exercised in code |
|---|---|---|
| `instagram_basic` | Baseline read access to the connected IG account; required for any Instagram Platform product | N/A — platform prerequisite |
| `instagram_manage_messages` | Send and receive DMs | `InstagramApiClient.postToMessagesApi` → `POST /{instagram_account_id}/messages` |
| `instagram_manage_comments` | Read and reply to comments | `InstagramApiClient.replyToComment` → `POST /{comment_id}/replies` — **required if you'll use comment auto-reply**; easy to miss since it's not always on a generic Instagram-DM-bot checklist |
| `pages_manage_metadata` | Subscribe the linked Page to this app's webhook fields | one-time step wiring the Webhooks product (see [Webhook Configuration](#webhook-configuration)) |
| `pages_show_list` | List/select the linked Page while minting the access token | token-generation step, see below |

> **Note on `pages_messaging`:** some Meta setup checklists also list `pages_messaging` for
> Instagram-via-Facebook-Login integrations. This codebase's own send calls go straight to the
> Instagram-scoped `/{instagram_account_id}/messages` edge, authorized by `instagram_manage_messages`
> — grant `pages_messaging` too if Meta's App Review UI asks for it for your app type, but nothing in
> this code path independently exercises it.

### 4. Advanced Access / App Review

Because each restaurant's Instagram account is a real, separate business that your Meta app does not
itself own, Meta requires **App Review for Advanced Access** to `instagram_manage_messages` and
`instagram_manage_comments` before the integration will work for accounts beyond your app's own
testers/admins. A handful of pilot restaurants can be added as app testers to skip review temporarily;
onboarding restaurants at scale requires passing App Review.

### 5. Collect the values you'll need

From the app's Basic Settings, note the **App ID** and **App Secret**. From the linked Instagram
account, note the numeric **Instagram Business Account ID** (Meta's Graph API Explorer, or
`GET /me/accounts` → the Page → `instagram_business_account.id`). All three go into the Settings tab
(see [Filling In the Settings Tab](#filling-in-the-settings-tab)).

---

## Generating the Long-Lived Page Access Token

1. In Graph API Explorer, select your app and the linked Page, and generate a **User Access Token**
   with the scopes above.
2. Exchange it for a **long-lived User Access Token**:
   ```
   GET /oauth/access_token
       ?grant_type=fb_exchange_token
       &client_id={app-id}
       &client_secret={app-secret}
       &fb_exchange_token={short-lived-user-token}
   ```
3. Use the long-lived user token to fetch the **Page Access Token**:
   ```
   GET /me/accounts?access_token={long-lived-user-token}
   ```
   The `access_token` returned for your Page in that list is a **long-lived Page Access Token**.
4. Paste that value into the Settings tab's **Page Access Token** field.

### The ~60-day expiry cycle

A long-lived Page Access Token is good for roughly **60 days**. This codebase has no automatic
refresh — it stores whatever token you paste in and uses it until Meta rejects it.

**What happens when it lapses:** every Graph call for that restaurant starts failing with Meta error
code 190 (`OAuthException`). `InstagramApiClient` classifies this as `TOKEN_INVALID`, which is
`fatalForChannel()` — the failure is logged at **ERROR**:

```
Instagram access token rejected by Meta (code 190): ...
The integration is down for this restaurant until the token is replaced.
```

For a running campaign, a dead token **halts the entire send** and leaves every untried recipient
`PENDING` for a later re-send. For one-off sends (wizard replies, comment auto-replies, admin DMs),
each call just fails individually — the channel goes quietly dark: subscribers get no wizard replies
and comments get no auto-replies, with nothing in the UI proactively flagging it.

Because nothing else surfaces this, **treat token renewal as a recurring calendar reminder** (every
45–50 days is a safe cadence) and/or watch application logs for the ERROR line above. To refresh,
repeat the steps above and paste the new token into Settings → Page Access Token → Save — this does
not require touching App ID, App Secret or Verify Token.

---

## Webhook Configuration

**Callback URL:** `https://<your-host>/api/v1/instagram/webhook`

The Settings tab shows this pre-built and read-only (constructed client-side as
`{protocol}//{host}/api/v1/instagram/webhook`) — copy it directly into Meta's dashboard rather than
retyping it.

In the Meta App Dashboard, add the **Webhooks** product, select the **Instagram** topic (the code
requires the payload's `"object"` field to equal `"instagram"` — other topics are ignored), and set:

- **Callback URL** = the URL above
- **Verify Token** = whatever you put in the Settings tab's Verify Token field (any string; it never
  leaves your infrastructure or Meta's, so a random high-entropy value is fine)
- **Subscription fields**: `messages`, `messaging_postbacks`, `comments`

Those three fields are exactly what `InstagramWebhookService` parses — messages and postbacks arrive
in the payload's `entry[].messaging[]` array, comments arrive in `entry[].changes[]` with
`field == "comments"`. Subscribing to other fields (e.g. `story_insights`) is harmless but does
nothing, since nothing in the code reads them.

### The verify-token handshake

Meta validates the callback URL with a `GET` carrying `hub.mode`, `hub.verify_token` and
`hub.challenge` as query parameters. `InstagramWebhookController.verify()`:

1. Requires `hub.mode` to be exactly `subscribe`.
2. Looks up **any** config (active or not — a config still being set up can complete the handshake)
   whose `verifyToken` matches, comparing in constant time.
3. On a match, echoes `hub.challenge` back verbatim as `Content-Type: text/plain` — deliberately never
   as HTML or JSON, since the challenge is attacker-influenceable content and reflecting it as HTML
   would be a reflected-XSS vector on the API origin.
4. Any failure (wrong mode, or no config matches the token) returns **one opaque, empty 403** — the
   two failure cases are indistinguishable on purpose, so the endpoint never confirms whether an
   integration exists for a given token.

You can test the handshake yourself before wiring it into Meta:

```bash
curl -i "https://your-host/api/v1/instagram/webhook?hub.mode=subscribe&hub.verify_token=YOUR_VERIFY_TOKEN&hub.challenge=12345"
# Expect: HTTP 200, Content-Type: text/plain, body "12345"
```

### The event POST

Every inbound event is a `POST` to the same URL with an `X-Hub-Signature-256` header (HMAC-SHA256 of
the raw request body, keyed by the app secret). `InstagramWebhookController.receive()` resolves the
owning config from the payload's `entry[].id` (the Instagram Business Account ID that received the
event) *before* checking the signature — the app secret needed to verify it is per-restaurant, so the
tenant must be known first. The signature check **fails closed**: a missing config, a missing app
secret, or a missing/malformed/incorrect signature all return 403. Once the signature verifies, the
controller returns 200 immediately and hands processing to an `@Async` background thread (Meta
re-delivers if it doesn't see a 2xx within roughly 20 seconds).

---

## Filling In the Settings Tab

| Setting (UI label) | Maps to | Purpose |
|---|---|---|
| **Meta App ID** | `appId` | From the app's Basic Settings |
| **App Secret** | `appSecret` | From the app's Basic Settings; verifies `X-Hub-Signature-256` on every inbound webhook. **Encrypted at rest.** Leave blank when updating an existing config to keep the current value |
| **Page Access Token** | `accessToken` | The long-lived token from [above](#generating-the-long-lived-page-access-token). **Encrypted at rest.** Leave blank when updating to keep the current value |
| **Instagram Account ID** | `instagramAccountId` | Numeric IG Business Account ID. Must be unique **platform-wide** — two restaurants cannot register the same Instagram account (`uq_ig_config_account`) |
| **Webhook URL** | *(read-only, generated)* | Copy into Meta's dashboard — see [Webhook Configuration](#webhook-configuration) |
| **Verify Token** | `verifyToken` | Any string; must match Meta dashboard's Verify Token exactly. Leave blank when updating to keep the current value |
| **Integration Active** | `isActive` | See below |
| **Welcome Message** | `welcomeMessage` | Prepended to the wizard's first prompt. Falls back to a default Uzbek greeting using the configured brand name (`branding.name`, default `Qahvoon`) if left blank |
| **Auto-reply to Comments** | `autoReplyEnabled` | Toggles whether new post comments get a public templated reply |
| **Auto-reply Template** | `autoReplyTemplate` | Supports a `{comment}` placeholder (the UI documents this one); the code also accepts the alias `{comment_text}` — both are replaced with the comment's text |
| **Private Replies** | `privateReplyEnabled` | Toggles the private-reply-from-comments flow (independent of auto-reply — a config can run either, both, or neither). See [Private Replies](#private-replies) |
| **Trigger Keyword** | `privateReplyKeyword` | Case-insensitive substring; a comment *containing* this word triggers the private DM. Blank never matches |
| **Private Reply Message** | `privateReplyTemplate` | The DM text. Supports `{code}` — replaced with a minted coupon, or stripped when none is available |
| **Coupon Promotion ID** | `privateReplyPromotionId` | Optional. The promotion a single-use code is minted from for `{code}`; must belong to this restaurant. Blank sends the message with `{code}` stripped |

### Activating requires an app secret

Turning **Integration Active** on — whether at creation or on an update — is rejected with a 400 error
unless a non-blank App Secret is present (`InstagramBotConfigService.requireVerifiableCredentials`):

> "An app secret is required before activating the Instagram integration — webhook deliveries are
> rejected unless their signature can be verified."

This is deliberate: the webhook signature check fails closed, so an active config with no app secret
would be a live, publicly reachable endpoint that could never accept a legitimate event. The same rule
applies when *editing* an already-active config down to a blank app secret — it is rejected rather than
silently leaving the integration active-but-unverifiable.

Activating a config also **deactivates** any other config previously active for the same restaurant —
at most one active config per restaurant is allowed (`uq_ig_config_active_per_restaurant`).

### Clear All Credentials

The destructive **Clear All Credentials** button wipes Access Token, App Secret and Verify Token *and*
force-deactivates the config in the same step — a config can never be left Active with nothing left to
verify signatures against.

---

## Private Replies

When someone comments a keyword on one of the business's posts, the integration can open a **private DM
thread** with them — Meta's private-reply endpoint (`POST /{ig-account-id}/messages` with a
`recipient.comment_id`, not a public comment reply). This is the "comment **MENU** and we'll DM you the
link" growth mechanic, and it matters for a second reason: a private reply **opens a fresh 24-hour
messaging window** with that person, which the public comment reply does not.

**How a comment is routed** (`InstagramWebhookService.processChangeEvent`):

1. The comment text is matched **case-insensitively** against **Trigger Keyword** as a *substring*, so
   `MENU` fires on "menu pls!", "🙋 MENU", or "can I get the MENU". A blank keyword never matches — so
   enabling the toggle alone will not DM every commenter.
2. On a match, **Private Reply Message** is sent as a DM. If **Coupon Promotion ID** is set, one
   single-use coupon code is minted from that promotion (via `CouponService`) and substituted into
   `{code}`; if it is unset, foreign to this restaurant, or minting fails for any reason, `{code}` is
   stripped and the DM still goes out — the newly-opened messaging window has value on its own.
3. The public **Auto-reply** and the private reply are **independent** — either, both, or neither — but
   they share a single webhook-dedup check, so Meta's at-least-once redelivery of the same comment
   yields **at most one** DM and **at most one** minted code per comment.

The private reply's DM uses `instagram_manage_messages`; matching/reading the comment uses
`instagram_manage_comments`. Every private reply is written to `instagram_logs` as a `PRIVATE_REPLY` row
(the comment id stands in for the not-yet-known DM recipient), so it appears in the message statistics
like any other send.

> **Coupon-abuse consideration.** The dedup check stops the *same* comment from minting twice, but nothing
> caps how many *distinct* keyword comments one determined person can post to harvest codes. The natural
> bound is the promotion's own finite, single-use code pool — size it deliberately, and disable the
> promotion (or the toggle) if you see abuse. A per-person cap would need the commenter's identity, which
> Meta does not expose until they are already in the DM thread.

---

## Security & Operations

### Credential encryption at rest

`app_secret` and `access_token` (not `verify_token`, which stays plaintext — it can only be used to
complete the harmless GET handshake, not to forge a signed event) are encrypted with **AES-256-GCM**
via `EncryptedStringConverter` / `CredentialCrypto` (migration `V169__encrypt_credential_columns.sql`).

- **Key:** environment variable `ELCAFE_ENCRYPTION_KEY` — a base64-encoded 128/192/256-bit AES key.
  Generate a 256-bit key with:
  ```bash
  openssl rand -base64 32
  ```
- **This key is shared platform-wide**, not Instagram-specific — the same variable also encrypts the
  Telegram owner-bot token (`V170__encrypt_telegram_bot_token.sql`). Losing or rotating it affects both
  integrations' stored credentials at once.
- **Inert until set.** With no key configured, `encrypt()` returns the plaintext unchanged — the app
  behaves exactly as before and nothing breaks on deploy, but nothing is protected either. Once a key
  is set, new writes are encrypted (tagged with an `enc:v1:` prefix); rows written before the key
  existed stay readable as legacy plaintext and are transparently re-encrypted the next time they're
  saved. No bulk migration is needed either way.
- **Guard the key.** If an already-encrypted row's key is later lost or unset, decryption throws rather
  than silently failing — there is no key-rotation tooling in this codebase, so losing the key makes
  those specific credential values permanently unreadable, not merely "falls back to plaintext." Store
  it in a secrets manager, not in source control, and note it is **not** pre-scaffolded in
  `.env.docker.example` — add it yourself alongside `DB_PASSWORD` / `JWT_SECRET` as described in
  `PRODUCTION_SETUP.md`.

### Global kill switch

`INSTAGRAM_ENABLED` (property `instagram.enabled`, default `true`). Set to `false` to immediately
suppress **every** outbound Graph call for **every restaurant** — DMs, quick-reply DMs, comment
auto-replies, and campaign sends — without touching a single restaurant's configuration. This is the
incident switch for a leaked token, a Meta-side restriction, or a runaway auto-reply loop (the same
pattern `telegram.bot.enabled` / `sms.enabled` use elsewhere in this app).

Inbound webhooks still return 200 while disabled — Meta sees nothing wrong — but every send silently
no-ops with an `INVALID_REQUEST` result and a debug log line:

```
Instagram integration disabled (instagram.enabled=false) — outbound send suppressed
```

### Circuit breaker

Outbound Graph calls run through a resilience4j circuit breaker named `instagram`
(`resilience4j.circuitbreaker.instances.instagram` in `application.yml`), inheriting the shared
`default` base config plus its own slow-call tuning:

| Setting | Value | Source |
|---|---|---|
| `sliding-window-size` | 10 | shared `default` config |
| `minimum-number-of-calls` | 5 | shared `default` config |
| `failure-rate-threshold` | 50% | shared `default` config |
| `wait-duration-in-open-state` | 30s | shared `default` config |
| `permitted-number-of-calls-in-half-open-state` | 2 | shared `default` config |
| `slow-call-duration-threshold` | 8s | `instagram`-specific override |
| `slow-call-rate-threshold` | 50% | `instagram`-specific override |

The 8s slow-call threshold sits deliberately under the HTTP client's own 5s-connect + 10s-read
timeouts, so a Meta slowdown is caught as "slow" rather than just timing out uncounted. When the
breaker is open, every send (`sendMessage`, `sendMessageWithQuickReplies`, `replyToComment`)
short-circuits to a `CIRCUIT_OPEN` result without contacting Meta at all, logged as a warning
(`Instagram circuit open, dropping ...`). A running campaign treats an open circuit the same as a dead
token: it halts immediately and leaves the rest of the recipients `PENDING`.

### PII erasure

- **Deleting a subscriber** (`DELETE /api/v1/instagram/subscribers/{id}`, `ADMIN`/`OWNER` only)
  permanently erases their display name, phone, birth date and every saved address
  (`InstagramBotService.eraseSubscriber`). Tenant-scoped and irreversible.
- **Deleting a Customer** elsewhere in the app cascades the same erasure to any Instagram subscriber
  linked to that customer, via an `onCustomerDeleted` event listener joined into the *same* delete
  transaction (a failure rolls both back together). Without this, the old foreign key would only null
  out the link and leave the subscriber's name/phone/birthday/addresses behind indefinitely.
- **Blocking** a subscriber is *not* erasure — it only stops the bot from acting on their messages; the
  data is untouched. Use Delete for an actual right-to-erasure request.

---

## Configuration Reference

Every `instagram.*` key, plus the two environment variables referenced throughout this guide:

| Key | Env var | Default | Meaning |
|---|---|---|---|
| `instagram.enabled` | `INSTAGRAM_ENABLED` | `true` | Global kill switch — see [Security & Operations](#security--operations) |
| `instagram.graph.base-url` | *(not wired to an env var)* | `https://graph.facebook.com` | Graph API host. This one is **not** set in `application.yml` — it's a hardcoded default on the `@Value` parameter in `InstagramApiClient`'s constructor. To override, add `instagram.graph.base-url` under the `instagram:` block yourself, or rely on Spring's relaxed env-var binding (`INSTAGRAM_GRAPH_BASE_URL`) |
| `instagram.graph.version` | *(not wired to an env var)* | `v19.0` | Graph API version segment appended to the base URL. Same override path as above — bump this when Meta retires v19.0 |
| `instagram.campaign.messages-per-second` | `INSTAGRAM_CAMPAIGN_MESSAGES_PER_SECOND` | `8` | Proactive pacing cap for campaign sends. `0` disables pacing |
| `instagram.campaign.messaging-window-hours` | `INSTAGRAM_CAMPAIGN_MESSAGING_WINDOW_HOURS` | `24` | How recent a subscriber's last inbound message must be to be included when a campaign's audience is built |
| `instagram.webhook.dedup-retention-days` | `INSTAGRAM_WEBHOOK_DEDUP_RETENTION_DAYS` | `7` | How long processed webhook-event ids are kept before the daily sweep purges them |
| `instagram.webhook.dedup-cleanup-cron` | `INSTAGRAM_WEBHOOK_DEDUP_CLEANUP_CRON` | `0 30 3 * * *` | Cron schedule (ShedLock-guarded, safe under a rolling deploy) for the dedup-table purge job |
| *(shared, not Instagram-specific)* | `ELCAFE_ENCRYPTION_KEY` | unset (encryption inert) | Base64 AES-128/192/256 key for credential-at-rest encryption; also used for the Telegram bot token |

---

## Campaigns

### The 24-hour messaging window

Meta only allows messaging a subscriber within **24 hours** of their last inbound message to the
business — marketing content is not eligible for any message tag that would extend that window. A
send outside the window is rejected with Meta error code 10 (or the more specific sub-codes
`2534014` / `2018278`), which `InstagramSendResult.classify()` maps to `RECIPIENT_UNAVAILABLE`.

`InstagramCampaignService` builds a campaign's audience **at creation time** from
`instagram.campaign.messaging-window-hours` (default 24h, `lastInteractionAt` on the subscriber):

- **ALL** — active, non-blocked subscribers who messaged within the window.
- **REGISTERED** — the same, restricted to `conversationState = REGISTERED`.

This means a campaign's `recipientCount` can legitimately be smaller than your total subscriber count
— subscribers outside the window are excluded up front rather than attempted and failed.

### Async send with pacing

Both `POST /api/v1/instagram/campaigns` (create-and-send) and
`POST /api/v1/instagram/subscribers/broadcast` (the older single-shot broadcast action, which now
wraps the same campaign engine) return **202 Accepted** immediately with the new campaign's id and
recipient count. `InstagramCampaignExecutor` performs the actual sends on a background `@Async`
thread, pacing between sends at `instagram.campaign.messages-per-second` (default 8/s, ≈125ms between
sends) to stay under Meta's rate limits proactively rather than relying on the circuit breaker to trip
reactively. Each recipient's outcome is written in its own committed transaction as it happens, so
progress is never lost to a crash mid-run.

### Halting vs. per-recipient failure

- **Fatal for the whole run** (`TOKEN_INVALID`, `RATE_LIMITED`, `CIRCUIT_OPEN`): the executor stops
  immediately, the campaign's status becomes `CANCELLED`, and every not-yet-attempted recipient stays
  `PENDING`.
- **Fatal only for that recipient** (outside the window, blocked the business, malformed request): that
  recipient is marked `FAILED` with an error message, and the run continues to the next recipient.

### Resumable by design

A campaign is **resumable**, not restarted: `POST /api/v1/instagram/campaigns/{id}/send` (the UI's
**Re-send** button, shown for `CANCELLED` or `DRAFT` campaigns) re-invokes the executor, which only
processes rows still `PENDING` — a recipient already marked `SENT` is never messaged twice
(`uq_ig_campaign_recipient` plus the PENDING-only query enforce this). This is what makes it safe to
retry after a dead token is replaced or the circuit breaker recovers.

Per-recipient delivery records — status, timestamp, error message — are available via
`GET /api/v1/instagram/campaigns/{id}/recipients`, surfaced in the UI as the **Recipients** dialog on
each campaign row. This is the first place to check when a campaign's sent count looks lower than
expected.

---

## Consent & Opt-out (STOP)

Marketing campaigns respect per-subscriber consent (V174, the `marketing_opt_in` / `opted_out_at`
columns on `instagram_subscribers`):

- **Opting out.** A subscriber who DMs an opt-out keyword — **STOP**, UNSUBSCRIBE, CANCEL, or Uzbek
  **to'xtat** / **bekor** (exact match, case-insensitive) — is unsubscribed immediately: `marketing_opt_in`
  flips to false, `opted_out_at` is stamped, and they get a plain confirmation. This is checked **before**
  any wizard step, so STOP is never mistaken for the name/phone/address the wizard was waiting on, and a
  stranger's first-ever message being STOP creates no subscriber at all. Opted-out subscribers show an
  **"Opted out"** badge on the Subscribers tab.
- **Opting back in.** **START**, SUBSCRIBE, or **obuna** re-subscribes (`marketing_opt_in` → true,
  `opted_out_at` cleared). "start" also (re)starts the registration wizard, exactly as before.
- **Campaigns exclude opt-outs.** Both audiences (ALL and REGISTERED) filter on `marketing_opt_in = true`,
  so an opted-out subscriber is never enqueued again — a campaign's `recipientCount` legitimately drops by
  the opted-out count.
- **Default is opt-in (grandfathered).** `marketing_opt_in` defaults to **true**, so every subscriber that
  existed before this shipped keeps receiving campaigns; only an explicit STOP changes that. (A stricter
  opt-in-required default was a deliberate product decision, left out of scope.)
- **Transactional messages are not gated.** Order-status and reservation DMs are transactional, not
  marketing, so they are **not** suppressed by a marketing opt-out — matching standard STOP-compliance
  practice.

---

## Troubleshooting

| Symptom | Likely cause | What to check |
|---|---|---|
| **403 on the GET hub-challenge** | `hub.mode` isn't exactly `subscribe`, or `hub.verify_token` doesn't match *any* restaurant's Verify Token field. Matching does **not** require the config to be Active — even a config still mid-setup can complete the handshake | Re-check the Verify Token for stray whitespace; the comparison is exact. Confirm you copied the value from the same config you intend to activate |
| **401 (not 403) on `/api/v1/instagram/webhook`** | The path fell out of `SecurityConfig`'s `permitAll` list — Meta sends no credentials of ours, so Spring Security is rejecting the request before it ever reaches the controller | Confirm both `/api/v1/instagram/webhook` and `/api/v1/instagram/webhook/**` are still listed under `permitAll` in `SecurityConfig`. (This is exactly the bug `V163` fixed platform-wide — the route had never been added, so every Meta request 401'd for this endpoint's entire history until then.) |
| **Meta shows the webhook as verified, POSTs return 200, but subscribers get no replies / comments get no auto-reply** | The config resolved for that Instagram account is not **Active**. The POST path returns 200 as soon as the signature checks out — Meta sees no error — but `InstagramWebhookService.processEntry` silently drops the event right after ("`config for account {} is inactive — entry dropped`") | Open Settings, confirm **Integration Active** is on and saved for the right config. This is the most common cause once the webhook itself is verified — check it before assuming the webhook was never registered in Meta's dashboard at all (the other common cause) |
| **Low campaign sent-count vs. subscriber totals** | Recipients outside the 24-hour messaging window | The audience is pre-filtered at campaign creation, so `recipientCount` can already be below your full subscriber list. For a resumed/stalled campaign, some previously-`PENDING` recipients may have aged out of the window since creation — check the **Recipients** dialog for `FAILED` rows with a window/availability error message |
| **Sends suppressed platform-wide / a campaign halts immediately with everything left `PENDING`** | Either `INSTAGRAM_ENABLED=false`, or a dead/expired Page Access Token (Meta error code 190) | Grep logs for `Instagram integration disabled (instagram.enabled=false)` (flip `INSTAGRAM_ENABLED` back to `true`) or `Instagram access token rejected by Meta (code 190)` (generate a fresh long-lived token — see [above](#generating-the-long-lived-page-access-token)) |
| **Campaign halts with "circuit open" in the logs** | The resilience4j breaker tripped after a burst of failures (≥50% of the last 10 calls) and is refusing calls for up to 30s | Wait for it to self-probe and close, then use **Re-send** — only the still-`PENDING` recipients are retried |
| **A single admin DM shows "sent: false" with no obvious reason** | The send failed for a specific, reported reason | Check the response body's `reason` field (`TOKEN_INVALID`, `RECIPIENT_UNAVAILABLE`, `RATE_LIMITED`, `CIRCUIT_OPEN`, `INVALID_REQUEST`) — the API always reports why; the UI toast is generic |
| **Can't register a second restaurant with the same Instagram Business Account ID** | By design — `uq_ig_config_account` allows one restaurant per IG account, platform-wide | Use a distinct Instagram Business Account per restaurant; this is not a bug to work around |

---

## Go-Live Checklist

- [ ] Meta app created; Instagram product added; IG professional account linked to a Facebook Page
- [ ] `instagram_basic`, `instagram_manage_messages`, `pages_manage_metadata`, `pages_show_list`
      granted (plus `instagram_manage_comments` if comment auto-reply will be used)
- [ ] Long-lived Page Access Token generated and pasted into Settings → Page Access Token
- [ ] Webhook callback URL registered in Meta's dashboard, copied from the Settings tab
- [ ] Verify Token set identically in the Settings tab and Meta's dashboard
- [ ] Webhook subscription fields include `messages`, `messaging_postbacks`, `comments`
- [ ] App Secret filled in (required before Active can be turned on)
- [ ] `ELCAFE_ENCRYPTION_KEY` provisioned in the environment before the first save, if credentials at
      rest should be encrypted
- [ ] **Integration Active** toggled on in the Settings tab and saved
- [ ] Test DM sent to the connected Instagram account and the wizard's welcome reply confirmed
- [ ] Calendar reminder set for Page Access Token renewal (every 45–50 days)

---

## Source

- Controller: `src/main/java/com/elcafe/modules/instagram/controller/InstagramWebhookController.java`
- Webhook processing: `src/main/java/com/elcafe/modules/instagram/service/InstagramWebhookService.java`
- Config service / entity: `src/main/java/com/elcafe/modules/instagram/service/InstagramBotConfigService.java`, `entity/InstagramBotConfig.java`
- Graph API client: `src/main/java/com/elcafe/modules/instagram/service/InstagramApiClient.java`
- Wizard / subscriber management: `src/main/java/com/elcafe/modules/instagram/service/InstagramBotService.java`
- Campaigns: `src/main/java/com/elcafe/modules/instagram/service/InstagramCampaignService.java`, `InstagramCampaignExecutor.java`
- Message log & statistics: `service/InstagramMessageLogger.java`, `repository/InstagramLogRepository.java`, `entity/InstagramLog.java`, `service/InstagramStatisticsService.java` (all under `.../modules/instagram/`)
- Templates: `service/InstagramTemplateService.java`, `controller/InstagramTemplateController.java`, `entity/InstagramTemplate.java`
- Private replies: `InstagramWebhookService.processChangeEvent` + `InstagramApiClient.sendPrivateReply`, with coupon minting via `src/main/java/com/elcafe/modules/promotion/service/CouponService.java`
- Customer order/reservation DMs: `src/main/java/com/elcafe/modules/notification/service/CustomerNotificationService.java` + `notification/channel/CustomerMessagingChannel.java` (Telegram/Instagram implementations)
- Consent / opt-out: `InstagramBotService` (`isOptOutKeyword`/`handleOptOut`/`handleOptIn`) + the `marketingOptIn` predicate in `InstagramSubscriberRepository`'s campaign-audience finders
- Persistent menu / ice breakers: `InstagramApiClient.setPersistentMenu`/`setIceBreakers` + the default profile pushed on activation by `InstagramBotConfigService`
- Encryption: `src/main/java/com/elcafe/common/crypto/CredentialCrypto.java`, `EncryptedStringConverter.java`
- Frontend Settings/Subscribers/Campaigns UI: `frontend/src/pages/InstagramMarketing.jsx`
- Config: `src/main/resources/application.yml` (search `instagram:` and `resilience4j:`)
- Migrations: `src/main/resources/db/migration/V163__instagram_tenant_scoping.sql`, `V166__instagram_campaigns.sql`, `V167__instagram_processed_events.sql`, `V169__encrypt_credential_columns.sql`, `V170__encrypt_telegram_bot_token.sql`, `V171__instagram_logs.sql`, `V172__instagram_templates.sql`, `V173__instagram_private_replies.sql`, `V174__instagram_opt_in.sql`
- Related: `PRODUCTION_SETUP.md` (environment/secrets provisioning), `docs/DEPLOYMENT_TOPOLOGY.md` (ShedLock-guarded scheduled jobs, single-node deployment)
