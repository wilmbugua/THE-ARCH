/**
 * Payment Notification System
 * Handles browser notifications for new payment confirmations
 * Desktop notifications for admin/staff when payments are received
 */

(function() {
  'use strict';

  const NOTIFICATION_PERMISSION_KEY = 'kalc_notification_permission_requested';
  const NOTIFICATION_ENABLED_KEY = 'kalc_notifications_enabled';
  const NOTIFICATION_SOUND_KEY = 'kalc_notification_sound_enabled';
  const LAST_PAYMENT_ID_KEY = 'kalc_last_notified_payment_id';

  class PaymentNotificationManager {
    constructor() {
      this.enabled = this.loadSettings();
      this.lastNotifiedPaymentId = localStorage.getItem(LAST_PAYMENT_ID_KEY) || '0';
      this.notificationPermission = Notification.permission;
      
      // Auto-request permission on first load if not asked
      if (this.enabled && !this.permissionRequested()) {
        this.requestPermission();
      }

      // Start polling for payment updates
      this.startPaymentPolling();
    }

    /**
     * Request notification permission from user
     */
    requestPermission() {
      if (!('Notification' in window)) {
        console.warn('[KALC-Notifications] Browser does not support notifications');
        return;
      }

      if (this.notificationPermission === 'granted') {
        localStorage.setItem(NOTIFICATION_PERMISSION_KEY, 'true');
        return;
      }

      if (this.notificationPermission !== 'denied') {
        Notification.requestPermission().then((permission) => {
          this.notificationPermission = permission;
          localStorage.setItem(NOTIFICATION_PERMISSION_KEY, 'true');
          if (permission === 'granted') {
            console.log('[KALC-Notifications] Permission granted for notifications');
          }
        });
      }
    }

    /**
     * Check if permission has been requested before
     */
    permissionRequested() {
      return localStorage.getItem(NOTIFICATION_PERMISSION_KEY) === 'true';
    }

    /**
     * Load notification settings from localStorage
     */
    loadSettings() {
      const settingValue = localStorage.getItem(NOTIFICATION_ENABLED_KEY);
      // Default to enabled if not explicitly set
      return settingValue !== 'false';
    }

    /**
     * Enable/disable notifications
     */
    setEnabled(enabled) {
      this.enabled = enabled;
      localStorage.setItem(NOTIFICATION_ENABLED_KEY, enabled ? 'true' : 'false');
    }

    /**
     * Enable/disable notification sound
     */
    setSoundEnabled(enabled) {
      localStorage.setItem(NOTIFICATION_SOUND_KEY, enabled ? 'true' : 'false');
    }

    /**
     * Check if notification sound is enabled
     */
    isSoundEnabled() {
      const settingValue = localStorage.getItem(NOTIFICATION_SOUND_KEY);
      // Default to enabled
      return settingValue !== 'false';
    }

    /**
     * Send a browser notification
     */
    notify(title, options = {}) {
      if (!this.enabled || this.notificationPermission !== 'granted') {
        return;
      }

      try {
        const notification = new Notification(title, {
          icon: '/brand/kalc-mark.svg',
          badge: '/brand/kalc-mark.svg',
          ...options,
        });

        // Auto-close notification after 5 seconds
        const timeout = (options.tag && options.tag.startsWith('payment-')) ? 7000 : 5000;
        setTimeout(() => notification.close(), timeout);

        // Play sound if enabled
        if (this.isSoundEnabled()) {
          this.playNotificationSound();
        }

        return notification;
      } catch (e) {
        console.error('[KALC-Notifications] Error sending notification:', e);
      }
    }

    /**
     * Send payment confirmation notification
     */
    notifyPayment(paymentData) {
      if (!this.enabled) {
        return;
      }

      const {
        id = 'unknown',
        amount = '0.00',
        method = 'cash',
        customerName = 'Customer',
        orderId = 'unknown',
        billId = 'unknown',
        status = 'confirmed'
      } = paymentData;

      const methodLabel = this.formatPaymentMethod(method);
      const title = `💰 Payment ${status === 'pending' ? 'Received (Pending)' : 'Confirmed'}`;
      const body = `${customerName}\n${amount} KES via ${methodLabel}\nOrder #${orderId}`;

      this.notify(title, {
        body,
        tag: `payment-${id}`,
        requireInteraction: status === 'pending',
      });

      // Track last notified payment
      localStorage.setItem(LAST_PAYMENT_ID_KEY, String(id));
    }

    /**
     * Format payment method for display
     */
    formatPaymentMethod(method) {
      const methods = {
        'cash': '💵 Cash',
        'mpesa': '📱 M-Pesa',
        'pdq': '💳 Card (PDQ)',
        'card': '💳 Card'
      };
      return methods[method?.toLowerCase()] || `${method}`;
    }

    /**
     * Play notification sound
     */
    playNotificationSound() {
      // Create a simple beep using Web Audio API as fallback
      try {
        if ('AudioContext' in window || 'webkitAudioContext' in window) {
          const AudioContext = window.AudioContext || window.webkitAudioContext;
          const ctx = new AudioContext();
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          
          osc.connect(gain);
          gain.connect(ctx.destination);
          
          osc.frequency.value = 800;
          gain.gain.setValueAtTime(0.3, ctx.currentTime);
          gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.1);
          
          osc.start(ctx.currentTime);
          osc.stop(ctx.currentTime + 0.1);
        }
      } catch (e) {
        console.warn('[KALC-Notifications] Could not play notification sound:', e.message);
      }
    }

    /**
     * Start polling for new payment confirmations
     */
    startPaymentPolling() {
      // Poll every 3 seconds
      setInterval(() => this.checkForNewPayments(), 3000);
    }

    /**
     * Check for new payments from the API
     */
    async checkForNewPayments() {
      if (!this.enabled) {
        return;
      }

      try {
        const authToken = this.getAuthToken();
        if (!authToken) return;

        const response = await fetch(`${window.__KALC_CONFIG.API_BASE}/api/v1/payments/recent?limit=10`, {
          headers: {
            'Authorization': `Bearer ${authToken}`,
            'Content-Type': 'application/json',
          },
        });

        if (response.status === 401) {
          // Token expired, clear last payment ID
          localStorage.removeItem(LAST_PAYMENT_ID_KEY);
          return;
        }

        if (!response.ok) {
          console.debug('[KALC-Notifications] API endpoint not available:', response.status);
          return;
        }

        const data = await response.json();
        if (!Array.isArray(data.payments)) return;

        const lastId = parseInt(this.lastNotifiedPaymentId, 10);
        // Process payments in reverse order (oldest to newest)
        for (let i = data.payments.length - 1; i >= 0; i--) {
          const payment = data.payments[i];
          if (payment.id > lastId && (payment.status === 'confirmed' || payment.status === 'auto_confirmed' || payment.status === 'pending')) {
            this.notifyPayment({
              id: payment.id,
              amount: payment.amount,
              method: payment.mode || payment.method,
              orderId: payment.order_id,
              billId: payment.bill_id,
              status: payment.status,
              customerName: payment.customer_name || 'Customer',
            });
          }
        }
      } catch (e) {
        console.debug('[KALC-Notifications] Error checking for new payments:', e.message);
      }
    }

    /**
     * Get auth token from localStorage
     */
    getAuthToken() {
      try {
        // Check for token in the app session storage first, then legacy helper storage.
        const authStr = localStorage.getItem('kalc_pos_session') || localStorage.getItem('kalc_auth_session');
        if (authStr) {
          const auth = JSON.parse(authStr);
          return auth.token;
        }
      } catch (e) {
        // Ignore parsing errors
      }
      return null;
    }

    /**
     * Get current settings
     */
    getSettings() {
      return {
        enabled: this.enabled,
        soundEnabled: this.isSoundEnabled(),
        permissionGranted: this.notificationPermission === 'granted',
        permissionDenied: this.notificationPermission === 'denied',
        permissionRequested: this.permissionRequested(),
      };
    }

    /**
     * Reset notification system (clear settings)
     */
    reset() {
      localStorage.removeItem(NOTIFICATION_PERMISSION_KEY);
      localStorage.removeItem(NOTIFICATION_ENABLED_KEY);
      localStorage.removeItem(NOTIFICATION_SOUND_KEY);
      localStorage.removeItem(LAST_PAYMENT_ID_KEY);
      this.enabled = this.loadSettings();
    }
  }

  // Initialize globally
  if (!window.__KALC_PAYMENTS) {
    window.__KALC_PAYMENTS = {};
  }

  window.__KALC_PAYMENTS.notificationManager = new PaymentNotificationManager();

  // Expose helper functions for easy access from components
  window.__KALC_PAYMENTS.enableNotifications = (enabled) => {
    window.__KALC_PAYMENTS.notificationManager.setEnabled(enabled);
  };

  window.__KALC_PAYMENTS.setSoundEnabled = (enabled) => {
    window.__KALC_PAYMENTS.notificationManager.setSoundEnabled(enabled);
  };

  window.__KALC_PAYMENTS.requestNotificationPermission = () => {
    window.__KALC_PAYMENTS.notificationManager.requestPermission();
  };

  window.__KALC_PAYMENTS.getNotificationSettings = () => {
    return window.__KALC_PAYMENTS.notificationManager.getSettings();
  };

  window.__KALC_PAYMENTS.notifyPayment = (paymentData) => {
    window.__KALC_PAYMENTS.notificationManager.notifyPayment(paymentData);
  };

  console.log('[KALC-Notifications] Payment notification system initialized');
})();
