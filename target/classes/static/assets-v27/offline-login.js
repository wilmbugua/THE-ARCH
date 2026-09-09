(function(){
  const realFetch = window.fetch.bind(window);
  const STORAGE_KEY = 'kalc_offline_auth';

  function makeResponse(payload){
    return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }

  function saveToStorage(token, user){
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: token, user: user, ts: Date.now() })); } catch(e) { /* ignore */ }
  }

  function readFromStorage(){
    try { const s = localStorage.getItem(STORAGE_KEY); return s ? JSON.parse(s) : null; } catch(e){ return null }
  }

  window.fetch = async function(input, init){
    // Normalize URL and allow relative /api/* to be redirected to backend directly
    let url = (typeof input === 'string') ? input : (input && input.url);
    const cfg = window.__KALC_CONFIG || {};
    const apiBase = cfg.API_BASE || '';

    // If the request is a relative API path and apiBase is set, rewrite to backend directly.
    // If apiBase is empty, keep relative URLs so the dev proxy can handle CORS.
    if (apiBase && typeof url === 'string' && url.startsWith('/api/')) {
      url = apiBase + url;
      if (typeof input === 'object') input = new Request(url, input);
      else input = url;
    }

    const isLogin = url && (url.indexOf('/api/login') !== -1 || url.indexOf('/api/v1/auth/pin-login') !== -1);

    try {
      const res = await realFetch(input, init);
      if (isLogin && res && res.ok) {
        // try to persist credentials from response
        try {
          const clone = res.clone();
          const data = await clone.json().catch(() => null);
          if (data && (data.token || data.user)) {
            saveToStorage(data.token, data.user || null);
          }
        } catch (e) { /* ignore parsing errors */ }
      }

      if (isLogin && (!res || !res.ok)) {
        // backend returned error; try stored credentials
        const stored = readFromStorage();
        if (stored) return makeResponse({ token: stored.token, user: stored.user });
        const fallback = { token: 'offline-token', user: { name: 'Offline User' } };
        saveToStorage(fallback.token, fallback.user);
        return makeResponse(fallback);
      }

      return res;
    } catch (e) {
      if (isLogin) {
        const stored = readFromStorage();
        if (stored) return makeResponse({ token: stored.token, user: stored.user });
        const fallback = { token: 'offline-token', user: { name: 'Offline User' } };
        saveToStorage(fallback.token, fallback.user);
        return makeResponse(fallback);
      }
      throw e;
    }
  };

  window.__KALC_OFFLINE_LOGIN = true;
})();
