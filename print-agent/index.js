/**
 * Qahvoon Print Agent
 *
 * Connects to the cloud backend via WebSocket and prints
 * kitchen tickets to local thermal printers.
 *
 * Usage:
 *   1. Configure .env file with your settings
 *   2. npm install
 *   3. npm start
 */

require('dotenv').config();
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const PrinterManager = require('./lib/printer-manager');
const TicketFormatter = require('./lib/ticket-formatter');

// Configuration from environment
const config = {
    serverUrl: process.env.SERVER_URL || 'ws://localhost:8080/ws-print-agent',
    restaurantId: parseInt(process.env.RESTAURANT_ID || '1'),
    // Auth token the backend requires on the WebSocket CONNECT once app.websocket.auth.mode=enforce
    // (audit #21). Mint it in the admin panel: POST /api/v1/settings/print-agent/token. Without it the
    // connection is rejected under enforce (and only logged under shadow).
    agentToken: process.env.AGENT_TOKEN || null,
    agentId: process.env.AGENT_ID || `agent-${uuidv4().substring(0, 8)}`,
    reconnectDelay: parseInt(process.env.RECONNECT_DELAY || '5000'),
    printerName: process.env.PRINTER_NAME || null, // Auto-detect if not set
    printerType: process.env.PRINTER_TYPE || 'usb', // 'usb' or 'network'
    printerIp: process.env.PRINTER_IP || null,
    printerPort: parseInt(process.env.PRINTER_PORT || '9100'),
};

console.log('===========================================');
console.log('       Qahvoon Print Agent v1.0.0');
console.log('===========================================');
console.log(`Agent ID: ${config.agentId}`);
console.log(`Restaurant ID: ${config.restaurantId}`);
console.log(`Server: ${config.serverUrl}`);
console.log(`Printer Type: ${config.printerType}`);
if (config.printerType === 'network') {
    console.log(`Printer IP: ${config.printerIp}:${config.printerPort}`);
} else {
    console.log(`Printer Name: ${config.printerName || 'Auto-detect'}`);
}
console.log('===========================================\n');

// Initialize printer manager
const printerManager = new PrinterManager(config);
const ticketFormatter = new TicketFormatter();

// STOMP client
let stompClient = null;
let isConnected = false;

/**
 * Connect to the backend WebSocket server
 */
function connect() {
    console.log('[WS] Connecting to server...');

    if (!config.agentToken) {
        console.warn('[WS] AGENT_TOKEN is not set — the server will reject this connection when ' +
            'websocket auth is enforced. Mint one via the admin panel and set AGENT_TOKEN in .env.');
    }

    stompClient = new Client({
        webSocketFactory: () => new WebSocket(config.serverUrl),
        // Authenticate the STOMP CONNECT so the backend binds this session to the restaurant and only
        // lets it read its own print topic (audit #21).
        connectHeaders: config.agentToken ? { Authorization: `Bearer ${config.agentToken}` } : {},
        reconnectDelay: config.reconnectDelay,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        onConnect: (frame) => {
            isConnected = true;
            console.log('[WS] Connected to server');

            // Subscribe to print jobs for this restaurant
            const destination = `/topic/print-agent/${config.restaurantId}`;
            console.log(`[WS] Subscribing to ${destination}`);

            stompClient.subscribe(destination, (message) => {
                handleMessage(JSON.parse(message.body));
            });

            // Register this agent with the server
            sendMessage('/app/print-agent/connect', {
                agentId: config.agentId,
                restaurantId: config.restaurantId
            });

            // Request any pending jobs
            sendMessage('/app/print-agent/get-jobs', {
                agentId: config.agentId,
                restaurantId: config.restaurantId
            });
        },

        onDisconnect: () => {
            isConnected = false;
            console.log('[WS] Disconnected from server');
        },

        onStompError: (frame) => {
            console.error('[WS] STOMP error:', frame.headers['message']);
            console.error('[WS] Details:', frame.body);
        },

        onWebSocketError: (event) => {
            console.error('[WS] WebSocket error:', event.message || 'Connection failed');
        },

        onWebSocketClose: () => {
            isConnected = false;
            console.log('[WS] WebSocket closed, will reconnect...');
        }
    });

    stompClient.activate();
}

/**
 * Send a message to the server
 */
function sendMessage(destination, body) {
    if (stompClient && isConnected) {
        stompClient.publish({
            destination,
            body: JSON.stringify(body)
        });
    }
}

/**
 * Handle incoming messages from the server
 */
async function handleMessage(message) {
    // Check if it's a notification or a print job
    if (message.type === 'NEW_JOBS') {
        console.log('[MSG] New jobs notification received');
        // Request the actual jobs
        sendMessage('/app/print-agent/get-jobs', {
            agentId: config.agentId,
            restaurantId: config.restaurantId
        });
        return;
    }

    // It's a print job
    if (message.id) {
        await processPrintJob(message);
    }
}

/**
 * Process a print job
 */
async function processPrintJob(job) {
    console.log(`\n[JOB] Processing job #${job.id} - Order ${job.orderNumber} (${job.stationName})`);

    // Acknowledge receipt
    sendMessage('/app/print-agent/job-received', {
        jobId: job.id,
        agentId: config.agentId
    });

    try {
        // Format the ticket
        const escposCommands = ticketFormatter.format(job);

        // Print to the printer
        await printerManager.print(escposCommands, job);

        // Report success
        sendMessage('/app/print-agent/job-completed', {
            jobId: job.id,
            agentId: config.agentId
        });

        console.log(`[JOB] Job #${job.id} completed successfully`);
    } catch (error) {
        console.error(`[JOB] Job #${job.id} failed:`, error.message);

        // Report failure
        sendMessage('/app/print-agent/job-failed', {
            jobId: job.id,
            agentId: config.agentId,
            error: error.message
        });
    }
}

/**
 * Graceful shutdown
 */
function shutdown() {
    console.log('\n[APP] Shutting down...');

    if (stompClient && isConnected) {
        sendMessage('/app/print-agent/disconnect', {
            agentId: config.agentId
        });
        stompClient.deactivate();
    }

    process.exit(0);
}

// Handle shutdown signals
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

// Start the agent
console.log('[APP] Starting print agent...');
printerManager.initialize()
    .then(() => {
        console.log('[PRINTER] Printer initialized');
        connect();
    })
    .catch((error) => {
        console.error('[PRINTER] Failed to initialize printer:', error.message);
        console.log('[APP] Starting anyway, will retry printing when jobs arrive...');
        connect();
    });
