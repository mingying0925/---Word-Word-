(function () {
  'use strict';

  /* ============================================================
   * SkillBridge 会话超时预警
   *
   * 工作原理：
   * 1. 从 <meta name="session-expires-at"> 读取 JWT 过期时间戳（毫秒）。
   * 2. 在过期前 5 分钟弹出预警，提示用户即将登出。
   * 3. 用户可选择"继续工作"（刷新页面重新获取 Token）或"立即登出"。
   * 4. 过期后自动跳转登录页（带 ?error=timeout）。
   *
   * 注意：本组件仅在已登录页面（包含 session-expires-at meta 标签）生效。
   * ============================================================ */

  var meta = document.querySelector('meta[name="session-expires-at"]');
  if (!meta) return;

  var expiresAt = parseInt(meta.getAttribute('content'), 10);
  if (!expiresAt || isNaN(expiresAt)) return;

  var WARNING_BEFORE_MS = 5 * 60 * 1000; // 过期前 5 分钟预警
  var CHECK_INTERVAL_MS = 30 * 1000;      // 每 30 秒检查一次

  var warned = false;

  function timeRemaining() {
    return expiresAt - Date.now();
  }

  function redirectToLogin() {
    var uri = window.location.pathname;
    var target = uri.indexOf('/teacher/') === 0 ? '/teacher/login' : '/student/login';
    window.location.href = target + '?error=timeout';
  }

  function showTimeoutWarning() {
    if (warned) return;
    warned = true;
    if (typeof showConfirm !== 'function') {
      // modal.js 未加载，直接提示
      alert('您的会话即将过期，请保存当前工作后刷新页面。');
      return;
    }
    showConfirm({
      title: '会话即将过期',
      message: '您的登录会话将在 5 分钟后过期。是否刷新页面以继续工作？未保存的内容可能丢失。',
      confirmText: '刷新继续',
      cancelText: '稍后处理',
      danger: false
    }).then(function (ok) {
      if (ok) {
        // 刷新页面以重新获取 Token（Cookie 仍有效时刷新会续期）
        window.location.reload();
      }
    });
  }

  function check() {
    var remaining = timeRemaining();
    if (remaining <= 0) {
      redirectToLogin();
      return;
    }
    if (remaining <= WARNING_BEFORE_MS) {
      showTimeoutWarning();
    }
  }

  // 初始检查 + 定时检查
  check();
  setInterval(check, CHECK_INTERVAL_MS);

})();
