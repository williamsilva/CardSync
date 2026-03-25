(function () {

  function resolveInput(btn) {
    // New pattern (preferred): data-toggle="password" + data-target="passwordFieldId"
    const targetId = btn.getAttribute("data-target");
    if (targetId) return document.getElementById(targetId);

    // Backward compat: data-toggle-password="#password"
    const legacy = btn.getAttribute("data-toggle-password");
    if (!legacy) return null;
    const id = legacy.startsWith('#') ? legacy.slice(1) : legacy;
    return document.getElementById(id);
  }

  function toggle(btn) {
    const input = resolveInput(btn);
    if (!input) return;

    const isHidden = input.type === "password";
    input.type = isHidden ? "text" : "password";

    btn.classList.toggle("active", isHidden);
    btn.setAttribute(
      "aria-label",
      isHidden ? "Ocultar senha" : "Mostrar senha"
    );
    btn.setAttribute("aria-pressed", isHidden ? "true" : "false");
  }

  document.addEventListener("click", function (e) {
    const btn = e.target.closest('[data-toggle="password"], [data-toggle-password]');
    if (!btn) return;

    e.preventDefault();
    toggle(btn);
  });

})();