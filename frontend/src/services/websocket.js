import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Get WebSocket URL from environment or default
const getWebSocketUrl = () => {
  const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';
  // Remove /api/v1 and add /ws-waiter
  const baseUrl = apiUrl.replace('/api/v1', '');
  return `${baseUrl}/ws-waiter`;
};

class WebSocketService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.subscriptions = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 10;
    this.reconnectDelay = 3000;
    this.listeners = new Map();
    this.connectPromise = null; // Track ongoing connection attempt
  }

  connect() {
    if (this.client && this.connected) {
      return Promise.resolve();
    }

    // If a connection attempt is already in progress, return that promise
    if (this.connectPromise) {
      return this.connectPromise;
    }

    this.connectPromise = new Promise((resolve, reject) => {
      const wsUrl = getWebSocketUrl();
      console.log('[WebSocket] Connecting to:', wsUrl);

      this.client = new Client({
        webSocketFactory: () => new SockJS(wsUrl),
        reconnectDelay: this.reconnectDelay,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: (str) => {
          if (import.meta.env.DEV) {
            console.log('[WebSocket Debug]', str);
          }
        },
        // Authenticate the STOMP CONNECT so the backend binds this session's tenant and rejects
        // cross-tenant subscriptions (audit #21). beforeConnect re-reads the token on every (re)connect,
        // so a token rotated mid-session by the axios refresh interceptor isn't stale on reconnect.
        // STOMP CONNECT headers are separate from the SockJS HTTP handshake.
        beforeConnect: () => {
          const token = localStorage.getItem('access_token');
          this.client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
        },
        onConnect: () => {
          console.log('[WebSocket] Connected successfully');
          this.connected = true;
          this.reconnectAttempts = 0;
          this.connectPromise = null;

          // Re-subscribe to any existing subscriptions after reconnect
          this.resubscribeAll();

          // Notify listeners
          this.notifyListeners('connect', { connected: true });
          resolve();
        },
        onDisconnect: () => {
          console.log('[WebSocket] Disconnected');
          this.connected = false;
          this.notifyListeners('disconnect', { connected: false });
        },
        onStompError: (frame) => {
          console.error('[WebSocket] STOMP error:', frame);
          this.connectPromise = null;
          this.notifyListeners('error', { error: frame });
          reject(frame);
        },
        onWebSocketError: (event) => {
          console.error('[WebSocket] WebSocket error:', event);
          this.notifyListeners('error', { error: event });
        },
      });

      this.client.activate();
    });
  }

  disconnect() {
    if (this.client) {
      console.log('[WebSocket] Disconnecting...');
      this.client.deactivate();
      this.client = null;
      this.connected = false;
      this.subscriptions.clear();
    }
  }

  subscribe(destination, callback) {
    // Check if already subscribed to this destination
    const existing = this.subscriptions.get(destination);
    if (existing && existing.subscription) {
      console.log('[WebSocket] Already subscribed to:', destination);
      return existing.subscription;
    }

    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, queuing subscription:', destination);
      // Store the subscription for later (will be subscribed in resubscribeAll)
      this.subscriptions.set(destination, { callback, subscription: null });
      return;
    }

    console.log('[WebSocket] Subscribing to:', destination);
    try {
      const subscription = this.client.subscribe(destination, (message) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch (e) {
          console.error('[WebSocket] Error parsing message:', e);
          callback(message.body);
        }
      });

      this.subscriptions.set(destination, { callback, subscription });
      return subscription;
    } catch (error) {
      console.error('[WebSocket] Failed to subscribe:', error);
      // Queue for later
      this.subscriptions.set(destination, { callback, subscription: null });
      return;
    }
  }

  unsubscribe(destination) {
    const sub = this.subscriptions.get(destination);
    if (sub && sub.subscription) {
      sub.subscription.unsubscribe();
    }
    this.subscriptions.delete(destination);
    console.log('[WebSocket] Unsubscribed from:', destination);
  }

  resubscribeAll() {
    this.subscriptions.forEach((value, destination) => {
      if (value.callback) {
        console.log('[WebSocket] Re-subscribing to:', destination);
        const subscription = this.client.subscribe(destination, (message) => {
          try {
            const body = JSON.parse(message.body);
            value.callback(body);
          } catch (e) {
            console.error('[WebSocket] Error parsing message:', e);
            value.callback(message.body);
          }
        });
        value.subscription = subscription;
      }
    });
  }

  send(destination, body) {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot send message');
      return false;
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
    return true;
  }

  addListener(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event).add(callback);
  }

  removeListener(event, callback) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).delete(callback);
    }
  }

  notifyListeners(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach((callback) => {
        try {
          callback(data);
        } catch (e) {
          console.error('[WebSocket] Error in listener:', e);
        }
      });
    }
  }

  isConnected() {
    return this.connected;
  }
}

// Singleton instance
const websocketService = new WebSocketService();

export default websocketService;
