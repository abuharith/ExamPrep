(function () {
    'use strict';

    var cards = window.flashcardData || [];
    if (!cards.length) {
        return;
    }
    var front = document.getElementById('flashcard-front');
    var back = document.getElementById('flashcard-back');
    var source = document.getElementById('flashcard-source');
    var answer = document.getElementById('flashcard-answer');
    var position = document.getElementById('card-position');
    var revealButton = document.getElementById('card-reveal');
    var prevButton = document.getElementById('card-prev');
    var nextButton = document.getElementById('card-next');
    var finishButton = document.getElementById('finish-review');
    var reviewedLabel = document.getElementById('reviewed-count');
    var index = 0;
    var revealed = new Set();
    var reviewed = 0;

    function render() {
        var card = cards[index];
        front.textContent = card.front;
        back.textContent = card.back;
        if (source) {
            source.textContent = card.sourceReference ? 'Source: ' + card.sourceReference : '';
        }
        var isRevealed = revealed.has(index);
        answer.hidden = !isRevealed;
        revealButton.textContent = isRevealed ? 'Hide answer' : 'Reveal answer';
        if (position) {
            position.textContent = String(index + 1);
        }
        prevButton.disabled = index === 0;
        nextButton.disabled = index === cards.length - 1;
    }

    function reveal() {
        if (!revealed.has(index)) {
            revealed.add(index);
            reviewed++;
            if (reviewedLabel) {
                reviewedLabel.textContent = String(reviewed);
            }
        } else {
            revealed.delete(index);
        }
        render();
    }

    function move(delta) {
        var next = Math.min(cards.length - 1, Math.max(0, index + delta));
        if (next !== index) {
            index = next;
            render();
        }
    }

    revealButton.addEventListener('click', reveal);
    prevButton.addEventListener('click', function () { move(-1); });
    nextButton.addEventListener('click', function () { move(1); });

    document.addEventListener('keydown', function (event) {
        if (event.target && /INPUT|TEXTAREA|SELECT/.test(event.target.tagName)) {
            return;
        }
        if (event.key === 'ArrowLeft') {
            event.preventDefault();
            move(-1);
        } else if (event.key === 'ArrowRight') {
            event.preventDefault();
            move(1);
        } else if (event.key === ' ') {
            event.preventDefault();
            reveal();
        }
    });

    if (finishButton) {
        finishButton.addEventListener('click', async function () {
            if (reviewed === 0) {
                finishButton.textContent = 'Reveal a card first';
                window.setTimeout(function () { finishButton.textContent = 'Finish review'; }, 1800);
                return;
            }
            var url = document.querySelector('[data-review-url]').getAttribute('data-review-url');
            finishButton.disabled = true;
            finishButton.textContent = 'Saving…';
            try {
                await window.ExamPrep.request(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ cardsReviewed: reviewed })
                });
                finishButton.textContent = 'Review recorded';
            } catch (error) {
                finishButton.disabled = false;
                finishButton.textContent = 'Try again';
            }
        });
    }

    render();
})();
