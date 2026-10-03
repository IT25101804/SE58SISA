
(function () {
  const STORAGE_KEY = 'wsims-theme';

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    const btn = document.getElementById('themeToggle');
    if (btn) {
      btn.innerHTML = theme === 'dark'
        ? '<i class="bi bi-sun-fill"></i>'
        : '<i class="bi bi-moon-stars-fill"></i>';
    }
  }

  function initTheme() {
    const saved = localStorage.getItem(STORAGE_KEY) || 'light';
    applyTheme(saved);

    const btn = document.getElementById('themeToggle');
    if (btn) {
      btn.addEventListener('click', () => {
        const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        localStorage.setItem(STORAGE_KEY, current);
        applyTheme(current);
      });
    }
  }

  function initMobileSidebar() {
    const sidebar = document.querySelector('.sidebar');
    const hamburger = document.getElementById('hamburgerBtn');
    const backdrop = document.getElementById('sidebarBackdrop');
    if (!sidebar || !hamburger || !backdrop) return;

    const open = () => { sidebar.classList.add('open'); backdrop.classList.add('open'); };
    const close = () => { sidebar.classList.remove('open'); backdrop.classList.remove('open'); };

    hamburger.addEventListener('click', open);
    backdrop.addEventListener('click', close);
    sidebar.querySelectorAll('a').forEach(link => link.addEventListener('click', close));
  }

  function initCardReveal() {
    document.querySelectorAll('.card').forEach((card, i) => {
      card.style.opacity = '0';
      card.style.transform = 'translateY(6px)';
      card.style.transition = 'opacity .35s ease, transform .35s ease';
      setTimeout(() => {
        card.style.opacity = '1';
        card.style.transform = 'translateY(0)';
      }, 40 * i);
    });
  }

  window.wsimsToast = function (message, type) {
    let stack = document.querySelector('.toast-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.className = 'toast-stack';
      document.body.appendChild(stack);
    }
    const toast = document.createElement('div');
    toast.className = 'toast' + (type ? ' ' + type : '');
    toast.innerHTML = message;
    stack.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
  };

  // SISA-styled confirmation dialog, used instead of the browser's own confirm() box.
  // Any form (or submit button) with data-confirm="message" asks before submitting.
  function showConfirm(message, onConfirm) {
    const backdrop = document.createElement('div');
    backdrop.className = 'confirm-backdrop';
    backdrop.innerHTML =
      '<div class="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirmTitle" aria-describedby="confirmMessage">' +
      '<div class="confirm-icon"><i class="bi bi-exclamation-triangle-fill"></i></div>' +
      '<h3 id="confirmTitle">Are you sure?</h3>' +
      '<p id="confirmMessage"></p>' +
      '<div class="confirm-actions">' +
      '<button type="button" class="btn btn-outline" data-action="cancel">Cancel</button>' +
      '<button type="button" class="btn btn-danger" data-action="ok"><i class="bi bi-trash-fill"></i> Delete</button>' +
      '</div></div>';
    backdrop.querySelector('#confirmMessage').textContent = message;
    const previousFocus = document.activeElement;

    function close() {
      document.removeEventListener('keydown', onKey);
      backdrop.remove();
      if (previousFocus && previousFocus.focus) previousFocus.focus();
    }
    function onKey(e) { if (e.key === 'Escape') close(); }

    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop || e.target.closest('[data-action="cancel"]')) close();
      if (e.target.closest('[data-action="ok"]')) { close(); onConfirm(); }
    });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(backdrop);
    backdrop.querySelector('[data-action="cancel"]').focus();
  }

  // Capture phase, so this runs before (and replaces) any page-level confirm() handlers.
  document.addEventListener('submit', (e) => {
    const form = e.target;
    const submitter = e.submitter;
    const message = (submitter && submitter.dataset.confirm) || form.dataset.confirm;
    if (!message) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    showConfirm(message, () => HTMLFormElement.prototype.submit.call(form));
  }, true);

  document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initMobileSidebar();
    initCardReveal();
  });
})();
