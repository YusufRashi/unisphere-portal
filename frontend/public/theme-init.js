// Applies the saved (or system) colour theme before the app renders, so dark mode never flashes white first.
// A plain file rather than an inline script, because the Content-Security-Policy allows scripts from this site only.
;(function () {
  var preference = 'system'
  try {
    preference = localStorage.getItem('unisphere-theme') || 'system'
  } catch (e) {
    // Storage can be unavailable (private windows, blocked site data): fall back to the system setting.
  }
  var dark = preference === 'dark' || (preference === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
})()
