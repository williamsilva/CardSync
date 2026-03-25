(function () {
  function getCookie(name) {
    const m = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
    return m ? decodeURIComponent(m[2]) : null;
  }

  // expõe helper global
  window.CardSync = window.CardSync || {};
  window.CardSync.csrf = function () {
    const header = document.querySelector('meta[name="_csrf_header"]')?.content || "X-XSRF-TOKEN";
    const token = getCookie("XSRF-TOKEN") || document.querySelector('meta[name="_csrf"]')?.content || "";
    return { header, token };
  };
})();
