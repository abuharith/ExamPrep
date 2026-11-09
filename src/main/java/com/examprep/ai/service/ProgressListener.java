package com.examprep.ai.service;

@FunctionalInterface
public interface ProgressListener {

    void update(int percent, String message);

    ProgressListener NOOP = (percent, message) -> {
    };
}
