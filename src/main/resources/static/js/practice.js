(function () {
    'use strict';

    document.querySelectorAll('.reveal-answer').forEach(function (button) {
        button.addEventListener('click', async function () {
            var item = button.closest('.practice-item');
            var panel = item.querySelector('.answer-panel');
            if (!panel) {
                return;
            }
            if (panel.dataset.loaded === 'true') {
                panel.hidden = !panel.hidden;
                button.textContent = panel.hidden ? 'Show answer' : 'Hide answer';
                return;
            }
            var original = button.textContent;
            button.disabled = true;
            button.textContent = 'Loading…';
            try {
                var data = await window.ExamPrep.request(button.getAttribute('data-reveal-url'), {
                    method: 'POST'
                });
                panel.replaceChildren();
                var label = document.createElement('p');
                label.className = 'answer-label';
                label.textContent = 'Suggested answer';
                var answer = document.createElement('p');
                answer.textContent = data.correctAnswer;
                var explanationLabel = document.createElement('p');
                explanationLabel.className = 'answer-label';
                explanationLabel.textContent = 'Explanation';
                var explanation = document.createElement('p');
                explanation.textContent = data.explanation;
                panel.appendChild(label);
                panel.appendChild(answer);
                panel.appendChild(explanationLabel);
                panel.appendChild(explanation);
                if (data.sourceReference) {
                    var source = document.createElement('p');
                    source.className = 'source-line';
                    source.textContent = 'Source: ' + data.sourceReference;
                    panel.appendChild(source);
                }
                panel.hidden = false;
                panel.dataset.loaded = 'true';
                button.textContent = 'Hide answer';
            } catch (error) {
                button.textContent = original;
                window.alert(error.message || 'The answer could not be loaded.');
            } finally {
                button.disabled = false;
            }
        });
    });
})();
