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
