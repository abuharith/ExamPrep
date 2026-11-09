package com.examprep.common.exception;

/**
 * The uploaded document yielded too little text to build study material from. For PDFs
 * this usually means the file is a scan; the message explains the next step.
 */
public class InsufficientMaterialException extends ProcessingException {

    public InsufficientMaterialException(String message) {
        super(message);
    }
}
