(function () {
    'use strict';

    var main = document.querySelector('[data-tutor-url]');
    if (!main) {
        return;
    }
    var tutorUrl = main.getAttribute('data-tutor-url');
    var form = document.getElementById('tutor-form');
    var input = document.getElementById('tutor-input');
    var submitButton = document.getElementById('tutor-submit');
    var conversation = document.getElementById('conversation');
    var emptyState = document.getElementById('tutor-empty');
    var busy = false;

    function removeEmptyState() {
        if (emptyState && emptyState.parentNode) {
            emptyState.parentNode.removeChild(emptyState);
            emptyState = null;
        }
    }

    function appendMessage(role, text, references, extraClass) {
        removeEmptyState();
        var article = document.createElement('article');
        article.className = 'message ' + extraClass;
        var roleLabel = document.createElement('p');
        roleLabel.className = 'message-role';
        roleLabel.textContent = role;
        var body = document.createElement('p');
        body.className = 'message-body';
        body.textContent = text;
        article.appendChild(roleLabel);
        article.appendChild(body);
        if (references && references.length) {
            var source = document.createElement('p');
            source.className = 'source-line';
            source.textContent = 'Sources: ' + references.join(', ');
            article.appendChild(source);
        }
        conversation.appendChild(article);
        article.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        return article;
    }

    function appendError(message) {
        removeEmptyState();
        var article = document.createElement('div');
        article.className = 'notice notice-error';
        article.setAttribute('role', 'alert');
        article.textContent = message;
        conversation.appendChild(article);
        article.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }

    async function ask(question) {
        if (busy || !question) {
            return;
        }
        busy = true;
        submitButton.disabled = true;
        submitButton.textContent = 'Thinking…';
        appendMessage('You', question, [], 'is-student');
        var pending = appendMessage('ExamPrep tutor', 'Reading the relevant sections…', [], 'is-tutor');
        try {
            var answer = await window.ExamPrep.request(tutorUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ question: question })
            });
            pending.parentNode.removeChild(pending);
            appendMessage('ExamPrep tutor', answer.answer, answer.sourceReferences, 'is-tutor');
            if (answer.insufficientInformation) {
                var note = document.createElement('p');
                note.className = 'field-hint';
                note.textContent = 'The uploaded material does not fully answer this question.';
                conversation.lastElementChild.appendChild(note);
            }
        } catch (error) {
            pending.parentNode.removeChild(pending);
            appendError(error.message || 'The tutor could not answer. Try again.');
        } finally {
            busy = false;
            submitButton.disabled = false;
            submitButton.textContent = 'Ask tutor';
            input.focus();
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var question = input.value.trim();
        if (!question) {
            return;
        }
        input.value = '';
        ask(question);
    });

    input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit'));
        }
    });

    document.querySelectorAll('.suggestions .chip-button').forEach(function (button) {
        button.addEventListener('click', function () {
            ask(button.textContent.trim());
        });
    });
})();
