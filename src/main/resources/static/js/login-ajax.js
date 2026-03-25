(function () {
  function $(id) { return document.getElementById(id); }
  function show(el, on) {
    if (!el) return;
    el.classList.toggle("show", !!on);
  }
  function disable(el, v) { if (el) el.disabled = !!v; }
  function setText(el, txt) { if (el) el.textContent = txt; }

  const form = document.querySelector("form.form");
  if (!form) return;

  const i18nBox = $("cs-i18n");
  function t(key, fallback) {
    const v = i18nBox?.dataset ? i18nBox.dataset[key] : null;
    return (v != null && String(v).trim() !== "") ? v : (fallback || "");
  }

  function tf(key, fallback, params) {
    let msg = t(key, fallback);

    if (params && typeof msg === "string") {
      Object.entries(params).forEach(([k, v]) => {
        msg = msg.replaceAll(`{${k}}`, String(v ?? ""));
      });
    }

    return msg;
  }

  function pluralUnit(value, oneKey, otherKey, fallbackOne, fallbackOther) {
    return Number(value) === 1
      ? t(oneKey, fallbackOne)
      : t(otherKey, fallbackOther);
  }

  const usernameInput = $("username");
  const passwordInput = $("password");
  const submitBtn = form.querySelector('button[type="submit"]');

  const modal = $("cs-modal");
  const modalTitle = $("cs-modal-title");
  const modalSub = $("cs-modal-sub");
  const modalIcon = $("cs-modal-icon");
  const modalOk = $("cs-modal-ok");
  if (modalOk) modalOk.textContent = t("modalOk", modalOk.textContent || "OK");

  const lockedBox = $("login-locked");
  const expiredBox = $("login-expired");
  const errorBox = $("login-error");
  const remainingBox = $("login-remaining");

  const lockIcon = $("lock-icon");
  const countdownEl = $("countdown");

  const remainingVal = $("remainingAttemptsVal");
  const nextThresholdVal = $("nextThresholdVal");
  const riskProgress = $("risk-progress");
  const riskNextInfo = $("risk-next-info");

  const badgeRisk = $("lockout-risk-badge");
  const badgeRiskText = badgeRisk ? badgeRisk.querySelector(".badge-text") : null;

  const warnCfg = $("pwd-warn-at");
  const pwdCard = $("pwd-expiry-card");
  const pwdDays = $("pwd-days");

  const expiredSecsEl = $("expired-redirect-secs");
  const expiredBarFill = $("expired-bar-fill");

  const forgotLink = $("forgot-link");
  const forgotDisabled = $("forgot-disabled");

  function setForgotBlocked(blocked) {
    const isBlocked = !!blocked;

    if (forgotLink) forgotLink.style.display = isBlocked ? "none" : "";

    if (forgotDisabled) {
      forgotDisabled.style.display = isBlocked ? "" : "none";
      return;
    }

    if (isBlocked && forgotLink && forgotLink.parentElement) {
      const sp = document.createElement("span");
      sp.id = "forgot-disabled";
      sp.className = "small";
      sp.textContent = t(
        "forgotDisabled",
        "Esqueci minha senha (indisponível quando a senha está expirada)"
      );
      forgotLink.parentElement.appendChild(sp);
    }
  }

  function wantsJsonHeaders() {
    return { "Accept": "application/json", "X-Requested-With": "fetch" };
  }

  async function safeJson(res) {
    try { return await res.json(); } catch { return null; }
  }

  function closeModal() {
    if (!modal) return;
    modal.classList.remove("show");
    modal.setAttribute("aria-hidden", "true");

    disable(usernameInput, false);
    disable(passwordInput, false);
    disable(submitBtn, false);
  }

  function openModal(opts) {
    if (!modal) return;

    modalTitle.textContent = opts.title || t("modalTitle", "Aviso");
    modalSub.textContent = opts.message || "";
    modalIcon.textContent = opts.icon || "⚠";

    modal.classList.add("show");
    modal.setAttribute("aria-hidden", "false");

    disable(usernameInput, true);
    disable(passwordInput, true);
    disable(submitBtn, true);

    modalOk.onclick = () => {
      closeModal();
      window.location.assign(opts.redirectTo || "/");
    };

    if ((opts.redirectTo || "").indexOf("/password/expired") === 0) {
      setForgotBlocked(true);
    }
  }

  closeModal();

  function fmtMMSS(ms) {
    const s = Math.max(0, Math.floor(ms / 1000));
    const mm = String(Math.floor(s / 60)).padStart(2, "0");
    const ss = String(s % 60).padStart(2, "0");
    return `${mm}:${ss}`;
  }

  let countdownTimer = null;
  function startLockCountdown(blockedUntilEpochMs) {
    if (!countdownEl) return;

    const until = Number(blockedUntilEpochMs || 0);
    if (!Number.isFinite(until) || until <= Date.now()) {
      setText(countdownEl, "");
      return;
    }

    if (countdownTimer) clearInterval(countdownTimer);

    const tick = () => {
      const left = until - Date.now();
      if (left <= 0) {
        clearInterval(countdownTimer);
        countdownTimer = null;
        setText(countdownEl, "");
        show(lockedBox, false);
        disable(usernameInput, false);
        disable(passwordInput, false);
        disable(submitBtn, false);
        return;
      }
      setText(countdownEl, " • " + fmtMMSS(left));
    };

    tick();
    countdownTimer = setInterval(tick, 1000);
  }

  let expiredTimer = null;
  function startExpiredRedirectCountdown(totalMs, redirectTo) {
    if (expiredTimer) clearInterval(expiredTimer);

    if (expiredBarFill) expiredBarFill.style.width = "0%";
    if (expiredSecsEl) expiredSecsEl.textContent = String(Math.ceil(totalMs / 1000));

    const start = Date.now();
    const end = start + totalMs;

    const tick = () => {
      const now = Date.now();
      const left = Math.max(0, end - now);

      if (expiredSecsEl) {
        const secs = Math.ceil(left / 1000);
        expiredSecsEl.textContent = String(secs);
      }

      if (expiredBarFill) {
        const elapsed = Math.min(totalMs, now - start);
        const pct = Math.max(0, Math.min(100, (elapsed / totalMs) * 100));
        expiredBarFill.style.width = pct.toFixed(0) + "%";
      }

      if (left <= 0) {
        clearInterval(expiredTimer);
        expiredTimer = null;
        window.location.assign(redirectTo || "/password/expired");
      }
    };

    tick();
    expiredTimer = setInterval(tick, 250);
  }

  function applyRiskUI(state) {
    const failed = Number(state.failedAttempts || 0);
    const nextThreshold = Number(state.nextThreshold || 0);
    const remaining = Number(state.remainingAttempts || 0);
    const nextDur = Number(state.nextLockDurationSeconds || 0);

    const hasRisk = Number.isFinite(nextThreshold) && nextThreshold > 0
      && Number.isFinite(failed) && failed > 0;

    const showRiskBox = hasRisk
      && state.hasError === true
      && !state.locked
      && !state.expiredPassword;

    show(remainingBox, showRiskBox);

    if (!showRiskBox) {
      if (badgeRisk) badgeRisk.classList.remove("show", "pulse", "danger");
      if (badgeRiskText) badgeRiskText.textContent = "";
      if (riskNextInfo) riskNextInfo.textContent = "";
      return;
    }

    setText(remainingVal, String(Math.max(0, remaining)));
    setText(nextThresholdVal, String(nextThreshold));

    const ratio = Math.min(1, Math.max(0, failed / nextThreshold));
    if (riskProgress) {
      riskProgress.style.width = (ratio * 100).toFixed(0) + "%";

      if (ratio < 0.6) {
        riskProgress.style.background = "linear-gradient(90deg, #0284c7, #19c1f3)";
        riskProgress.style.boxShadow = "0 0 10px rgba(2,132,199,.35)";
      } else if (ratio < 0.85) {
        riskProgress.style.background = "linear-gradient(90deg, #f59e0b, #fb923c)";
        riskProgress.style.boxShadow = "0 0 10px rgba(245,158,11,.28)";
      } else {
        riskProgress.style.background = "linear-gradient(90deg, #ef4444, #fb7185)";
        riskProgress.style.boxShadow = "0 0 10px rgba(239,68,68,.25)";
      }
    }

    if (riskNextInfo) {
      if (Number.isFinite(nextDur) && nextDur > 0) {
        const mins = Math.round(nextDur / 60);
        const hrs = Math.round(mins / 60);

        const txt = mins < 60
          ? `${mins} ${pluralUnit(mins, "durationMinuteOne", "durationMinuteOther", "minuto", "minutos")}`
          : `${hrs} ${pluralUnit(hrs, "durationHourOne", "durationHourOther", "hora", "horas")}`;

        riskNextInfo.textContent = tf(
          "nextLockInfo",
          "Próximo bloqueio será de {duration}.",
          { duration: txt }
        );
      } else {
        riskNextInfo.textContent = "";
      }
    }

    const usedRatio = (Number.isFinite(failed) && Number.isFinite(nextThreshold) && nextThreshold > 0)
      ? (failed / nextThreshold)
      : 0;

    const highRisk = usedRatio >= 0.75;

    if (badgeRisk) {
      if (highRisk && hasRisk && !state.locked) {
        badgeRisk.classList.add("show");
        if (badgeRiskText) {
          badgeRiskText.textContent = t("highLockRisk", "⚠ Risco alto de bloqueio");
        }
        badgeRisk.classList.add("pulse");
        badgeRisk.classList.add("danger");
      } else {
        badgeRisk.classList.remove("show", "pulse", "danger");
        if (badgeRiskText) badgeRiskText.textContent = "";
      }
    }
  }

  function bootstrapFromServer() {
    const st = window.__CS_LOGIN_STATE__ || {};
    const locked = st.locked === true;
    const expired = st.expiredPassword === true;
    const hasError = st.hasError === true;

    if (badgeRisk) badgeRisk.classList.remove("show", "pulse", "danger");

    show(lockedBox, locked);
    if (locked && lockIcon) lockIcon.textContent = "🔒";
    if (locked) {
      disable(usernameInput, true);
      disable(passwordInput, true);
      disable(submitBtn, true);
      startLockCountdown(st.blockedUntilEpochMs);
    }

    show(expiredBox, expired && !locked);
    setForgotBlocked(expired && !locked);
    show(errorBox, hasError && !locked && !expired);

    applyRiskUI(st);

    if (expired && !locked) {
      disable(usernameInput, true);
      disable(passwordInput, true);
      disable(submitBtn, true);
      startExpiredRedirectCountdown(20000, "/password/expired");
    }
  }

  bootstrapFromServer();

  async function checkPasswordStatus(username, redirectAfter) {
    const u = (username || "").trim();
    if (!u) return false;

    const res = await fetch("/login/password/status", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        ...wantsJsonHeaders(),
        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
      },
      body: new URLSearchParams({ username: u })
    });

    if (!res.ok) return false;

    const data = await safeJson(res);
    if (!data) return false;

    const expired = data.expired === true;
    const days = Number(data.daysRemaining);

    let warnAt = 0;
    if (warnCfg && warnCfg.dataset && warnCfg.dataset.warnAt) {
      warnAt = Number(warnCfg.dataset.warnAt);
    }
    if (!Number.isFinite(warnAt) || warnAt < 0) warnAt = 0;

    if (expired) {
      openModal({
        icon: "🔒",
        title: t("passwordExpiredTitle", "Senha expirada"),
        message: t("passwordExpiredMessage", "Sua senha expirou. Clique em OK para atualizar."),
        redirectTo: "/password/expired"
      });
      return true;
    }

    if (Number.isFinite(days) && days >= 0 && warnAt > 0 && days <= warnAt) {
      if (pwdCard) pwdCard.classList.remove("d-none");
      if (pwdDays) pwdDays.textContent = String(days);

      openModal({
        icon: "⚠",
        title: t("passwordExpiringTitle", "Sua senha vai expirar"),
        message: tf(
          "passwordExpiringMessage",
          "Sua senha expira em {days} dia(s). Clique em OK para continuar.",
          { days }
        ),
        redirectTo: redirectAfter
      });
      return true;
    }

    return false;
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    closeModal();

    if (lockedBox && lockedBox.classList.contains("show")) return;
    if (expiredBox && expiredBox.classList.contains("show")) return;

    disable(submitBtn, true);

    const username = usernameInput ? usernameInput.value : "";

    try {
      const fd = new FormData(form);

      const res = await fetch(form.getAttribute("action") || "/login", {
        method: "POST",
        body: fd,
        credentials: "same-origin",
        headers: wantsJsonHeaders(),
        redirect: "manual"
      });

      const payload = await safeJson(res);

      if (res.ok && payload && payload.ok) {
        const redirectTo = payload.redirectTo || (window.__CS_LOGIN_STATE__?.defaultTarget || "/");

        const opened = await checkPasswordStatus(username, redirectTo);

        if (!opened) {
          window.location.assign(redirectTo);
        }

        return;
      }

      if (payload && payload.ok === false) {
        const st = {
          locked: payload.locked === true,
          hasError: payload.hasError === true,
          expiredPassword: payload.expiredPassword === true,
          failedAttempts: payload.failedAttempts ?? 0,
          remainingAttempts: payload.remainingAttempts ?? 0,
          nextThreshold: payload.nextThreshold ?? 0,
          nextLockDurationSeconds: payload.nextLockDurationSeconds ?? 0,
          blockedUntilEpochMs: payload.blockedUntilEpochMs ?? 0
        };

        window.__CS_LOGIN_STATE__ = { ...(window.__CS_LOGIN_STATE__ || {}), ...st };
        bootstrapFromServer();
      } else {
        window.__CS_LOGIN_STATE__ = { ...(window.__CS_LOGIN_STATE__ || {}), hasError: true };
        bootstrapFromServer();
      }

      disable(submitBtn, false);
    } catch (err) {
      console.error(err);
      disable(submitBtn, false);
    }
  });
})();