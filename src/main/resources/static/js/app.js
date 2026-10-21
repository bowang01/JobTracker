// JobTrack front-end script (no inline scripts, to satisfy the CSP)
(function () {
  'use strict';

  // Ask for confirmation before submitting forms marked with data-confirm (deleting applications, document versions, ...)
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var msg = form.getAttribute('data-confirm');
    if (msg && !window.confirm(msg)) {
      e.preventDefault();
    }
  }, true);

  // Scroll to the section named by the server after a redirect (keeps #fragments out of the address bar)
  document.addEventListener('DOMContentLoaded', function () {
    var marker = document.getElementById('scroll-target');
    var target = marker && document.getElementById(marker.getAttribute('data-target'));
    if (target) target.scrollIntoView();
  });

  // Search & filter panels: open on wide screens; on phones stay folded (so the list comes first)
  // unless a filter is in use. A panel with a filter in use gets an "Active" tag either way.
  document.addEventListener('DOMContentLoaded', function () {
    var wide = window.matchMedia('(min-width: 720px)').matches;
    var panels = document.querySelectorAll('details[data-filter-panel]');
    for (var i = 0; i < panels.length; i++) {
      var panel = panels[i];
      var fields = panel.querySelectorAll('input:not([type=hidden]), select');
      var active = false;
      for (var j = 0; j < fields.length; j++) {
        if (fields[j].value) { active = true; break; }
      }
      if (active) panel.classList.add('is-active');
      if (wide) panel.open = true;
    }
  });

  // Question form: the Language field only applies to technical questions
  function syncTechnicalOnly() {
    var checked = document.querySelector('input[name=category]:checked');
    var technical = !checked || checked.value === 'TECHNICAL';
    var fields = document.querySelectorAll('[data-technical-only]');
    for (var i = 0; i < fields.length; i++) fields[i].hidden = !technical;
  }
  document.addEventListener('DOMContentLoaded', syncTechnicalOnly);
  document.addEventListener('change', function (e) {
    if (e.target && e.target.name === 'category' && e.target.type === 'radio') syncTechnicalOnly();
  });

  // Check the file size in the browser before uploading (the server validates it again)
  document.addEventListener('change', function (e) {
    var input = e.target;
    if (!input.matches || !input.matches('input[type=file][data-max-bytes]')) return;
    var max = parseInt(input.getAttribute('data-max-bytes'), 10);
    var file = input.files && input.files[0];
    if (file && max && file.size > max) {
      window.alert('File too large: ' + (file.size / 1048576).toFixed(1) + 'MB. The limit is ' + (max / 1048576) + 'MB per file.');
      input.value = '';
    }
  });
})();
