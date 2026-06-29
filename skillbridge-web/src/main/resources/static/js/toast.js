(function () {
  'use strict';

  /* ===== Loading Overlay ===== */
  var overlay = document.querySelector('.loading-overlay');

  window.showLoading = function (text) {
    if (!overlay) return;
    var el = overlay.querySelector('.loading-text');
    if (el && text) el.textContent = text;
    overlay.classList.add('active');
  };

  window.hideLoading = function () {
    if (!overlay) return;
    overlay.classList.remove('active');
  };

  /* ===== Toast ===== */
  var container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  window.showToast = function (message, type) {
    type = type || 'success';

    var el = document.createElement('div');
    el.className = 'toast toast--' + type;

    var icon = document.createElement('span');
    icon.className = 'toast__icon';
    if (type === 'success') {
      icon.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="#8ABA9A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>';
    } else {
      icon.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="#D4A0A0" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
    }
    el.appendChild(icon);

    var msg = document.createElement('span');
    msg.textContent = message;
    el.appendChild(msg);

    var close = document.createElement('button');
    close.className = 'toast__close';
    close.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';
    (function (t) { close.addEventListener('click', function (e) { e.stopPropagation(); dismiss(t); }); })(el);
    el.appendChild(close);

    container.appendChild(el);

    requestAnimationFrame(function () {
      el.classList.add('show');
    });

    var timer = setTimeout(function () { dismiss(el); }, 4000);

    function dismiss(target) {
      clearTimeout(timer);
      target.classList.remove('show');
      target.addEventListener('transitionend', function () {
        if (target.parentNode) target.parentNode.removeChild(target);
      });
    }

    el.addEventListener('click', function () {
      if (el.classList.contains('show')) dismiss(el);
    });
  };

})();
