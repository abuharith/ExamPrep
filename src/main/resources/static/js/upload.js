(function () {
    'use strict';

    var form = document.getElementById('upload-form');
    if (!form) {
        return;
    }
    var input = document.getElementById('file-input');
    var dropzone = document.getElementById('dropzone');
    var selectedLabel = document.getElementById('upload-selected');
    var submitButton = document.getElementById('upload-submit');
    var progressBox = document.getElementById('upload-progress');
    var progressBar = document.getElementById('upload-progress-bar');
    var progressText = document.getElementById('upload-progress-text');
    var resultBox = document.getElementById('upload-result');
    var chosen = null;
    var maxBytes = 25 * 1024 * 1024;
    var allowed = ['pdf', 'docx', 'doc', 'pptx', 'ppt', 'txt', 'md', 'markdown', 'rtf', 'odt', 'odp'];

    function describe(file) {
        if (!file) {
            return 'No file selected.';
        }
        var size = file.size < 1024 * 1024
            ? Math.max(1, Math.round(file.size / 1024)) + ' KB'
            : (file.size / (1024 * 1024)).toFixed(1) + ' MB';
        return file.name + ' · ' + size;
    }

    function selectFile(file) {
        chosen = file || null;
        selectedLabel.textContent = describe(chosen);
        hideResult();
    }

    function hideResult() {
        resultBox.hidden = true;
        resultBox.textContent = '';
    }

    function showResult(message, kind) {
        resultBox.hidden = false;
        resultBox.className = 'notice notice-' + (kind || 'info');
        resultBox.textContent = message;
    }

    function extensionOf(name) {
        var dot = name.lastIndexOf('.');
        return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
    }

    function validate(file) {
        if (!file) {
            return 'Choose a document to upload.';
        }
        if (allowed.indexOf(extensionOf(file.name)) < 0) {
            return 'That file type is not supported. Use PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT or ODP.';
        }
        if (file.size > maxBytes) {
            return 'That file is larger than the upload limit for this server.';
        }
        if (file.size === 0) {
            return 'That file is empty.';
        }
        return null;
    }

    input.addEventListener('change', function () {
        selectFile(input.files && input.files[0]);
    });

    ['dragenter', 'dragover'].forEach(function (name) {
        dropzone.addEventListener(name, function (event) {
            event.preventDefault();
            dropzone.classList.add('is-dragging');
        });
    });

    ['dragleave', 'dragend'].forEach(function (name) {
        dropzone.addEventListener(name, function () {
            dropzone.classList.remove('is-dragging');
        });
    });

    dropzone.addEventListener('drop', function (event) {
        event.preventDefault();
        dropzone.classList.remove('is-dragging');
        if (event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files.length) {
            selectFile(event.dataTransfer.files[0]);
        }
    });

    dropzone.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            input.click();
        }
    });

    function pollStatus(documentId) {
        var url = '/api/documents/' + documentId + '/status';
        var timer = window.setInterval(async function () {
            try {
                var status = await window.ExamPrep.request(url);
                if (status.status === 'READY') {
                    window.clearInterval(timer);
                    showResult('Text extracted from ' + status.filename + '. Opening the document…', 'success');
                    window.setTimeout(function () {
                        window.location.href = '/documents/' + documentId;
                    }, 900);
                } else if (status.status === 'FAILED') {
                    window.clearInterval(timer);
                    showResult('Extraction failed: ' + (status.failureReason || 'the document could not be read.'),
                        'error');
                    submitButton.disabled = false;
                } else if (status.message) {
                    showResult(status.message, 'info');
                }
            } catch (ignored) {
                // Retry on the next tick.
            }
        }, 2000);
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var problem = validate(chosen);
        if (problem) {
            showResult(problem, 'error');
            return;
        }
        var data = new FormData();
        data.append('file', chosen, chosen.name);

        var csrfMeta = document.querySelector('meta[name="_csrf"]');
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/documents');
        xhr.withCredentials = true;
        if (csrfMeta && headerMeta) {
            xhr.setRequestHeader(headerMeta.content, csrfMeta.content);
        }
        xhr.upload.addEventListener('progress', function (progressEvent) {
            if (progressEvent.lengthComputable) {
                var percent = Math.round((progressEvent.loaded / progressEvent.total) * 100);
                progressBar.style.width = percent + '%';
                progressText.textContent = 'Uploading… ' + percent + '%';
            }
        });
        xhr.addEventListener('load', function () {
            submitButton.disabled = false;
            progressBox.hidden = true;
            var payload = null;
            try {
                payload = JSON.parse(xhr.responseText);
            } catch (ignored) {
                payload = null;
            }
            if (xhr.status === 202 && payload) {
                showResult('Upload received. Extracting text…', 'info');
                progressBar.style.width = '0%';
                selectFile(null);
                input.value = '';
                pollStatus(payload.documentId);
            } else {
                showResult((payload && (payload.detail || payload.message))
                    || 'The upload failed. Please try again.', 'error');
            }
        });
        xhr.addEventListener('error', function () {
            submitButton.disabled = false;
            progressBox.hidden = true;
            showResult('The upload could not reach the server. Check your connection and try again.', 'error');
        });

        submitButton.disabled = true;
        hideResult();
        progressBox.hidden = false;
        progressBar.style.width = '0%';
        progressText.textContent = 'Uploading…';
        xhr.send(data);
    });
})();
