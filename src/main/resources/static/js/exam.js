(function () {
    'use strict';

    var config = window.examConfig;
    if (!config || !config.questions || !config.questions.length) {
        return;
    }
    var questions = config.questions;
    var answers = {};
    var index = 0;
    var totalSeconds = Math.max(1, Number(config.durationMinutes) || 30) * 60;
    var remaining = totalSeconds;
    var submitted = false;

    var prompt = document.getElementById('question-prompt');
    var meta = document.getElementById('question-meta');
    var optionsBox = document.getElementById('exam-options');
    var navList = document.getElementById('exam-nav-list');
    var timerLabel = document.getElementById('exam-timer');
    var prevButton = document.getElementById('exam-prev');
    var nextButton = document.getElementById('exam-next');
    var submitButton = document.getElementById('exam-submit');
    var errorBox = document.getElementById('exam-error');

    function format(seconds) {
        var minutes = Math.floor(seconds / 60);
        var rest = seconds % 60;
        return String(minutes).padStart(2, '0') + ':' + String(rest).padStart(2, '0');
    }

    function answeredCount() {
        return Object.keys(answers).length;
    }

    function renderNav() {
        navList.innerHTML = '';
        questions.forEach(function (question, position) {
            var item = document.createElement('li');
            var button = document.createElement('button');
            button.type = 'button';
            button.textContent = String(position + 1);
            button.setAttribute('aria-label', 'Question ' + (position + 1)
                + (answers[question.id] ? ' (answered)' : ' (unanswered)'));
            if (position === index) {
                button.classList.add('is-current');
            }
            if (answers[question.id]) {
                button.classList.add('is-answered');
            }
            button.addEventListener('click', function () {
                index = position;
                renderQuestion();
            });
            item.appendChild(button);
            navList.appendChild(item);
        });
    }

    function renderQuestion() {
        var question = questions[index];
        prompt.textContent = question.prompt;
        meta.textContent = 'Question ' + (index + 1) + ' of ' + questions.length
            + ' · ' + question.difficulty.charAt(0) + question.difficulty.slice(1).toLowerCase();
        optionsBox.innerHTML = '';
        var groupName = 'question-' + question.id;
        question.options.forEach(function (option, optionIndex) {
            var label = document.createElement('label');
            label.className = 'exam-option';
            var radio = document.createElement('input');
            radio.type = 'radio';
            radio.name = groupName;
            radio.value = option;
            radio.checked = answers[question.id] === option;
            radio.addEventListener('change', function () {
                answers[question.id] = option;
                label.classList.add('is-selected');
                renderNav();
            });
            if (radio.checked) {
                label.classList.add('is-selected');
            }
            var text = document.createElement('span');
            text.textContent = option;
            var letter = document.createElement('span');
            letter.className = 'option-letter';
            letter.textContent = String.fromCharCode(65 + optionIndex) + '.';
            label.appendChild(radio);
            label.appendChild(letter);
            label.appendChild(text);
            optionsBox.appendChild(label);
        });
        prevButton.disabled = index === 0;
        nextButton.disabled = index === questions.length - 1;
        renderNav();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    function tick() {
        remaining--;
        timerLabel.textContent = format(Math.max(0, remaining));
        timerLabel.classList.toggle('is-low', remaining <= 60);
        if (remaining <= 0) {
            submit(true);
        }
    }

    async function submit(automatic) {
        if (submitted) {
            return;
        }
        var unanswered = questions.length - answeredCount();
        if (!automatic) {
            var message = unanswered > 0
                ? 'You have ' + unanswered + ' unanswered question(s). Submit the examination for scoring?'
                : 'Submit the examination for scoring?';
            if (!window.confirm(message)) {
                return;
            }
        }
        submitted = true;
        submitButton.disabled = true;
        submitButton.textContent = 'Submitting…';
        errorBox.hidden = true;
        try {
            var result = await window.ExamPrep.request(config.quizUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    answers: answers,
                    durationSeconds: Math.max(0, totalSeconds - Math.max(0, remaining))
                })
            });
            window.location.href = config.resultBaseUrl + '/' + result.attemptId;
        } catch (error) {
            submitted = false;
            submitButton.disabled = false;
            submitButton.textContent = 'Submit examination';
            errorBox.hidden = false;
            errorBox.textContent = error.message || 'The examination could not be submitted. Try again.';
        }
    }

    prevButton.addEventListener('click', function () {
        if (index > 0) {
            index--;
            renderQuestion();
        }
    });
    nextButton.addEventListener('click', function () {
        if (index < questions.length - 1) {
            index++;
            renderQuestion();
        }
    });
    submitButton.addEventListener('click', function () { submit(false); });

    timerLabel.textContent = format(remaining);
    renderQuestion();
    window.setInterval(tick, 1000);
})();
