package com.elcafe.modules.pos.display.enums;

/**
 * Types of customer display connection methods.
 */
public enum DisplayConnectionType {
    SERIAL,     // RS-232 serial (pole displays)
    USB,        // USB connection
    NETWORK,    // TCP/IP network
    WEBSOCKET,  // WebSocket (for screens/tablets)
    HDMI        // Direct HDMI connection
}
