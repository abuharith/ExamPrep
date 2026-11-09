package com.examprep.document.parser;

import java.util.Locale;
import java.util.Set;

/**
 * Shared behaviour for format-specific parsers. Each subclass declares the file extensions it
 * handles and implements {@link #extract}; the base class owns the extension matching so every
 * parser answers {@link #supports} the same way (case-insensitive, null-safe).
 */
public abstract class AbstractDocumentParser implements DocumentParser {

    private final Set<String> extensions;

    /** @param extensions lower-case extensions without a dot, for example {@code "pdf"} */
    protected AbstractDocumentParser(String... extensions) {
        this.extensions = Set.of(extensions);
    }

    @Override
    public final boolean supports(String extension) {
        return extension != null && extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /** The extensions this parser handles, mainly useful for diagnostics and tests. */
    public final Set<String> supportedExtensions() {
        return extensions;
    }
}
