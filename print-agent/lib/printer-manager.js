/**
 * Printer Manager
 *
 * Handles communication with thermal printers via USB or network
 */

const escpos = require('escpos');

// Try to load USB adapter (may fail on some systems)
let USB = null;
try {
    USB = require('escpos-usb');
    escpos.USB = USB;
} catch (e) {
    console.log('[PRINTER] USB support not available:', e.message);
}

// Try to load Network adapter
let Network = null;
try {
    Network = require('escpos-network');
    escpos.Network = Network;
} catch (e) {
    console.log('[PRINTER] Network support not available:', e.message);
}

class PrinterManager {
    constructor(config) {
        this.config = config;
        this.device = null;
        this.printer = null;
        this.isInitialized = false;
    }

    /**
     * Initialize the printer connection
     */
    async initialize() {
        if (this.config.printerType === 'network') {
            return this.initializeNetworkPrinter();
        } else {
            return this.initializeUSBPrinter();
        }
    }

    /**
     * Initialize USB printer
     */
    async initializeUSBPrinter() {
        if (!USB) {
            throw new Error('USB support not available. Install libusb.');
        }

        return new Promise((resolve, reject) => {
            try {
                // If printer name specified, try to find it
                if (this.config.printerName) {
                    const devices = USB.findPrinter();
                    const found = devices.find(d =>
                        d.deviceDescriptor &&
                        (d.deviceDescriptor.iProduct === this.config.printerName ||
                         d.deviceDescriptor.idProduct === this.config.printerName)
                    );
                    if (found) {
                        this.device = new USB(found);
                    } else {
                        // Try default
                        this.device = new USB();
                    }
                } else {
                    // Auto-detect first available printer
                    this.device = new USB();
                }

                this.printer = new escpos.Printer(this.device);
                this.isInitialized = true;
                console.log('[PRINTER] USB printer initialized');
                resolve();
            } catch (error) {
                reject(new Error(`Failed to initialize USB printer: ${error.message}`));
            }
        });
    }

    /**
     * Initialize network printer
     */
    async initializeNetworkPrinter() {
        if (!Network) {
            throw new Error('Network support not available');
        }

        return new Promise((resolve, reject) => {
            try {
                if (!this.config.printerIp) {
                    reject(new Error('Printer IP not configured'));
                    return;
                }

                this.device = new Network(this.config.printerIp, this.config.printerPort);
                this.printer = new escpos.Printer(this.device);
                this.isInitialized = true;
                console.log(`[PRINTER] Network printer initialized: ${this.config.printerIp}:${this.config.printerPort}`);
                resolve();
            } catch (error) {
                reject(new Error(`Failed to initialize network printer: ${error.message}`));
            }
        });
    }

    /**
     * Print ESC/POS commands
     */
    async print(commands, job) {
        // Use job-specific printer settings if available
        const printerIp = job.printerIp || this.config.printerIp;
        const printerPort = job.printerPort || this.config.printerPort;
        const connectionType = job.connectionType || this.config.printerType;

        if (connectionType === 'NETWORK' || connectionType === 'network') {
            return this.printToNetwork(commands, printerIp, printerPort);
        } else {
            return this.printToUSB(commands);
        }
    }

    /**
     * Print to network printer
     */
    async printToNetwork(commands, ip, port) {
        return new Promise((resolve, reject) => {
            if (!Network) {
                reject(new Error('Network support not available'));
                return;
            }

            const device = new Network(ip, port);
            const printer = new escpos.Printer(device);

            device.open((error) => {
                if (error) {
                    reject(new Error(`Failed to connect to printer: ${error.message}`));
                    return;
                }

                try {
                    // Execute the commands
                    commands(printer);

                    printer
                        .cut()
                        .close(() => {
                            resolve();
                        });
                } catch (e) {
                    device.close();
                    reject(e);
                }
            });
        });
    }

    /**
     * Print to USB printer
     */
    async printToUSB(commands) {
        return new Promise((resolve, reject) => {
            if (!this.device || !this.printer) {
                reject(new Error('USB printer not initialized'));
                return;
            }

            this.device.open((error) => {
                if (error) {
                    reject(new Error(`Failed to open USB printer: ${error.message}`));
                    return;
                }

                try {
                    // Execute the commands
                    commands(this.printer);

                    this.printer
                        .cut()
                        .close(() => {
                            resolve();
                        });
                } catch (e) {
                    this.device.close();
                    reject(e);
                }
            });
        });
    }

    /**
     * List available USB printers
     */
    static listUSBPrinters() {
        if (!USB) {
            console.log('USB support not available');
            return [];
        }

        try {
            const devices = USB.findPrinter();
            console.log('Available USB printers:');
            devices.forEach((d, i) => {
                console.log(`  ${i + 1}. VID: ${d.deviceDescriptor.idVendor}, PID: ${d.deviceDescriptor.idProduct}`);
            });
            return devices;
        } catch (e) {
            console.log('Error listing printers:', e.message);
            return [];
        }
    }
}

module.exports = PrinterManager;
