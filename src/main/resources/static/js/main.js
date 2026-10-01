// WSIMS shared front-end behaviour.
// Keep this file small: each module branch should add its own page-specific
// script (e.g. attendance.js, timetable.js) rather than growing this one.

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
    // Always start on the light/white theme by default — dark mode is opt-in
    // only, via the toggle button (previously this matched the OS/browser
    // dark-mode setting automatically, which is why it looked all-dark).
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

  // Small reusable toast helper: window.wsimsToast('Saved successfully', 'success')
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

  document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initMobileSidebar();
    initCardReveal();
  });
})();
