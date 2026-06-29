/* ===== 报名表单交互逻辑（从 student/form.html 内联 <script> 提取） =====
   依赖页面内联 boot 脚本注入的两个全局变量：
     - window.activityId  活动ID（字符串）
     - window.activityData 活动对象（含 status / deadline，由 Thymeleaf 序列化为 JSON）
   依赖全局.css 与全局 toast.js（showToast / showLoading） */

(function () {
  // ===== 截止时间前端拦截 =====
  (function () {
    var activity = window.activityData || null;
    var submitBtn = document.getElementById('submitBtn');
    if (!submitBtn || !activity) return;

    // 活动已截止（status=1）
    if (activity.status === 1) {
      submitBtn.disabled = true;
      submitBtn.textContent = '活动已截止';
      submitBtn.style.opacity = '0.5';
      submitBtn.style.cursor = 'not-allowed';
      return;
    }

    // 截止时间到达后禁用提交
    if (activity.deadline) {
      var deadline = new Date(activity.deadline);
      function checkDeadline() {
        if (new Date() > deadline) {
          submitBtn.disabled = true;
          submitBtn.textContent = '已过截止时间';
          submitBtn.style.opacity = '0.5';
          submitBtn.style.cursor = 'not-allowed';
        }
      }
      checkDeadline();
      // 每分钟检查一次
      setInterval(checkDeadline, 60000);
    }
  })();

  // ===== 表单提交拦截 =====
  (function () {
    var form = document.querySelector('form');
    if (form) {
      var submitting = false;
      form.addEventListener('submit', function (e) {
        if (submitting) {
          e.preventDefault();
          return;
        }
        // 提交前确认对话框，避免误提交（提交后不可修改）
        if (!confirm('提交后将无法修改，确认要提交吗？\n\n请仔细检查所有字段是否填写正确。')) {
          e.preventDefault();
          return;
        }
        submitting = true;
        var submitBtn = document.getElementById('submitBtn');
        if (submitBtn) {
          submitBtn.disabled = true;
        }
        if (typeof showLoading === 'function') {
          showLoading('正在生成申报文档，请稍候...');
        }
        // 提交前清除 localStorage 草稿
        try { localStorage.removeItem('skillbridge_draft_' + (window.activityId || '')); } catch (e) {}
        e.preventDefault();
        var f = this;
        setTimeout(function () { f.submit(); }, 350);
      });
    }
  })();

  // ===== 填写进度条 + localStorage 草稿自动保存 =====
  (function () {
    var form = document.querySelector('form');
    if (!form) return;

    var progressFill = document.getElementById('progressFill');
    var progressFilled = document.getElementById('progressFilledCount');
    var progressTotal = document.getElementById('progressTotalCount');
    var draftBanner = document.getElementById('draftRestoredBanner');
    var clearDraftBtn = document.getElementById('clearDraftBtn');

    // 收集所有需要统计的输入控件（排除按钮、hidden、file、disabled）
    function collectFields() {
      var fields = [];
      var els = form.querySelectorAll('input[type="text"], input[type="date"], input[type="radio"], select, textarea');
      els.forEach(function (el) {
        if (el.disabled || el.type === 'hidden' || el.type === 'file') return;
        fields.push(el);
      });
      return fields;
    }

    // 统计已填字段（radio 组按组算）
    function updateProgress() {
      var fields = collectFields();
      var radioGroups = {};
      var total = 0;
      var filled = 0;

      fields.forEach(function (el) {
        if (el.type === 'radio') {
          var name = el.name;
          if (!radioGroups[name]) {
            radioGroups[name] = true;
            total++;
            var checked = form.querySelector('input[type="radio"][name="' + name + '"]:checked');
            if (checked) filled++;
          }
        } else {
          total++;
          if (el.value && el.value.trim()) filled++;
        }
      });

      if (progressTotal) progressTotal.textContent = total;
      if (progressFilled) progressFilled.textContent = filled;
      if (progressFill) {
        var pct = total > 0 ? Math.round((filled / total) * 100) : 0;
        progressFill.style.width = pct + '%';
      }
    }

    // localStorage 草稿自动保存
    var activityId = document.querySelector('input[name="studentId"]');
    var storageKey = 'skillbridge_draft_' + (window.activityId || '');

    function saveDraftLocal() {
      try {
        var data = {};
        var els = form.querySelectorAll('input[type="text"], input[type="date"], input[type="radio"]:checked, select, textarea');
        els.forEach(function (el) {
          if (el.disabled || !el.name) return;
          if (el.type === 'radio') {
            data[el.name] = el.value;
          } else {
            data[el.name] = el.value;
          }
        });
        localStorage.setItem(storageKey, JSON.stringify(data));
      } catch (e) {
        // localStorage 不可用时静默失败
      }
    }

    // 恢复 localStorage 草稿（仅在无服务端草稿时恢复）
    function restoreDraftLocal() {
      if (draftBanner && draftBanner.style.display !== 'none') return; // 已有服务端草稿
      try {
        var raw = localStorage.getItem(storageKey);
        if (!raw) return;
        var data = JSON.parse(raw);
        var restored = false;
        Object.keys(data).forEach(function (name) {
          var el = form.querySelector('[name="' + name + '"]');
          if (!el || el.disabled) return;
          if (el.type === 'radio') {
            var radio = form.querySelector('input[type="radio"][name="' + name + '"][value="' + data[name] + '"]');
            if (radio && !radio.checked) { radio.checked = true; restored = true; }
          } else {
            if (!el.value && data[name]) { el.value = data[name]; restored = true; }
          }
        });
        if (restored && draftBanner) {
          draftBanner.style.display = 'flex';
          draftBanner.querySelector('span').textContent = '已为您恢复本地保存的草稿内容，请检查后继续提交。';
        }
      } catch (e) {}
    }

    // 清除草稿按钮
    if (clearDraftBtn) {
      clearDraftBtn.addEventListener('click', function () {
        form.reset();
        try { localStorage.removeItem(storageKey); } catch (e) {}
        if (draftBanner) draftBanner.style.display = 'none';
        updateProgress();
      });
    }

    // 显示服务端草稿恢复提示
    if (draftBanner && draftBanner.getAttribute('data-has-draft') !== null) {
      draftBanner.style.display = 'flex';
    }

    // 初始化
    restoreDraftLocal();
    updateProgress();

    // 监听输入变化
    form.addEventListener('input', updateProgress);
    form.addEventListener('change', updateProgress);

    // 每 30 秒自动保存到 localStorage
    setInterval(saveDraftLocal, 30000);
    // 页面隐藏时也保存
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) saveDraftLocal();
    });
  })();

  // ===== 证件照上传：预览 + 校验 + 拖拽 =====
  (function () {
    var photoBoxes = document.querySelectorAll('.photo-box');
    if (!photoBoxes.length) return;

    photoBoxes.forEach(function (box) {
      var input = box.querySelector('input[type="file"]');
      var preview = box.querySelector('.photo-preview');
      var maxSize = parseInt(input.getAttribute('data-max-size') || '2097152', 10);

      function showPreview(file) {
        var reader = new FileReader();
        reader.onload = function (e) {
          preview.src = e.target.result;
          box.classList.add('has-image');
        };
        reader.readAsDataURL(file);
      }

      function clearPreview() {
        preview.src = '';
        box.classList.remove('has-image');
        input.value = '';
      }

      function showError(msg) {
        if (typeof showToast === 'function') {
          showToast(msg, 'error');
        } else {
          alert(msg);
        }
      }

      // 文件选择处理
      input.addEventListener('change', function () {
        var file = this.files[0];
        if (!file) return;
        if (!file.type.startsWith('image/')) {
          showError('请上传图片文件（JPG / PNG / GIF）。');
          clearPreview();
          return;
        }
        if (file.size > maxSize) {
          showError('图片大小不能超过 2MB，当前文件大小：' + (file.size / 1024 / 1024).toFixed(2) + 'MB。');
          clearPreview();
          return;
        }
        showPreview(file);
      });

      // 拖拽支持
      box.addEventListener('dragover', function (e) {
        e.preventDefault();
        box.classList.add('drag-over');
      });
      box.addEventListener('dragleave', function () {
        box.classList.remove('drag-over');
      });
      box.addEventListener('drop', function (e) {
        e.preventDefault();
        box.classList.remove('drag-over');
        var file = e.dataTransfer.files[0];
        if (!file) return;
        if (!file.type.startsWith('image/')) {
          showError('请上传图片文件（JPG / PNG / GIF）。');
          return;
        }
        if (file.size > maxSize) {
          showError('图片大小不能超过 2MB，当前文件大小：' + (file.size / 1024 / 1024).toFixed(2) + 'MB。');
          return;
        }
        var dt = new DataTransfer();
        dt.items.add(file);
        input.files = dt.files;
        showPreview(file);
      });
    });
  })();

  // ===== 工作经历：动态增删行 + name 索引重排 =====
  (function () {
    var tbody = document.getElementById('workExpBody');
    var btnAdd = document.getElementById('btnAddWorkExp');
    if (!tbody || !btnAdd) return;

    // 新行模板：仅挂 data-field，name 由 reindex() 统一刷新
    function createRow() {
      var tr = document.createElement('tr');
      tr.className = 'exp-row';
      tr.innerHTML =
        '<td><span class="exp-index">1</span></td>' +
        '<td><div class="exp-date-cell">' +
          '<input type="date" data-field="startDate" class="input-morandi"/>' +
          '<span class="exp-dash">-</span>' +
          '<input type="date" data-field="endDate" class="input-morandi"/>' +
        '</div></td>' +
        '<td><input type="text" data-field="companyName" class="input-morandi" placeholder="工作单位"/></td>' +
        '<td><input type="text" data-field="position" class="input-morandi" placeholder="职务"/></td>' +
        '<td class="exp-action-cell"><button type="button" class="btn-del-exp" title="删除该行">✕</button></td>';
      return tr;
    }

    // 核心：重排所有行的 name 索引，确保从 0 起且连续，兼容后端 List 绑定
    function reindex() {
      var rows = tbody.querySelectorAll('tr.exp-row');
      rows.forEach(function (row, i) {
        row.querySelector('.exp-index').textContent = i + 1;
        row.querySelectorAll('[data-field]').forEach(function (input) {
          input.name = 'workExp[' + i + '].' + input.getAttribute('data-field');
        });
      });
    }

    function addRow() {
      var row = createRow();
      tbody.appendChild(row);
      reindex();
    }

    btnAdd.addEventListener('click', addRow);

    // 事件委托：删除当前行
    tbody.addEventListener('click', function (e) {
      var btn = e.target.closest('.btn-del-exp');
      if (!btn) return;
      var row = btn.closest('tr.exp-row');
      if (!row) return;
      // 至少保留 1 行：仅清空值，不删除
      if (tbody.querySelectorAll('tr.exp-row').length <= 1) {
        row.querySelectorAll('input').forEach(function (input) { input.value = ''; });
        return;
      }
      row.classList.add('exp-row-removing');
      setTimeout(function () {
        row.remove();
        reindex();
      }, 200);
    });
  })();
})();
