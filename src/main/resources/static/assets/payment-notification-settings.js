/**
 * Payment Notification Settings UI
 * Admin panel for managing payment notification preferences
 */

(function() {
  'use strict';

  class NotificationSettingsUI {
    constructor() {
      this.container = null;
      this.isOpen = false;
    }

    /**
     * Initialize and inject settings panel into the page
     */
    init() {
      this.createSettingsButton();
      this.createSettingsModal();
      this.attachEventListeners();
    }

    /**
     * Create the floating settings button
     */
    createSettingsButton() {
      const button = document.createElement('div');
      button.id = 'kalc-notification-settings-btn';
      button.className = 'kalc-notification-settings-btn';
      button.innerHTML = `
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="1"></circle>
          <path d="M12 1v6m0 6v6"></path>
          <path d="M4.22 4.22l4.24 4.24m2.12 2.12l4.24 4.24"></path>
          <path d="M1 12h6m6 0h6"></path>
          <path d="M4.22 19.78l4.24-4.24m2.12-2.12l4.24-4.24"></path>
          <path d="M19.78 4.22l-4.24 4.24m-2.12 2.12l-4.24 4.24"></path>
          <path d="M19.78 19.78l-4.24-4.24m-2.12-2.12l-4.24-4.24"></path>
        </svg>
      `;
      button.title = 'Notification Settings';
      button.addEventListener('click', () => this.toggleModal());
      document.body.appendChild(button);
    }

    /**
     * Create the settings modal
     */
    createSettingsModal() {
      this.container = document.createElement('div');
      this.container.id = 'kalc-notification-settings-modal';
      this.container.className = 'kalc-notification-settings-modal hidden';
      
      const settings = window.__KALC_PAYMENTS.getNotificationSettings();
      
      this.container.innerHTML = `
        <div class="kalc-notification-settings-content">
          <div class="kalc-notification-settings-header">
            <h3>💬 Notification Settings</h3>
            <button class="kalc-close-btn" aria-label="Close settings">&times;</button>
          </div>
          
          <div class="kalc-notification-settings-body">
            <div class="kalc-setting-group">
              <label class="kalc-setting-label">
                <input 
                  type="checkbox" 
                  id="kalc-notif-enabled" 
                  class="kalc-setting-checkbox"
                  ${settings.enabled ? 'checked' : ''}
                  aria-label="Enable payment notifications"
                />
                <span>Enable Payment Notifications</span>
              </label>
              <p class="kalc-setting-description">
                Receive desktop notifications when new payments are received
              </p>
            </div>

            <div class="kalc-setting-group">
              <label class="kalc-setting-label">
                <input 
                  type="checkbox" 
                  id="kalc-notif-sound" 
                  class="kalc-setting-checkbox"
                  ${settings.soundEnabled ? 'checked' : ''}
                  aria-label="Enable notification sound"
                />
                <span>Notification Sound</span>
              </label>
              <p class="kalc-setting-description">
                Play a sound when a payment notification arrives
              </p>
            </div>

            <div class="kalc-setting-group permission-status">
              <label class="kalc-setting-label">
                <span>Permission Status</span>
              </label>
              <div class="kalc-permission-indicator ${settings.permissionGranted ? 'granted' : settings.permissionDenied ? 'denied' : 'default'}">
                <span class="kalc-permission-icon">
                  ${settings.permissionGranted ? '✓' : settings.permissionDenied ? '✗' : '?'}
                </span>
                <span class="kalc-permission-text">
                  ${settings.permissionGranted ? 'Notifications Enabled' : settings.permissionDenied ? 'Notifications Blocked' : 'Ask for Permission'}
                </span>
              </div>
              ${!settings.permissionGranted && !settings.permissionDenied ? `
                <button class="kalc-btn kalc-btn-primary" id="kalc-request-permission-btn">
                  Request Permission
                </button>
              ` : ''}
            </div>

            <div class="kalc-setting-group info-box">
              <p>
                💡 <strong>Tip:</strong> Notifications work best when you keep the tab open. 
                Modern browsers can also display notifications when the tab is in the background.
              </p>
            </div>
          </div>

          <div class="kalc-notification-settings-footer">
            <button class="kalc-btn kalc-btn-secondary" id="kalc-test-notif-btn">
              Test Notification
            </button>
            <button class="kalc-btn kalc-btn-primary" id="kalc-close-settings-btn">
              Close
            </button>
          </div>
        </div>
      `;

      document.body.appendChild(this.container);
    }

    /**
     * Attach event listeners to settings
     */
    attachEventListeners() {
      const closeBtn = this.container.querySelector('.kalc-close-btn');
      const closeSettingsBtn = this.container.querySelector('#kalc-close-settings-btn');
      const enabledCheckbox = this.container.querySelector('#kalc-notif-enabled');
      const soundCheckbox = this.container.querySelector('#kalc-notif-sound');
      const requestPermissionBtn = this.container.querySelector('#kalc-request-permission-btn');
      const testNotifBtn = this.container.querySelector('#kalc-test-notif-btn');

      closeBtn?.addEventListener('click', () => this.toggleModal());
      closeSettingsBtn?.addEventListener('click', () => this.toggleModal());
      
      enabledCheckbox?.addEventListener('change', (e) => {
        window.__KALC_PAYMENTS.enableNotifications(e.target.checked);
        console.log('[KALC-Notifications] Notifications ' + (e.target.checked ? 'enabled' : 'disabled'));
      });

      soundCheckbox?.addEventListener('change', (e) => {
        window.__KALC_PAYMENTS.setSoundEnabled(e.target.checked);
        console.log('[KALC-Notifications] Sound ' + (e.target.checked ? 'enabled' : 'disabled'));
      });

      requestPermissionBtn?.addEventListener('click', () => {
        window.__KALC_PAYMENTS.requestNotificationPermission();
      });

      testNotifBtn?.addEventListener('click', () => {
        window.__KALC_PAYMENTS.notifyPayment({
          id: 'test-' + Date.now(),
          amount: '1,200.00',
          method: 'cash',
          orderId: 'TEST-001',
          billId: 'TEST-001',
          status: 'confirmed',
          customerName: 'Test Customer',
        });
      });

      // Close modal when clicking outside
      this.container.addEventListener('click', (e) => {
        if (e.target === this.container) {
          this.toggleModal();
        }
      });
    }

    /**
     * Toggle modal visibility
     */
    toggleModal() {
      this.isOpen = !this.isOpen;
      if (this.isOpen) {
        this.container.classList.remove('hidden');
      } else {
        this.container.classList.add('hidden');
      }
    }
  }

  // CSS Styles for notification settings UI
  const styles = `
    <style>
      .kalc-notification-settings-btn {
        position: fixed;
        bottom: 20px;
        right: 20px;
        width: 50px;
        height: 50px;
        border-radius: 50%;
        background: linear-gradient(135deg, #0F2D5C 0%, #1a3f7f 100%);
        color: white;
        border: none;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        transition: all 0.3s ease;
        z-index: 999;
      }

      .kalc-notification-settings-btn:hover {
        transform: scale(1.1);
        box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
      }

      .kalc-notification-settings-modal {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
        animation: fadeIn 0.3s ease;
      }

      .kalc-notification-settings-modal.hidden {
        display: none;
      }

      @keyframes fadeIn {
        from {
          opacity: 0;
        }
        to {
          opacity: 1;
        }
      }

      .kalc-notification-settings-content {
        background: white;
        border-radius: 8px;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
        max-width: 450px;
        width: 90%;
        max-height: 80vh;
        overflow-y: auto;
        animation: slideUp 0.3s ease;
      }

      @keyframes slideUp {
        from {
          transform: translateY(20px);
          opacity: 0;
        }
        to {
          transform: translateY(0);
          opacity: 1;
        }
      }

      .kalc-notification-settings-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 20px;
        border-bottom: 1px solid #e5e7eb;
      }

      .kalc-notification-settings-header h3 {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
        color: #0F172A;
      }

      .kalc-close-btn {
        background: none;
        border: none;
        font-size: 28px;
        color: #6b7280;
        cursor: pointer;
        padding: 0;
        width: 32px;
        height: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .kalc-close-btn:hover {
        color: #111827;
      }

      .kalc-notification-settings-body {
        padding: 20px;
      }

      .kalc-setting-group {
        margin-bottom: 20px;
      }

      .kalc-setting-label {
        display: flex;
        align-items: center;
        font-size: 14px;
        font-weight: 500;
        color: #111827;
        cursor: pointer;
        margin-bottom: 8px;
      }

      .kalc-setting-checkbox {
        margin-right: 10px;
        width: 18px;
        height: 18px;
        cursor: pointer;
      }

      .kalc-setting-description {
        margin: 0;
        font-size: 13px;
        color: #6b7280;
        margin-left: 28px;
        line-height: 1.4;
      }

      .kalc-permission-indicator {
        display: flex;
        align-items: center;
        padding: 12px;
        border-radius: 6px;
        font-size: 13px;
        font-weight: 500;
        margin-top: 8px;
      }

      .kalc-permission-indicator.granted {
        background: #dcfce7;
        color: #166534;
        border: 1px solid #bbf7d0;
      }

      .kalc-permission-indicator.denied {
        background: #fee2e2;
        color: #991b1b;
        border: 1px solid #fecaca;
      }

      .kalc-permission-indicator.default {
        background: #e0e7ff;
        color: #3730a3;
        border: 1px solid #c7d2fe;
      }

      .kalc-permission-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 20px;
        height: 20px;
        margin-right: 8px;
        font-weight: bold;
        font-size: 14px;
      }

      .kalc-btn {
        padding: 10px 16px;
        border: none;
        border-radius: 6px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
      }

      .kalc-btn-primary {
        background: #0F2D5C;
        color: white;
      }

      .kalc-btn-primary:hover {
        background: #0a1e3f;
        transform: translateY(-1px);
      }

      .kalc-btn-secondary {
        background: #e5e7eb;
        color: #111827;
      }

      .kalc-btn-secondary:hover {
        background: #d1d5db;
      }

      .kalc-info-box {
        background: #f0f9ff;
        border-left: 4px solid #0284c7;
        padding: 12px;
        border-radius: 4px;
        font-size: 13px;
        color: #0c4a6e;
        margin-bottom: 0;
      }

      .kalc-info-box p {
        margin: 0;
        line-height: 1.5;
      }

      .kalc-notification-settings-footer {
        display: flex;
        gap: 10px;
        padding: 15px 20px;
        border-top: 1px solid #e5e7eb;
        justify-content: flex-end;
      }

      #kalc-request-permission-btn {
        margin-top: 10px;
        width: 100%;
      }

      @media (max-width: 600px) {
        .kalc-notification-settings-btn {
          bottom: 16px;
          right: 16px;
          width: 45px;
          height: 45px;
        }

        .kalc-notification-settings-content {
          width: 95%;
        }
      }
    </style>
  `;

  // Inject styles
  document.head.insertAdjacentHTML('beforeend', styles);

  // Initialize after page loads
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      const ui = new NotificationSettingsUI();
      ui.init();
      window.__KALC_PAYMENTS.settingsUI = ui;
    });
  } else {
    const ui = new NotificationSettingsUI();
    ui.init();
    window.__KALC_PAYMENTS.settingsUI = ui;
  }

  console.log('[KALC-Notifications] Settings UI initialized');
})();
