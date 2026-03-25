(function () {
  // Preenche campos hidden de username a partir do cookie CS_LAST_USERNAME
  // (mesma convenção usada no login). Não usa script inline (CSP friendly).

  const input = document.getElementById("username");
  if (!input) return;

  // já veio do model
  if (input.value && input.value.trim()) return;

  function getCookie(name) {
    const all = document.cookie || "";
    const parts = all.split(";");
    for (const p of parts) {
      const s = p.trim();
      if (!s) continue;
      if (s.startsWith(name + "=")) {
        return s.substring((name + "=").length);
      }
    }
    return null;
  }

  const raw = getCookie("CS_LAST_USERNAME");
  if (!raw) return;

  try {
    input.value = decodeURIComponent(raw);
  } catch {
    input.value = raw;
  }
})();
