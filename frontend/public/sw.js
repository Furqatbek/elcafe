/**
 * Service Worker for Push Notifications
 */

// Cache name for offline support
const CACHE_NAME = 'qahvoon-push-v1';

// Install event
self.addEventListener('install', (event) => {
  console.log('Service Worker installing...');
  self.skipWaiting();
});

// Activate event
self.addEventListener('activate', (event) => {
  console.log('Service Worker activating...');
  event.waitUntil(clients.claim());
});

// Push event - handle incoming push notifications
self.addEventListener('push', (event) => {
  console.log('Push received:', event);

  let notificationData = {
    title: 'Qahvoon',
    body: 'You have a new notification',
    icon: '/icon-192.png',
    badge: '/badge-72.png',
    tag: 'qahvoon-notification',
    data: {}
  };

  // Parse push data
  if (event.data) {
    try {
      const data = event.data.json();
      notificationData = {
        title: data.title || notificationData.title,
        body: data.body || notificationData.body,
        icon: data.icon || notificationData.icon,
        image: data.image,
        badge: data.badge || notificationData.badge,
        tag: data.tag || notificationData.tag,
        data: data.data || {},
        actions: data.actions || []
      };
    } catch (e) {
      console.error('Error parsing push data:', e);
      notificationData.body = event.data.text();
    }
  }

  // Show notification
  const notificationOptions = {
    body: notificationData.body,
    icon: notificationData.icon,
    badge: notificationData.badge,
    tag: notificationData.tag,
    data: notificationData.data,
    requireInteraction: true,
    vibrate: [200, 100, 200]
  };

  // Add image if present
  if (notificationData.image) {
    notificationOptions.image = notificationData.image;
  }

  // Add actions if present
  if (notificationData.actions && notificationData.actions.length > 0) {
    notificationOptions.actions = notificationData.actions;
  }

  event.waitUntil(
    self.registration.showNotification(notificationData.title, notificationOptions)
  );
});

// Notification click event
self.addEventListener('notificationclick', (event) => {
  console.log('Notification clicked:', event);
  event.notification.close();

  const notificationData = event.notification.data || {};
  let targetUrl = '/';

  // Handle action clicks
  if (event.action) {
    switch (event.action) {
      case 'view':
        targetUrl = notificationData.url || '/';
        break;
      case 'dismiss':
        return; // Just close
      default:
        if (notificationData.actionUrls && notificationData.actionUrls[event.action]) {
          targetUrl = notificationData.actionUrls[event.action];
        }
    }
  } else {
    // Main notification click
    targetUrl = notificationData.url || '/';
  }

  // Open or focus the appropriate window
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        // Check if there's already a window open
        for (const client of clientList) {
          if (client.url.includes(self.location.origin) && 'focus' in client) {
            client.navigate(targetUrl);
            return client.focus();
          }
        }
        // Open new window
        if (clients.openWindow) {
          return clients.openWindow(targetUrl);
        }
      })
  );
});

// Notification close event
self.addEventListener('notificationclose', (event) => {
  console.log('Notification closed:', event);
  // Could send analytics here
});

// Fetch event (for potential caching)
self.addEventListener('fetch', (event) => {
  // Pass through for now
  event.respondWith(fetch(event.request));
});
