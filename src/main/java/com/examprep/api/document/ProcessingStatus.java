package com.examprep.api.document;

/** Lifecycle of an uploaded document. */
public enum ProcessingStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
