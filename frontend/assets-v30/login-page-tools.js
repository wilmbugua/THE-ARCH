(function () {
  const messageStoreKey = "kalc_login_messages";

  const originalFetch = window.fetch ? window.fetch.bind(window) : null;
  if (originalFetch && !window.__kalcLoginToolsFetchWrapped) {
    window.__kalcLoginToolsFetchWrapped = true;
    window.fetch = function () {
      const args = arguments;
      return originalFetch.apply(window, args).then(function (response) {
        try {
          const url = String(args[0] && args[0].url ? args[0].url : args[0]);
          if (response.ok && url.indexOf("/api/v1/messages") !== -1) {
            response.clone().json().then(function (messages) {
              if (Array.isArray(messages)) {
                localStorage.setItem(messageStoreKey, JSON.stringify(messages.slice(0, 20)));
              }
            }).catch(function () {});
          }
        } catch (_) {}
        return response;
      });
    };
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char];
    });
  }

  function readMessages() {
    try {
      const stored = JSON.parse(localStorage.getItem(messageStoreKey) || "[]");
      return Array.isArray(stored) ? stored : [];
    } catch (_) {
      return [];
    }
  }

  function showMessages() {
    const existing = document.querySelector(".login-message-modal");
    if (existing) existing.remove();

    const messages = readMessages();
    const rows = messages.length
      ? messages.map(function (message) {
          const created = message.created_at ? new Date(message.created_at).toLocaleString() : "";
          return '<article class="login-message-card"><h3>' + escapeHtml(message.title || "Message") +
            '</h3><p>' + escapeHtml(message.body || "") + '</p><small>' + escapeHtml(created) + '</small></article>';
        }).join("")
      : '<div class="login-message-empty">No cached messages yet. Log in once while online to sync manager messages.</div>';

    const modal = document.createElement("div");
    modal.className = "login-message-modal";
    modal.innerHTML =
      '<div class="login-message-panel" role="dialog" aria-modal="true" aria-label="Manager messages">' +
      '<div class="login-message-head"><h2>Manager Messages</h2><button type="button" class="login-message-close" aria-label="Close messages">x</button></div>' +
      '<div class="login-message-list">' + rows + '</div></div>';
    modal.addEventListener("click", function (event) {
      if (event.target === modal || event.target.classList.contains("login-message-close")) modal.remove();
    });
    document.body.appendChild(modal);
  }

  function enhanceLogin() {
    const login = document.querySelector(".login-overlay");
    const footer = document.querySelector(".login-footer");
    const enter = document.querySelector(".enter-btn");
    if (!login || !footer || !enter || document.querySelector(".login-icon-actions")) return;

    const actions = document.createElement("div");
    actions.className = "login-icon-actions";

    const loginButton = document.createElement("button");
    loginButton.type = "button";
    loginButton.className = "login-round-action login-power-action";
    loginButton.setAttribute("aria-label", "Login");
    loginButton.innerHTML = "&#x23FB;";
    loginButton.addEventListener("click", function () {
      enter.click();
    });

    const messageButton = document.createElement("button");
    messageButton.type = "button";
    messageButton.className = "login-round-action login-message-action";
    messageButton.setAttribute("aria-label", "Manager messages");
    messageButton.innerHTML = "&#x2709;";
    messageButton.addEventListener("click", showMessages);

    const modeButton = document.createElement("button");
    modeButton.type = "button";
    modeButton.className = "login-mode-toggle";
    modeButton.setAttribute("aria-label", "Toggle online or offline mode");
    function syncModeButton() {
      const mode = localStorage.getItem("kalc_pos_mode") === "client" ? "client" : "server";
      modeButton.textContent = mode === "client" ? "Offline" : "Online";
      modeButton.classList.toggle("offline", mode === "client");
      modeButton.classList.toggle("online", mode !== "client");
      modeButton.setAttribute("title", mode === "client" ? "Offline mode" : "Online mode");
    }
    modeButton.addEventListener("click", function () {
      const current = localStorage.getItem("kalc_pos_mode") === "client" ? "client" : "server";
      localStorage.setItem("kalc_pos_mode", current === "client" ? "server" : "client");
      syncModeButton();
    });
    syncModeButton();

    actions.appendChild(loginButton);
    actions.appendChild(modeButton);
    actions.appendChild(messageButton);
    footer.parentNode.insertBefore(actions, footer);

    const status = document.createElement("div");
    status.className = "login-backend-status offline";
    status.setAttribute("aria-label", "Backend status");
    status.setAttribute("title", "Backend status");
    footer.parentNode.insertBefore(status, actions.nextSibling);
    pollBackendStatus();
  }

  function updateBackendStatus() {
    const status = document.querySelector(".login-backend-status");
    if (!status) return;
    if (status.getAttribute("data-polled") === "true") return;
    const footerText = (document.querySelector(".login-footer") || {}).textContent || "";
    const isOnline = /online/i.test(footerText);
    status.classList.toggle("online", isOnline);
    status.classList.toggle("offline", !isOnline);
    status.setAttribute("title", isOnline ? "Backend online" : "Backend offline");
    status.setAttribute("aria-label", isOnline ? "Backend online" : "Backend offline");
  }

  function pollBackendStatus() {
    const status = document.querySelector(".login-backend-status");
    if (!status || !originalFetch) return;
    originalFetch("/api/v1/health", { cache: "no-store" })
      .then(function (response) {
        const isOnline = response.ok;
        status.setAttribute("data-polled", "true");
        status.classList.toggle("online", isOnline);
        status.classList.toggle("offline", !isOnline);
        status.setAttribute("title", isOnline ? "Backend online" : "Backend offline");
        status.setAttribute("aria-label", isOnline ? "Backend online" : "Backend offline");
      })
      .catch(function () {
        status.setAttribute("data-polled", "true");
        status.classList.remove("online");
        status.classList.add("offline");
        status.setAttribute("title", "Backend offline");
        status.setAttribute("aria-label", "Backend offline");
      });
  }

  const observer = new MutationObserver(enhanceLogin);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  setInterval(updateBackendStatus, 1000);
  setInterval(pollBackendStatus, 5000);
  setTimeout(pollBackendStatus, 250);
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", enhanceLogin);
  } else {
    enhanceLogin();
  }
})();
