(function () {
  if (window.__kalcMenuImportLoaded) return;
  window.__kalcMenuImportLoaded = true;

  function token() {
    try {
      return localStorage.getItem("kalc_auth_token") ||
        localStorage.getItem("kalc_pos_token") ||
        localStorage.getItem("authToken") ||
        "";
    } catch (_) {
      return "";
    }
  }

  function findTokenFromStorage() {
    var direct = token();
    if (direct) return direct;
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var key = localStorage.key(i);
        var value = localStorage.getItem(key) || "";
        if (/^[a-f0-9]{64}$/i.test(value)) return value;
        if (value.indexOf('"token"') !== -1) {
          try {
            var parsed = JSON.parse(value);
            if (parsed && parsed.token) return parsed.token;
            if (parsed && parsed.auth && parsed.auth.token) return parsed.auth.token;
          } catch (_) {}
        }
      }
    } catch (_) {}
    return "";
  }

  function notice(message) {
    window.alert(message);
  }

  function downloadTemplate() {
    var csv = "Item,Category,Selling Price\nSoda 300ML,Soft Drinks,100\nTusker Lager,Beers,250\nChicken Stew,Mains,450\n";
    var blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    var url = URL.createObjectURL(blob);
    var link = document.createElement("a");
    link.href = url;
    link.download = "kalc-menu-import-template.csv";
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  }

  function findMenuHost() {
    var headings = Array.prototype.slice.call(document.querySelectorAll("h2,h3,b,div"));
    for (var i = 0; i < headings.length; i++) {
      var text = (headings[i].textContent || "").trim();
      if (/menu items|item name|categories|save item/i.test(text)) {
        return headings[i].closest(".waiter-panel, .inventory-panel, [style]") || headings[i].parentElement;
      }
    }
    return null;
  }

  function canManageMenuImports() {
    var text = document.body ? document.body.textContent || "" : "";
    if (/Role:\s*(admin|manager)\b/i.test(text)) return true;
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var value = localStorage.getItem(localStorage.key(i)) || "";
        if (/"role"\s*:\s*"(admin|manager)"/i.test(value)) return true;
      }
    } catch (_) {}
    return false;
  }

  function installButton() {
    var existing = document.querySelector(".kalc-menu-import-bar");
    if (!canManageMenuImports()) {
      if (existing) existing.remove();
      return;
    }
    if (existing) return;
    var host = findMenuHost();
    if (!host) return;

    var bar = document.createElement("div");
    bar.className = "kalc-menu-import-bar";

    var button = document.createElement("button");
    button.type = "button";
    button.className = "kalc-menu-import-button";
    button.textContent = "Import Menu CSV";
    button.title = "Import menu items from a CSV spreadsheet";

    var template = document.createElement("button");
    template.type = "button";
    template.className = "kalc-menu-template-button";
    template.textContent = "Import Template";
    template.title = "Download CSV import template";
    template.addEventListener("click", downloadTemplate);

    var input = document.createElement("input");
    input.type = "file";
    input.accept = ".csv,text/csv";
    input.className = "kalc-menu-import-input";

    button.addEventListener("click", function () {
      input.value = "";
      input.click();
    });

    input.addEventListener("change", function () {
      var file = input.files && input.files[0];
      if (!file) return;
      var tab = window.prompt("Import to which menu? Type kitchen or bar.", "bar");
      if (tab == null) return;
      tab = /^k/i.test(tab) ? "kitchen" : "bar";

      var auth = findTokenFromStorage();
      if (!auth) {
        notice("Please log in again before importing menu items.");
        return;
      }

      button.disabled = true;
      button.textContent = "Importing...";
      var form = new FormData();
      form.append("file", file);
      fetch("/api/v1/products/import?tab=" + encodeURIComponent(tab), {
        method: "POST",
        headers: { Authorization: "Bearer " + auth },
        body: form
      }).then(function (response) {
        return response.text().then(function (text) {
          var data = {};
          try { data = JSON.parse(text); } catch (_) {}
          if (!response.ok) throw new Error(data.message || text || "Import failed");
          return data;
        });
      }).then(function (data) {
        notice("Menu import complete.\nImported: " + (data.imported || 0) +
          "\nUpdated: " + (data.updated || 0) +
          "\nSkipped: " + (data.skipped || 0));
        window.location.reload();
      }).catch(function (error) {
        notice("Menu import failed: " + (error && error.message ? error.message : "Unknown error"));
      }).finally(function () {
        button.disabled = false;
        button.textContent = "Import Menu CSV";
      });
    });

    bar.appendChild(button);
    bar.appendChild(template);
    host.insertBefore(bar, host.firstChild);
    document.body.appendChild(input);
  }

  var observer = new MutationObserver(installButton);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", installButton);
  } else {
    installButton();
  }
})();
