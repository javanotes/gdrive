package com.docview.web;

import org.springframework.core.NestedRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "file not found")
public class ResourceNotFound extends NestedRuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7534959276153187959L;

	public ResourceNotFound(String msg) {
		super(msg);
	}

	public ResourceNotFound(String msg, Throwable cause) {
		super(msg, cause);
	}

}
