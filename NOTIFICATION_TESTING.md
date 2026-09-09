# Payment Notification System - Quick Start Guide

## What Was Implemented

### 1. Frontend Notification Module (`payment-notification.js`)
- Manages browser notification permissions
- Polls for new payments every 3 seconds
- Displays desktop notifications with payment details
- Stores settings in localStorage
- Provides global API via `window.__KALC_PAYMENTS`

### 2. Notification Settings UI (`payment-notification-settings.js`)
- Floating settings button in bottom-right corner
- Settings panel with:
  - Toggle notifications on/off
  - Toggle notification sound on/off
  - View permission status
  - Request permissions
  - Test notifications
- Responsive design, works on mobile and desktop

### 3. Backend API Controller (`PaymentNotificationController.java`)
- `/api/v1/payments/recent` - Get recent payments for polling
- `/api/v1/payments/stats` - Get payment statistics
- `/api/v1/payments/{id}` - Get single payment details
- Requires authentication (Bearer token)
- Only accessible to admin/manager/super_waiter roles

### 4. HTML Integration
- Scripts loaded in `frontend/index.html`
- Loads before main React app
- Initializes automatically

## Quick Testing Steps

### Step 1: Start Backend
```powershell
cd D:\KALCPOS\backend
./start-backend.ps1
# Or start in background
./start-backend-bg.ps1
```

### Step 2: Start Frontend
```powershell
cd D:\KALCPOS\frontend
# Run your dev server or proxy
# Default: http://127.0.0.1:8000
```

### Step 3: Access KALCPOS
- Open http://127.0.0.1:8000 in your browser
- Login as admin/staff user

### Step 4: Grant Notification Permission
- Look for settings button (âš™ï¸) in bottom-right corner
- Click it to open settings panel
- Click "Request Permission"
- Allow notifications in browser dialog

### Step 5: Test Notifications
- Click "Test Notification" button in settings panel
- You should see a desktop notification appear
- Note: May require browser window to be focused first time

### Step 6: Trigger Real Payment
- Create an order
- Complete payment (Cash, M-Pesa, or Card)
- You should receive a notification for the payment
- If not, check browser console (F12) for errors

## Verification Checklist

- [ ] Backend starts without errors
- [ ] Frontend loads without errors
- [ ] Settings button appears in bottom-right corner
- [ ] Settings panel opens and closes properly
- [ ] Permission request works
- [ ] Test notification appears
- [ ] Console shows "[KALC-Notifications] Payment notification system initialized"
- [ ] New payments trigger notifications
- [ ] Notification sound plays (if enabled)
- [ ] Settings persist after page refresh

## Browser Console Commands

Open DevTools (F12) and try these commands:

```javascript
// Check current settings
window.__KALC_PAYMENTS.getNotificationSettings()

// Enable/disable notifications
window.__KALC_PAYMENTS.enableNotifications(true)
window.__KALC_PAYMENTS.enableNotifications(false)

// Test notification
window.__KALC_PAYMENTS.notifyPayment({
  id: Date.now(),
  amount: '1,500.00',
  method: 'cash',
  orderId: 'TEST-001',
  billId: 'TEST-001',
  status: 'confirmed',
  customerName: 'Test Customer'
})

// Get all notification logs
console.log(window.__KALC_PAYMENTS)
```

## Common Issues & Solutions

### Issue: Settings button doesn't appear
**Solution:** 
- Check browser console for errors (F12)
- Ensure payment-notification-settings.js is loaded
- Clear browser cache and reload

### Issue: Test notification doesn't appear
**Solution:**
- Grant notification permission first
- Check browser notification settings (chrome://settings/content/notifications)
- Try clicking the browser window first
- Ensure notifications are not blocked by OS

### Issue: Real payments don't trigger notifications
**Solution:**
- Check backend is running: curl http://localhost:8081/api/v1/health
- Check browser console for API errors
- Verify your user role is admin/manager/super_waiter
- Check network tab in DevTools to see if API calls are being made

### Issue: Notifications blocked by browser
**Solution:**
- Check address bar for notification permission indicator
- Click it to change to "Always allow"
- Or go to browser settings â†’ Privacy â†’ Notifications â†’ Find KALCPOS URL â†’ Allow

## Environment Variables

Backend configuration (optional):
```
KALC_SERVER_PORT=8081 (default)
KALC_DB_URL=jdbc:mariadb://localhost:3306/kalc_pos_web
KALC_DB_USER=root
KALC_DB_PASSWORD=Kaisy@3030
```

## Files Added/Modified

**New Files:**
- `frontend/assets/payment-notification.js` (10 KB)
- `frontend/assets/payment-notification-settings.js` (13.5 KB)
- `src/main/java/com/kalcpos/api/PaymentNotificationController.java` (7.6 KB)
- `PAYMENT_NOTIFICATIONS.md` (6.3 KB)
- `pom.xml` (updated with main class)

**Modified Files:**
- `frontend/index.html` (added script tags)

**Directories Created:**
- `src/main/java/com/kalcpos/api/`

## Next Steps

1. **Test the system thoroughly** with real payment scenarios
2. **Gather user feedback** on notification behavior
3. **Monitor logs** to identify any edge cases
4. **Consider future enhancements:**
   - WebSocket support for real-time updates (remove polling)
   - Notification history/archive
   - Admin dashboard with payment notifications summary
   - Customizable notification messages

## Support

For issues or questions:
1. Check the console (F12) for error messages
2. Review `PAYMENT_NOTIFICATIONS.md` for detailed documentation
3. Check backend logs at `backend/logs/backend.log`
4. Verify database connectivity

---

**Status:** âœ… Implementation Complete  
**Version:** 1.0  
**Date:** August 2026

