package com.examprep.support;

import java.time.Duration;
import java.util.function.Supplier;

public final class Await {

    private Await() {
    }

    public static <T> T until(Duration timeout, Supplier<T> probe, java.util.function.Predicate<T> condition,
                              String description) {
        long deadline = System.nanoTime() + timeout.toNanos();
        T last = null;
        while (System.nanoTime() < deadline) {
            last = probe.get();
            if (condition.test(last)) {
                return last;
            }
            try {
                Thread.sleep(120);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + description, ex);
            }
        }
        throw new AssertionError("Timed out waiting for " + description + "; last value: " + last);
    }
}
