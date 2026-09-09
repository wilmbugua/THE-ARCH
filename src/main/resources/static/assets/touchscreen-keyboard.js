(function () {
  if (window.__kalcTouchKeyboardLoaded) return;
  window.__kalcTouchKeyboardLoaded = true;

  var activeInput = null;
  var keyboard = null;
  var keys = null;
  var title = null;
  var spacer = null;
  var shifted = false;

  var textRows = [
    "1234567890",
    "QWERTYUIOP",
    "ASDFGHJKL",
    "ZXCVBNM"
  ];
  var numericKeys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "Back", "-", "/", ":"];

  function build() {
    if (keyboard) return;
    keyboard = document.createElement("div");
    keyboard.className = "kalc-touch-keyboard";
    keyboard.hidden = true;
    keyboard.innerHTML =
      '<div class="kalc-touch-keyboard-head">' +
        '<div class="kalc-touch-keyboard-title"></div>' +
        '<button type="button" class="kalc-touch-key action" data-key="done">Done</button>' +
      '</div>' +
      '<div class="kalc-touch-keyboard-keys"></div>';
    document.body.appendChild(keyboard);
    title = keyboard.querySelector(".kalc-touch-keyboard-title");
    keys = keyboard.querySelector(".kalc-touch-keyboard-keys");

    spacer = document.createElement("div");
    spacer.className = "kalc-touch-spacer";
    spacer.hidden = true;
    document.body.appendChild(spacer);

    keyboard.addEventListener("pointerdown", function (event) {
      event.preventDefault();
    });
    keyboard.addEventListener("click", function (event) {
      var button = event.target.closest("[data-key]");
      if (!button) return;
      press(button.getAttribute("data-key"));
    });
  }

  function shouldUseKeyboard(input) {
    if (!input || input.disabled || input.readOnly) return false;
    if (!input.matches("input, textarea")) return false;
    // Allow keyboard for mpesa reference/time fields even if they have pin-input class
    var isMpesaField = isMpesaPaymentField(input);
    if (!isMpesaField && hasPairedAppKeypad(input)) return false;
    if (input.classList.contains("pin-input") && !isMpesaField) return false;
    var type = (input.getAttribute("type") || "text").toLowerCase();
    return ["text", "search", "tel", "number", "password", "email", "date", "time", "textarea"].indexOf(type) !== -1;
  }

  function hasPairedAppKeypad(input) {
    var parent = input.parentElement;
    if (!parent) return false;
    return !!parent.querySelector('button[aria-label^="Open "][aria-label$=" keyboard"]');
  }

  function isMpesaPaymentField(input) {
    var id = (input.id || "").toLowerCase();
    var name = (input.name || "").toLowerCase();
    var placeholder = (input.placeholder || "").toLowerCase();
    var label = "";
    var labelNode = input.closest("label");
    if (labelNode) label = (labelNode.textContent || "").toLowerCase();
    return id.includes("mpesa") || id.includes("paynet") ||
      name.includes("mpesa") || name.includes("paynet") ||
      placeholder.includes("m-pesa") || placeholder.includes("mpesa") ||
      placeholder.includes("02:30") || label.includes("m-pesa") ||
      label.includes("payment time");
  }

  function modeFor(input) {
    var type = (input.getAttribute("type") || "").toLowerCase();
    var inputMode = (input.getAttribute("inputmode") || "").toLowerCase();
    var id = (input.id || "").toLowerCase();
    // Use numeric mode for time fields and paynet time
    if (["number", "tel", "date", "time"].indexOf(type) !== -1 || ["numeric", "decimal", "tel"].indexOf(inputMode) !== -1 || id.includes("time")) {
      return "numeric";
    }
    return "text";
  }

  function labelFor(input) {
    var label = "";
    if (input.id) {
      var explicit = document.querySelector('label[for="' + CSS.escape(input.id) + '"]');
      if (explicit) label = explicit.textContent || "";
    }
    if (!label) {
      var parent = input.closest("label");
      if (parent) label = parent.textContent || "";
    }
    return (label || input.placeholder || input.name || "Entry").trim().replace(/\s+/g, " ").slice(0, 80);
  }

  function show(input) {
    if (!shouldUseKeyboard(input)) return;
    build();
    activeInput = input;
    keyboard.hidden = false;
    spacer.hidden = false;
    title.textContent = labelFor(input);
    render(modeFor(input));
    setTimeout(function () {
      try { input.scrollIntoView({ block: "center", behavior: "smooth" }); } catch (_) {}
    }, 50);
  }

  function hide() {
    if (!keyboard) return;
    keyboard.hidden = true;
    spacer.hidden = true;
    activeInput = null;
  }

  function render(mode) {
    keyboard.classList.toggle("numeric", mode === "numeric");
    keys.innerHTML = "";
    if (mode === "numeric") {
      numericKeys.forEach(addKey);
      addKey("Clear", "danger wide");
      addKey("Done", "action wide");
      return;
    }
    textRows.forEach(function (row) {
      row.split("").forEach(function (key) {
        addKey(shifted ? key : key.toLowerCase());
      });
    });
    addKey("Shift", "action wide");
    addKey("Space", "space");
    addKey("Back", "danger wide");
    addKey("Clear", "danger wide");
    addKey("Done", "action wide");
  }

  function addKey(key, extraClass) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "kalc-touch-key" + (extraClass ? " " + extraClass : "");
    button.setAttribute("data-key", key.toLowerCase());
    button.textContent = key;
    keys.appendChild(button);
  }

  function setValue(value) {
    if (!activeInput) return;
    activeInput.value = value;
    activeInput.dispatchEvent(new Event("input", { bubbles: true }));
    activeInput.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function insert(text) {
    var input = activeInput;
    if (!input) return;
    var start = input.selectionStart == null ? input.value.length : input.selectionStart;
    var end = input.selectionEnd == null ? input.value.length : input.selectionEnd;
    setValue(input.value.slice(0, start) + text + input.value.slice(end));
    var next = start + text.length;
    try { input.setSelectionRange(next, next); } catch (_) {}
    input.focus();
  }

  function backspace() {
    var input = activeInput;
    if (!input) return;
    var start = input.selectionStart == null ? input.value.length : input.selectionStart;
    var end = input.selectionEnd == null ? input.value.length : input.selectionEnd;
    if (start === end && start > 0) start -= 1;
    setValue(input.value.slice(0, start) + input.value.slice(end));
    try { input.setSelectionRange(start, start); } catch (_) {}
    input.focus();
  }

  function press(key) {
    if (!activeInput && key !== "done") return;
    if (key === "done") {
      if (activeInput) activeInput.blur();
      hide();
      return;
    }
    if (key === "back") return backspace();
    if (key === "clear") return setValue("");
    if (key === "space") return insert(" ");
    if (key === "shift") {
      shifted = !shifted;
      render("text");
      return;
    }
    insert(key);
  }

  document.addEventListener("focusin", function (event) {
    if (shouldUseKeyboard(event.target)) show(event.target);
  });

  document.addEventListener("pointerdown", function (event) {
    if (!keyboard || keyboard.hidden) return;
    if (event.target.closest(".kalc-touch-keyboard")) return;
    if (event.target.matches("input, textarea")) return;
    hide();
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") hide();
  });
})();
