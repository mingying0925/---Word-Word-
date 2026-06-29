(function () {
  'use strict';

  /* ============================================================
   * SkillBridge Modal 组件
   * 替代原生 window.confirm / window.alert，提供玻璃拟态风格弹窗。
   *
   * API:
   *   window.showConfirm({ title, message, confirmText, cancelText, danger })
   *     → Promise<boolean>  true=确认, false=取消
   *   window.showAlert({ title, message, okText })
   *     → Promise<void>
   * ============================================================ */

  var root = document.querySelector('.modal-root');
  if (!root) {
    root = document.createElement('div');
    root.className = 'modal-root';
    root.setAttribute('aria-live', 'polite');
    document.body.appendChild(root);
  }

  // 焦点管理：记录触发弹窗前的焦点元素，关闭后还原
  var lastFocused = null;

  function trapFocus(modal) {
    var focusable = modal.querySelectorAll(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable.length) return null;
    var first = focusable[0];
    var last = focusable[focusable.length - 1];
    first.focus();
    return function (e) {
      if (e.key !== 'Tab') return;
      if (e.shiftKey) {
        if (document.activeElement === first) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
  }

  function openModal(opts) {
    return new Promise(function (resolve) {
      // 同时只允许一个 modal
      if (root.querySelector('.modal-overlay')) {
        resolve(false);
        return;
      }

      lastFocused = document.activeElement;

      var overlay = document.createElement('div');
      overlay.className = 'modal-overlay' + (opts.danger ? ' modal-overlay--danger' : '');
      overlay.setAttribute('role', 'dialog');
      overlay.setAttribute('aria-modal', 'true');
      if (opts.title) overlay.setAttribute('aria-labelledby', 'modal-title');

      var card = document.createElement('div');
      card.className = 'modal-card glass-card';

      var titleEl = document.createElement('div');
      titleEl.className = 'modal-title';
      titleEl.id = 'modal-title';
      titleEl.textContent = opts.title || '';
      if (!opts.title) titleEl.style.display = 'none';
      card.appendChild(titleEl);

      var msgEl = document.createElement('div');
      msgEl.className = 'modal-message';
      msgEl.textContent = opts.message || '';
      card.appendChild(msgEl);

      var actions = document.createElement('div');
      actions.className = 'modal-actions';

      function close(result) {
        overlay.classList.remove('show');
        document.removeEventListener('keydown', keyHandler);
        setTimeout(function () {
          if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
          if (lastFocused && typeof lastFocused.focus === 'function') {
            lastFocused.focus();
          }
          resolve(result);
        }, 200);
      }

      // 取消按钮（confirm 模式）
      if (opts.type === 'confirm') {
        var cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.className = 'modal-btn modal-btn--cancel';
        cancelBtn.textContent = opts.cancelText || '取消';
        cancelBtn.addEventListener('click', function () { close(false); });
        actions.appendChild(cancelBtn);
      }

      var okBtn = document.createElement('button');
      okBtn.type = 'button';
      okBtn.className = 'modal-btn modal-btn--ok' + (opts.danger ? ' modal-btn--danger' : '');
      okBtn.textContent = opts.okText || opts.confirmText || '确定';
      okBtn.addEventListener('click', function () { close(true); });
      actions.appendChild(okBtn);

      card.appendChild(actions);
      overlay.appendChild(card);

      // 点击遮罩取消（仅 confirm）
      if (opts.type === 'confirm') {
        overlay.addEventListener('click', function (e) {
          if (e.target === overlay) close(false);
        });
      }

      // ESC 关闭
      function keyHandler(e) {
        if (e.key === 'Escape') {
          e.preventDefault();
          close(opts.type === 'confirm' ? false : true);
        }
      }
      document.addEventListener('keydown', keyHandler);

      root.appendChild(overlay);
      requestAnimationFrame(function () {
        overlay.classList.add('show');
      });

      // 焦点陷阱
      var trap = trapFocus(card);
      if (trap) {
        card.addEventListener('keydown', trap);
      }
    });
  }

  window.showConfirm = function (opts) {
    if (typeof opts === 'string') opts = { message: opts };
    opts.type = 'confirm';
    return openModal(opts);
  };

  window.showAlert = function (opts) {
    if (typeof opts === 'string') opts = { message: opts };
    opts.type = 'alert';
    return openModal(opts);
  };

  /* ============================================================
   * 危险操作确认：替代 onclick="return confirm(...)"
   * 用法：
   *   <button data-confirm="确定要删除吗？" data-confirm-danger>删除</button>
   *   <form data-confirm="确定要清空名单吗？">...</form>
   * ============================================================ */
  function bindDataConfirm() {
    document.addEventListener('click', function (e) {
      var target = e.target.closest('[data-confirm]');
      if (!target) return;
      // 已经过确认的标记，放行
      if (target.__confirmed) {
        target.__confirmed = false;
        return;
      }
      e.preventDefault();
      e.stopImmediatePropagation();
      var message = target.getAttribute('data-confirm');
      var danger = target.hasAttribute('data-confirm-danger');
      var title = target.getAttribute('data-confirm-title') || '';
      showConfirm({
        title: title,
        message: message,
        danger: danger,
        confirmText: target.getAttribute('data-confirm-ok') || '确定',
        cancelText: target.getAttribute('data-confirm-cancel') || '取消'
      }).then(function (ok) {
        if (!ok) return;
        // 还原原始操作
        target.__confirmed = true;
        // 触发原生点击 / 提交
        if (target.tagName === 'FORM') {
          target.submit();
        } else {
          target.click();
        }
      });
    }, true);

    // 表单的 data-confirm 在 submit 时拦截
    document.addEventListener('submit', function (e) {
      var form = e.target;
      if (!form.hasAttribute('data-confirm')) return;
      if (form.__confirmed) {
        form.__confirmed = false;
        return;
      }
      e.preventDefault();
      var message = form.getAttribute('data-confirm');
      var danger = form.hasAttribute('data-confirm-danger');
      showConfirm({
        message: message,
        danger: danger,
        confirmText: form.getAttribute('data-confirm-ok') || '确定',
        cancelText: form.getAttribute('data-confirm-cancel') || '取消'
      }).then(function (ok) {
        if (!ok) return;
        form.__confirmed = true;
        form.submit();
      });
    }, true);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindDataConfirm);
  } else {
    bindDataConfirm();
  }

})();
