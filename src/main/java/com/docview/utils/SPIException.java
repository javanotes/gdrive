package com.docview.utils;

import org.springframework.core.NestedRuntimeException;

public class SPIException extends NestedRuntimeException {

	public SPIException(String msg, Throwable cause) {
		super(msg, cause);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 4350622231670573407L;

}
