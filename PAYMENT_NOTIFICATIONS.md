# Payment Confirmation Notification System

## Overview
KALCPOS now includes a real-time browser notification system for admin and staff users. When new payments are received or confirmed, staff will receive desktop notifications even if the browser window is not in focus.

## Features

✅ **Real-time Notifications** - Automatic browser desktop notifications when payments are confirmed
✅ **Configurable Settings** - Enable/disable notifications and sound preferences
✅ **Permission Management** - Intuitive permission request dialog
✅ **Test Mode** - Test notifications to verify system is working
✅ **Notification Sounds** - Optional audio feedback for new payments
✅ **Auto-polling** - Checks for new payments every 3 seconds
✅ **Multi-method Support** - Notifications for Cash, M-Pesa, and Card (PDQ) payments

## How It Works

### On First Use
1. User opens KALCPOS in their browser
2. Payment notification module loads automatically
3. Settings button (⚙️) appears in bottom-right corner
4. User clicks to open notification settings panel
5. Click "Request Permission" to enable notifications

### During Operation
- Payment notification system continuously polls for new payments every 3 seconds
- When a new payment is confirmed/received, a browser notification appears
- Notification displays:
  - Payment status (✓ Confirmed or ⏳ Pending)
  - Amount and payment method (Cash, M-Pesa, Card)
  - Customer name
  - Order number
- Notifications auto-close after 5-7 seconds
- Optional notification sound plays (configurable)

## Accessing Settings

### Settings Button
- Located in **bottom-right corner** of the screen
- Click to open the notification settings panel
- Settings are saved automatically to browser

### Settings Panel Options

**Enable Payment Notifications**
- Toggle to enable/disable all payment notifications
- Notifications won't appear if disabled
- Default: Enabled

**Notification Sound**
- Toggle to enable/disable audio feedback
- Plays when notifications arrive
- Default: Enabled

**Permission Status**
- Shows current notification permission status
- ✓ Notifications Enabled
- ✗ Notifications Blocked
- ? Ask for Permission

**Test Notification**
- Send a test notification to verify system is working
- Useful for confirming notifications are enabled

## Browser Compatibility

✅ Chrome/Chromium 90+
✅ Firefox 78+
✅ Safari 14.1+
✅ Edge 90+

Note: Notifications require explicit user permission granted via the browser's permission dialog.

## Troubleshooting

### Notifications Not Appearing

**Check 1: Permission Status**
- Open settings panel
- Check "Permission Status" indicator
- If showing "Notifications Blocked", you need to:
  1. Check browser permissions (address bar → site settings)
  2. Allow notifications for KALCPOS
  3. Refresh the page

**Check 2: Notifications Enabled**
- Verify "Enable Payment Notifications" toggle is ON

**Check 3: Test Notification**
- Click "Test Notification" button
- If test notification doesn't appear, browser notifications may be blocked system-wide

**Check 4: Browser Settings**
- Windows: Settings → System → Notifications → Check browser is allowed
- Mac: System Preferences → Notifications → Allow for browser
- Linux: Check notification daemon is running

### Sound Not Playing

- Open settings and verify "Notification Sound" toggle is ON
- Check your system volume settings
- Some browsers require user interaction before audio can play

### Notifications Only Show When Tab Is Focused

- This is normal behavior in some browsers
- Modern browsers can display notifications in background with proper permissions
- Ensure you've granted "Allow background notifications" permission

## API Endpoints

### Get Recent Payments
```
GET /api/v1/payments/recent?limit=10
Authorization: Bearer {token}
```

Returns the 10 most recent payments for notification polling.

**Response:**
```json
{
  "payments": [
    {
      "id": 123,
      "order_id": 456,
      "bill_id": 789,
      "mode": "cash",
      "status": "confirmed",
      "amount": "1200.00",
      "customer_name": "John Doe",
      "verified_at": "2026-08-03T10:47:21"
    }
  ],
  "count": 1,
  "user_id": 1,
  "user_role": "admin"
}
```

### Get Payment Statistics
```
GET /api/v1/payments/stats
Authorization: Bearer {token}
```

Returns payment statistics for today.

### Get Single Payment Details
```
GET /api/v1/payments/{paymentId}
Authorization: Bearer {token}
```

Returns detailed information for a specific payment.

## Data Storage

Notification settings are stored in browser localStorage:
- `kalc_notifications_enabled` - Whether notifications are enabled
- `kalc_notification_sound_enabled` - Whether sound is enabled
- `kalc_notification_permission_requested` - Whether permission was requested
- `kalc_last_notified_payment_id` - Last payment ID that was notified (prevents duplicates)

To reset notification settings:
1. Open browser DevTools (F12)
2. Go to Application → Local Storage
3. Find the KALCPOS URL
4. Delete the above keys
5. Refresh the page

## Privacy & Security

- Notification data is NOT sent to any external service
- Settings are stored only in your browser's local storage
- Notifications require valid authentication token
- Only admin/manager/super_waiter roles can access payment notifications

## Monitoring Payment Events

For development/monitoring purposes, the notification system logs to browser console:

```javascript
// View all payment notification events
localStorage.setItem('kalc_debug_notifications', 'true');
// Check console for detailed logs
```

## Backend Integration

The notification system integrates with the KALCPOS backend through the PaymentNotificationController.

### Verification Command
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8081/api/v1/payments/recent
```

## Support

For issues with payment notifications:
1. Check browser console for error messages (F12)
2. Verify notification permissions in browser settings
3. Test with the "Test Notification" button
4. Check that your user role includes payment access
5. Ensure backend API is running on expected port

---

**Version:** 1.0  
**Last Updated:** August 2026  
**Requires:** KALCPOS 0.0.1+
