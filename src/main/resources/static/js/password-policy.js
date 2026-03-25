(function () {
  const form = document.getElementById("passwordForm");
  if (!form) return;

  const newPassword = document.getElementById("newPassword");
  const confirmPassword = document.getElementById("confirmPassword");
  const submitBtn = document.getElementById("submitBtn");
  const matchHint = document.getElementById("matchHint");

  const policyBox = document.getElementById("policyBox");
  const policyHint = document.getElementById("policyHint");
  const policyRules = document.getElementById("policyRules");

  const policyEndpoint = policyBox?.dataset?.policyEndpoint || "/api/password/policy";
  const checkEndpoint = policyBox?.dataset?.checkEndpoint || "/api/password/policy/check";

  // last rendered snapshot (code -> { state, label })
  let lastRulesByCode = new Map();
  let policyOk = false;

  function iconSvgForState(state) {
    // CSP-friendly: no inline scripts, only inline SVG markup.
    if (state === "OK") {
      return (
        '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">'
        + '<path d="M20 6 9 17l-5-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />'
        + '</svg>'
      );
    }
    if (state === "FAIL") {
      return (
        '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">'
        + '<path d="M18 6 6 18M6 6l12 12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />'
        + '</svg>'
      );
    }
    // PENDING
    return (
      '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false" class="spin">'
      + '<circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-dasharray="18 10" />'
      + '</svg>'
    );
  }

  function getUsernameHidden() {
    const el = form?.querySelector('input[name="username"], input#username');
    const v = el?.value || "";
    return v || null;
  }

  function setHint(text) {
    if (policyHint) policyHint.textContent = text || "";
  }

  function hidePolicyBox() {
    if (policyBox) policyBox.style.display = "none";
  }

  function clearRules() {
    if (policyRules) policyRules.innerHTML = "";
    lastRulesByCode = new Map();
  }

  function renderRules(rules) {
    clearRules();
    if (!policyRules) return;

    for (const r of rules || []) {
      if (!r?.code) continue;

      const row = document.createElement("div");
      row.className = "rule pending";
      row.dataset.code = r.code;

      const ic = document.createElement("span");
      ic.className = "rule-ic";
      ic.innerHTML = iconSvgForState("PENDING");

      const txt = document.createElement("span");
      txt.className = "txt";
      txt.textContent = r.label || r.code;

      row.appendChild(ic);
      row.appendChild(txt);
      policyRules.appendChild(row);

      lastRulesByCode.set(r.code, { state: "PENDING", label: txt.textContent });
    }
  }

  function applyRuleState(code, state, label) {
    const el = policyRules?.querySelector(`[data-code="${code}"]`);
    if (!el) return;

    const prev = lastRulesByCode.get(code);
    const prevState = prev?.state || "PENDING";

    el.classList.remove("ok", "bad", "pending");
    el.classList.add(state === "OK" ? "ok" : state === "FAIL" ? "bad" : "pending");

    // Icon
    const ic = el.querySelector(".rule-ic");
    if (ic) ic.innerHTML = iconSvgForState(state);

    if (label) {
      const txt = el.querySelector(".txt");
      if (txt) txt.textContent = label;
    }

    // Animate only when a rule transitions (pending->ok/fail, fail->ok, ok->fail)
    if (prevState !== state) {
      el.classList.remove("changed");
      // force reflow so the animation restarts
      // eslint-disable-next-line no-unused-expressions
      void el.offsetWidth;
      el.classList.add("changed");
      window.setTimeout(() => el.classList.remove("changed"), 260);
    }

    lastRulesByCode.set(code, { state, label: label || prev?.label || code });
  }

  function updateSubmit() {
    if (!submitBtn) return;
    submitBtn.disabled = !policyOk;
  }

  function updateMatchHintFromRules(rules) {
    if (!matchHint) return;
    const match = (rules || []).find(r => r?.code === "PASSWORD_NOT_MATCH");
    if (!match) {
      matchHint.textContent = "";
      return;
    }

    // evita poluição: só mostra quando o usuário mexer no confirm
    const conf = confirmPassword?.value || "";
    if (!conf) {
      matchHint.textContent = "";
      return;
    }

  }

  async function loadPolicy() {
    if (!policyBox) return;

    try {
      const res = await fetch(policyEndpoint, { method: "GET", headers: { "Accept": "application/json" } });

      if (res.status === 404) {
        hidePolicyBox();
        return;
      }
      if (!res.ok) {
        setHint("Não foi possível carregar a política agora.");
        return;
      }

      const data = await res.json();
      const rules = Array.isArray(data?.rules) ? data.rules : [];

      if (!rules.length) {
        setHint("Política não disponível.");
        return;
      }

      setHint("");
      renderRules(rules);
    } catch (e) {
      setHint("Não foi possível carregar a política agora.");
    }
  }

  async function checkPolicy() {
    policyOk = false;
    updateSubmit();

    const pwd = newPassword?.value || "";
    const conf = confirmPassword?.value || "";
    const username = getUsernameHidden();

    try {
      const csrf = window.CardSync?.csrf?.() || { header: "X-XSRF-TOKEN", token: "" };

      const res = await fetch(checkEndpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
          [csrf.header]: csrf.token
        },
        body: JSON.stringify({ password: pwd, confirmPassword: conf, username })
      });

      if (!res.ok) return;
      const data = await res.json();
      const rules = Array.isArray(data?.rules) ? data.rules : [];

      // aplica estados
      let allOk = true;
      for (const r of rules) {
        if (!r?.code) continue;
        applyRuleState(r.code, r.state, r.label);
        if (r.state !== "OK") allOk = false;
      }

      policyOk = !!data?.ok && allOk;
      updateMatchHintFromRules(rules);
    } catch (e) {
      // neutro
    } finally {
      updateSubmit();
    }
  }

  // listeners
  newPassword?.addEventListener("input", checkPolicy);
  confirmPassword?.addEventListener("input", checkPolicy);

  // init
  loadPolicy().finally(() => {
    checkPolicy();
  });
})();