KALC POS â€” Developer runbook (short)

Purpose: make local dev reliable and avoid login breakage when backend is offline.

Quick start
1. Start backend (foreground):
   - Open Powershell in D:\KALCPOS\backend
   - ./start-backend.ps1
   (or start detached) ./start-backend-bg.ps1
2. Serve frontend with proxy (recommended):
   - Start proxy server: run D:\KALCPOS\frontend\serve_ps_proxy.ps1 (already provided)
   - Open http://127.0.0.1:8000
3. Smoke check: from repo root run: D:\KALCPOS\frontend\dev_smoke.ps1

Offline login behavior
- frontend/assets/offline-login.js intercepts /api/login failures and will use stored credentials in localStorage under key "kalc_offline_auth".
- To opt out: remove the <script src="/assets/offline-login.js"></script> line from frontend/index.html and reload.
- Stored credentials can be cleared from browser devtools (Application -> Local Storage) or by running a small snippet in console: localStorage.removeItem('kalc_offline_auth')

Files added
- backend/start-backend.ps1 (foreground)
- backend/start-backend-bg.ps1 (detached starter)
- backend/stop-backend.ps1 (stop by jar match)
- backend/health_check.ps1 (health check)
- frontend/assets/config.js (runtime API base)
- frontend/dev_smoke.ps1 (basic smoke tests)

Notes
- Ensure Java is on PATH to run backend scripts.
- Consider adding these checks to CI to catch regressions where frontend assumes backend unreachable.

