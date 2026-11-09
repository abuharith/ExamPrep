(function () {
    'use strict';

    var main = document.querySelector('[data-study-status]');
    if (!main) {
        return;
    }
    var statusUrl = main.getAttribute('data-study-status');
    var readyUrl = main.getAttribute('data-study-ready');
    var bar = document.getElementById('study-progress-bar');
    var percentLabel = document.getElementById('study-progress-percent');
    var message = document.getElementById('study-progress-message');
    var timer = null;
    var stopped = false;

    function showFailure(reason) {
        stopped = true;
        window.clearInterval(timer);
        if (message) {
            message.textContent = 'Generation failed.';
        }
        if (bar) {
            bar.style.width = '100%';
            bar.classList.add('is-error');
            bar.classList.remove('indeterminate');
        }
        var panel = document.getElementById('study-failed-panel');
        if (!panel) {
            panel = document.createElement('section');
            panel.id = 'study-failed-panel';
            panel.className = 'panel';
            panel.setAttribute('role', 'alert');
            var heading = document.createElement('h2');
            heading.textContent = 'Generation could not be completed';
            var paragraph = document.createElement('p');
            paragraph.id = 'study-failed-reason';
            var actions = document.createElement('div');
            actions.className = 'page-actions';
            var link = document.createElement('a');
            link.className = 'btn btn-secondary';
            link.href = '/dashboard';
            link.textContent = 'Return to dashboard';
            actions.appendChild(link);
            panel.appendChild(heading);
            panel.appendChild(paragraph);
            panel.appendChild(actions);
            main.appendChild(panel);
        }
        panel.hidden = false;
        var reasonElement = panel.querySelector('p');
        if (reasonElement) {
            reasonElement.textContent = reason || 'The study package could not be generated. Try again.';
        }
    }

    async function poll() {
        if (stopped) {
            return;
        }
        try {
            var status = await window.ExamPrep.request(statusUrl);
            if (bar) {
                bar.style.width = Math.max(3, status.progressPercent) + '%';
            }
            if (percentLabel) {
                percentLabel.textContent = status.progressPercent + '%';
            }
            if (message && status.message) {
                message.textContent = status.message;
            }
            if (status.status === 'READY') {
                stopped = true;
                window.clearInterval(timer);
                if (bar) {
                    bar.style.width = '100%';
                }
                if (message) {
                    message.textContent = 'Study package ready. Opening it now…';
                }
                window.location.href = readyUrl;
            } else if (status.status === 'FAILED') {
                showFailure(status.failureReason);
            }
        } catch (error) {
            // A transient failure should not stop polling.
        }
    }

    poll();
    timer = window.setInterval(poll, 2500);
})();
