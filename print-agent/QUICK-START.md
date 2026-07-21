# Print Agent - Quick Start Guide

## What is Print Agent?

Print Agent is a small program that runs on your computer (or Raspberry Pi) and connects your local thermal printer to the Qahvoon cloud system. When a customer places an order, the ticket automatically prints.

```
Cloud (Qahvoon) -----> Print Agent (your computer) -----> Thermal Printer
```

---

## Step 1: Find Your Printer's IP Address

Most thermal printers have a "self-test" button. Hold it while turning on the printer - it will print its IP address.

Common default IPs:
- `192.168.1.100`
- `192.168.0.100`
- `192.168.1.87`

**Test the connection:**
```bash
ping 192.168.1.100
```

---

## Step 2: Find Your Restaurant ID

1. Login to Qahvoon admin panel
2. Go to **Settings** > **Restaurant Settings**
3. Your Restaurant ID is shown at the top (usually a number like `1`, `2`, etc.)

---

## Step 3: Configure the Agent

```bash
cd print-agent
cp .env.example .env
```

Edit the `.env` file:

```env
# Qahvoon server WebSocket URL
SERVER_URL=wss://www.qahvoon.uz/ws-print-agent

# Your restaurant ID (from admin panel)
RESTAURANT_ID=1

# Authentication token — REQUIRED once the server enforces WebSocket auth.
# Mint it in the admin panel (POST /api/v1/settings/print-agent/token, as an ADMIN/OWNER
# of this restaurant) and paste the returned token here. See "Get your token" below.
AGENT_TOKEN=

# Printer settings
PRINTER_TYPE=network
PRINTER_IP=192.168.1.100
PRINTER_PORT=9100
```

> **Note:** The server is at `https://www.qahvoon.uz`, API at `/api/v1`, WebSocket at `/ws-print-agent`.

> **Get your token:** in the admin panel, open **Settings → Printer Settings** and generate a print-agent token (this calls `POST /api/v1/settings/print-agent/token`). Paste it into `AGENT_TOKEN`. The agent sends it as a Bearer token on connect; without it, the server rejects the connection once WebSocket auth is in `enforce` mode (default is `shadow`, which only logs).

---

## Step 4: Install and Run

```bash
# Install dependencies (only once)
npm install

# Start the agent
npm start
```

**You should see:**
```
=== Qahvoon Print Agent v1.0.0 ===
[APP] Starting print agent...
[WS] Connecting to server...
[WS] Connected to server
```

---

## Step 5: Configure Printers in Qahvoon

1. Go to **Settings** > **Printer Settings** in Qahvoon admin
2. Click **"Add Printer"**
3. Fill in:
   - **Printer Name**: Any name (e.g., "Kitchen Printer")
   - **Type**: KITCHEN (for order tickets) or CUSTOMER (for receipts)
   - **Connection Type**: NETWORK
   - **IP Address**: Your printer's IP (e.g., `192.168.1.100`)
   - **Port**: `9100`
4. Enable **"Auto Print"** if you want orders to print automatically
5. Click **Save**

> **Note:** The print-agent WebSocket endpoint is always available. Whether a token is required depends on the server's `app.websocket.auth.mode` (default `shadow` logs but allows; `enforce` requires a valid `AGENT_TOKEN`).

---

## Testing

1. Make sure the agent is running (`npm start`)
2. Create a test order in Qahvoon
3. The ticket should print automatically

---

## Common Problems

### "Connection refused" or "Cannot connect"

- Check `SERVER_URL` is correct (use `wss://` not `ws://` for HTTPS)
- Make sure your internet is working
- Verify `RESTAURANT_ID` is correct

### "Printer not responding"

- Verify printer IP: `ping 192.168.1.100`
- Test printer port: `nc -zv 192.168.1.100 9100`
- Make sure printer is turned on
- Check printer is on the same network as your computer

### "Agent starts but nothing prints"

- Make sure you have a printer configured in **Settings** > **Printer Settings**
- Verify the printer IP in Qahvoon matches your actual printer
- Check that **"Auto Print"** is enabled for the printer
- Try creating a new order

---

## Running 24/7 (Recommended)

### Option A: Keep Terminal Open

Just keep `npm start` running in a terminal window.

### Option B: Run as Background Service (Linux)

1. Create service file:
```bash
sudo nano /etc/systemd/system/print-agent.service
```

2. Add this content:
```ini
[Unit]
Description=Qahvoon Print Agent
After=network.target

[Service]
Type=simple
WorkingDirectory=/home/YOUR_USERNAME/elcafe/print-agent
ExecStart=/usr/bin/node index.js
Restart=always

[Install]
WantedBy=multi-user.target
```

3. Start the service:
```bash
sudo systemctl enable print-agent
sudo systemctl start print-agent
```

4. Check status:
```bash
sudo systemctl status print-agent
```

---

## Multiple Kitchen Stations

If you have multiple printers (e.g., one for drinks, one for food):

1. Configure each printer in Qahvoon **Settings** > **Printer Settings**
2. Assign menu categories to each printer (station routing)
3. The single print agent will send jobs to the correct printer based on item category

---

## Quick Reference

| Command | What it does |
|---------|--------------|
| `npm start` | Start the print agent |
| `npm run dev` | Start with auto-reload (for development) |
| `Ctrl+C` | Stop the agent |

| Setting | Example | Description |
|---------|---------|-------------|
| `SERVER_URL` | `wss://www.qahvoon.uz/ws-print-agent` | Qahvoon WebSocket server (path `/ws-print-agent`, no `/api`) |
| `RESTAURANT_ID` | `1` | Your restaurant ID |
| `AGENT_TOKEN` | `<minted token>` | Bearer token from Settings → Printer Settings; required under WebSocket-auth enforce |
| `PRINTER_TYPE` | `network` | Use `network` or `usb` |
| `PRINTER_IP` | `192.168.1.100` | Your printer's IP |
| `PRINTER_PORT` | `9100` | Default thermal printer port |

---

## Need Help?

1. Check the full documentation in `README.md`
2. Look at server logs in Qahvoon admin panel
3. Check print agent logs in terminal
