package com.docview.utils;

import java.io.IOException;

public class FileIOException extends RuntimeException {

	public FileIOException(String message, IOException cause) {
		super(message, cause);
	}
	public FileIOException(Exception cause) {
		super(cause);
	}
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

}
