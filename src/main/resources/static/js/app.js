(function () {
    'use strict';

    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

    async function request(url, options) {
        var config = Object.assign({ credentials: 'same-origin' }, options || {});
        var headers = new Headers((options && options.headers) || {});
        var method = (config.method || 'GET').toUpperCase();
        if (csrfMeta && csrfHeaderMeta && method !== 'GET' && method !== 'HEAD') {
            headers.set(csrfHeaderMeta.content, csrfMeta.content);
        }
        config.headers = headers;
        var response = await fetch(url, config);
        if (!response.ok) {
            var detail = response.status === 401
                ? 'Your session has expired. Sign in again.'
                : 'Something went wrong. Please try again.';
            try {
                var body = await response.json();
                detail = body.detail || body.message || detail;
            } catch (ignored) {
                // Keep the generic message when the body is not JSON.
            }
            var error = new Error(detail);
            error.status = response.status;
            throw error;
        }
        if (response.status === 204) {
            return null;
        }
        var type = response.headers.get('content-type') || '';
        return type.indexOf('application/json') >= 0 ? response.json() : response.text();
    }

    window.ExamPrep = { request: request };

    document.querySelectorAll('[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!window.confirm(form.getAttribute('data-confirm'))) {
                event.preventDefault();
            }
        });
    });

    // Document pages poll extraction status and refresh once it settles.
    document.querySelectorAll('[data-document-poll]').forEach(function (element) {
        var url = element.getAttribute('data-document-poll');
        var message = document.getElementById('document-status-message');
        var timer = window.setInterval(async function () {
            try {
                var status = await request(url);
                if (message && status.message) {
                    message.textContent = status.message;
                }
                if (status.status === 'READY' || status.status === 'FAILED') {
                    window.clearInterval(timer);
                    window.location.reload();
                }
            } catch (ignored) {
                // Transient network errors are retried on the next tick.
            }
        }, 2500);
    });

    // Keep the current section visible in the study rail.
    var railLinks = document.querySelectorAll('.study-rail a[href^="#"]');
    if (railLinks.length && 'IntersectionObserver' in window) {
        var map = {};
        railLinks.forEach(function (link) {
            map[link.getAttribute('href').slice(1)] = link;
        });
        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                var link = map[entry.target.id];
                if (!link) {
                    return;
                }
                if (entry.isIntersecting) {
                    railLinks.forEach(function (other) { other.style.color = ''; });
                    link.style.color = 'var(--accent)';
                }
            });
        }, { rootMargin: '-20% 0px -70% 0px' });
        Object.keys(map).forEach(function (id) {
            var section = document.getElementById(id);
            if (section) {
                observer.observe(section);
            }
        });
    }
})();
