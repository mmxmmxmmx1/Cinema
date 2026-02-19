(function () {
  'use strict';

  function initLockCountdown() {
    const container = document.querySelector('.error[data-lock]');
    if (!container) {
      return;
    }
    let seconds = parseInt(container.getAttribute('data-lock'), 10);
    if (Number.isNaN(seconds)) {
      return;
    }
    const valueEl = container.querySelector('.countdown-value');
    if (!valueEl) {
      return;
    }

    function format(sec) {
      const m = String(Math.floor(sec / 60)).padStart(2, '0');
      const s = String(sec % 60).padStart(2, '0');
      return m + ':' + s;
    }

    let intervalId;
    function tick() {
      if (seconds <= 0) {
        valueEl.textContent = '00:00';
        if (intervalId) {
          clearInterval(intervalId);
        }
        setTimeout(function () {
          window.location.reload();
        }, 1000);
        return;
      }
      valueEl.textContent = format(seconds);
      seconds -= 1;
    }

    tick();
    intervalId = setInterval(tick, 1000);
  }

  function initAutoLogout(formId, heartbeatUrl) {
    const logoutForm = document.getElementById(formId);
    if (!logoutForm) {
      return;
    }

    const INACTIVITY_LIMIT = 10 * 60 * 1000;
    const HEARTBEAT_INTERVAL = 60 * 1000;
    const events = ['click', 'mousemove', 'keydown', 'scroll', 'touchstart', 'touchmove'];
    let triggered = false;
    let lastHeartbeat = Date.now();
    let timer = setTimeout(triggerLogout, INACTIVITY_LIMIT);

    function resetTimer() {
      if (triggered) {
        return;
      }
      clearTimeout(timer);
      timer = setTimeout(triggerLogout, INACTIVITY_LIMIT);
      sendHeartbeat();
    }

    function triggerLogout() {
      if (triggered) {
        return;
      }
      triggered = true;
      logoutForm.submit();
    }

    function sendHeartbeat() {
      const now = Date.now();
      if (now - lastHeartbeat < HEARTBEAT_INTERVAL) {
        return;
      }
      lastHeartbeat = now;

      if (navigator.sendBeacon) {
        try {
          navigator.sendBeacon(heartbeatUrl, new Blob([], { type: 'text/plain' }));
          return;
        } catch (_) {
          // ignore and fallback to fetch
        }
      }
      fetch(heartbeatUrl, { method: 'POST', keepalive: true }).catch(function () {
      });
    }

    events.forEach(function (evt) {
      document.addEventListener(evt, resetTimer, { passive: true });
    });

    sendHeartbeat();
  }

  function initChecklistBulkToggle() {
    const checkAllBtn = document.getElementById('check-all-btn');
    const uncheckAllBtn = document.getElementById('uncheck-all-btn');
    if (!checkAllBtn && !uncheckAllBtn) {
      return;
    }

    function setAll(value) {
      const checkboxes = document.querySelectorAll('.check-item');
      checkboxes.forEach(function (cb) {
        cb.checked = value;
      });
    }

    if (checkAllBtn) {
      checkAllBtn.addEventListener('click', function () {
        setAll(true);
      });
    }
    if (uncheckAllBtn) {
      uncheckAllBtn.addEventListener('click', function () {
        setAll(false);
      });
    }
  }

  function initEmployeeTodoEditor() {
    const section = document.getElementById('todo-section');
    if (!section) {
      return;
    }

    const viewMode = section.querySelector('#todo-view');
    const editMode = section.querySelector('#todo-edit');
    const editBtn = section.querySelector('#edit-todo-btn');
    const saveBtn = section.querySelector('#save-todo-btn');
    const cancelBtn = section.querySelector('#cancel-todo-btn');
    const textarea = editMode ? editMode.querySelector('textarea') : null;
    const ul = viewMode ? viewMode.querySelector('ul') : null;

    if (!viewMode || !editMode || !editBtn || !saveBtn || !cancelBtn || !textarea || !ul) {
      return;
    }

    editBtn.addEventListener('click', function () {
      const items = Array.from(ul.querySelectorAll('li')).map(function (li) {
        return li.textContent.trim();
      });
      textarea.value = items.join('\n');
      viewMode.style.display = 'none';
      editMode.style.display = 'block';
      editBtn.style.display = 'none';
      textarea.focus();
    });

    cancelBtn.addEventListener('click', function () {
      viewMode.style.display = 'block';
      editMode.style.display = 'none';
      editBtn.style.display = 'inline-block';
    });

    saveBtn.addEventListener('click', async function () {
      const lines = textarea.value.split('\n').filter(function (line) {
        return line.trim();
      });
      const normalized = lines.map(function (line) {
        return line.trim();
      }).filter(function (line) {
        return !!line;
      });
      const csrf = document.getElementById('employee-csrf-token');
      const token = csrf ? csrf.value : '';

      try {
        const response = await fetch('/employee/todos', {
          method: 'POST',
          credentials: 'same-origin',
          headers: Object.assign(
            { 'Content-Type': 'application/json' },
            token ? { 'X-CSRF-TOKEN': token } : {}
          ),
          body: JSON.stringify({ items: normalized })
        });
        if (!response.ok) {
          throw new Error('儲存失敗');
        }

        const applied = normalized.length > 0
          ? normalized
          : ['08:30 — 影廳巡檢與音響測試', '12:30 — 售票櫃台交接', '16:45 — 小賣部盤點'];

        ul.innerHTML = '';
        applied.forEach(function (line) {
          const li = document.createElement('li');
          li.textContent = line;
          ul.appendChild(li);
        });
        viewMode.style.display = 'block';
        editMode.style.display = 'none';
        editBtn.style.display = 'inline-block';
      } catch (_) {
        window.alert('儲存今日待辦失敗，請稍後再試。');
      }
    });
  }

  function initAdminShowtimesAutoSubmit() {
    const select = document.querySelector('#movieId[data-auto-submit="true"]');
    if (!select) {
      return;
    }
    select.addEventListener('change', function () {
      if (select.form) {
        select.form.submit();
      }
    });
  }

  function boot() {
    initLockCountdown();
    initAutoLogout('member-auto-logout', '/member/activity');
    initAutoLogout('employee-auto-logout', '/employee/activity');
    initAutoLogout('manager-auto-logout', '/employee/manager/activity');
    initAutoLogout('it-auto-logout', '/employee/it/activity');
    initAutoLogout('admin-auto-logout', '/employee/admin/activity');
    initChecklistBulkToggle();
    initEmployeeTodoEditor();
    initAdminShowtimesAutoSubmit();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }
})();

