# Qahvoon Print Agent

A lightweight print agent that runs on your local network and connects to the Qahvoon cloud backend via WebSocket. When new orders are placed, print jobs are automatically sent to this agent which prints to your local thermal printer.

## Architecture

```
┌─────────────────┐         WebSocket          ┌─────────────────┐
│  Cloud Backend  │ ◄─────────────────────────► │  Print Agent    │
│  (qahvoon.uz)   │                             │  (your network) │
└─────────────────┘                             └────────┬────────┘
                                                         │
                                                         │ ESC/POS
                                                         ▼
                                                ┌─────────────────┐
                                                │ Thermal Printer │
                                                │ (192.168.x.x)   │
                                                └─────────────────┘
```

## Requirements

- Node.js 18 or higher (`npm run dev` uses `node --watch`, which requires Node ≥ 18.11)
- Thermal printer with ESC/POS support (most receipt printers)
- Network connection to both internet and local printer

## Supported Printers

- **Network printers** (recommended): Any ESC/POS compatible printer with ethernet/WiFi
  - Epson TM-T20, TM-T88
  - Star TSP100, TSP650
  - SNBC BTP series
  - Most Chinese thermal printers

- **USB printers**: Requires libusb installed on your system
  - Linux: `sudo apt install libusb-1.0-0-dev`
  - macOS: `brew install libusb`
  - Windows: Use Zadig to install WinUSB driver

## Installation

1. **Clone or copy the print-agent folder** to a computer on your local network

2. **Install dependencies**:
   ```bash
   cd print-agent
   npm install
   ```

3. **Configure the agent**:
   ```bash
   cp .env.example .env
   nano .env  # Edit with your settings
   ```

4. **Start the agent**:
   ```bash
   npm start
   ```

## Configuration

Edit the `.env` file:

```env
# Your cloud server WebSocket URL (note: the endpoint path is /ws-print-agent, with no /api prefix)
SERVER_URL=wss://your-server.com/ws-print-agent

# Your restaurant ID (from admin panel)
RESTAURANT_ID=1

# Authentication token — REQUIRED once the server enforces WebSocket auth
# (app.websocket.auth.mode=enforce). Mint it in the admin panel:
#   POST /api/v1/settings/print-agent/token   (as an ADMIN/OWNER of this restaurant)
# then paste the returned JWT here. It is scoped to this restaurant and valid ~1 year.
# The agent sends it as `Authorization: Bearer <AGENT_TOKEN>` on the STOMP CONNECT frame;
# a tokenless connection is rejected under enforce.
AGENT_TOKEN=

# Printer type: 'usb' or 'network'
PRINTER_TYPE=network

# For network printers:
PRINTER_IP=192.168.1.100
PRINTER_PORT=9100
```

## Running as a Service

### Linux (systemd)

Create `/etc/systemd/system/elcafe-print-agent.service`:

```ini
[Unit]
Description=Qahvoon Print Agent
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/print-agent
ExecStart=/usr/bin/node index.js
Restart=always
RestartSec=10
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
```

Then:
```bash
sudo systemctl enable elcafe-print-agent
sudo systemctl start elcafe-print-agent
```

### Windows

Use [NSSM](https://nssm.cc/) to create a Windows service:
```cmd
nssm install QahvoonPrintAgent "C:\Program Files\nodejs\node.exe" "C:\print-agent\index.js"
nssm start QahvoonPrintAgent
```

### Raspberry Pi

Perfect for running the print agent 24/7:
1. Install Raspberry Pi OS Lite
2. Install Node.js: `curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash - && sudo apt install -y nodejs`
3. Copy print-agent folder
4. Set up systemd service as shown above

## Troubleshooting

### Agent not connecting
- Check SERVER_URL is correct (use `wss://` for HTTPS servers)
- Verify RESTAURANT_ID matches your restaurant in admin panel
- Check firewall allows outbound WebSocket connections

### Printer not printing
- Verify printer IP is correct: `ping 192.168.1.100`
- Check printer is on port 9100: `nc -zv 192.168.1.100 9100`
- Ensure printer is ESC/POS compatible

### USB printer issues
- Install libusb drivers
- On Linux, add user to `lp` group: `sudo usermod -a -G lp $USER`
- On Windows, use Zadig to install WinUSB driver

## Multiple Printers / Kitchen Stations

For multiple kitchen stations, you can:

1. **Run multiple agents** on the same machine (different configs)
2. **Configure printers per job**: The backend sends printer IP with each job, so one agent can print to different printers

## Development

```bash
# Run with auto-reload
npm run dev

# List USB printers
node -e "require('./lib/printer-manager').listUSBPrinters()"
```

## License

MIT
