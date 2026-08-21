(function () {
    'use strict';

    function init(doc) {
        var toggle = doc.getElementById('nav-toggle');
        var nav = doc.getElementById('site-nav');
        if (!toggle || !nav) {
            return;
        }

        function isOpen() {
            return toggle.getAttribute('aria-expanded') === 'true';
        }

        function setOpen(open) {
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            nav.classList.toggle('is-open', open);
        }

        toggle.addEventListener('click', function () {
            setOpen(!isOpen());
        });

        doc.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && isOpen()) {
                setOpen(false);
                toggle.focus();
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            init(document);
        });
    } else {
        init(document);
    }
})();
