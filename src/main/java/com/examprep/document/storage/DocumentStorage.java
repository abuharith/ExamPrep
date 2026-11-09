package com.examprep.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Private document storage. Files live outside the served static resources and are
 * addressed only by an internally generated key, never by a client-supplied path.
 */
public interface DocumentStorage {

    Path store(InputStream content, String extension) throws IOException;

    Path resolve(String storageKey);

    void delete(String storageKey) throws IOException;

    void deleteQuietly(String storageKey);

    Path createTempFile(String extension) throws IOException;
}
