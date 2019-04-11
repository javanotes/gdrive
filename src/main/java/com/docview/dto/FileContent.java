package com.docview.dto;

import java.io.Closeable;
import java.io.IOException;

import org.springframework.core.io.Resource;

public class FileContent implements Closeable{

	public long getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}
	public long getLastModifiedAt() {
		return lastModifiedAt;
	}
	public void setLastModifiedAt(long lastModifiedAt) {
		this.lastModifiedAt = lastModifiedAt;
	}
	public Resource getContentBytes() {
		return contentBytes;
	}
	public void setContentBytes(Resource contentBytes) {
		this.contentBytes = contentBytes;
	}
	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	private long createdAt;
	private long lastModifiedAt;
	private Resource contentBytes;
	private long size;
	@Override
	public String toString() {
		return " [createdAt=" + createdAt + ", lastModifiedAt=" + lastModifiedAt + ", size=" + size + "]";
	}
	@Override
	public void close() throws IOException {
		if(contentBytes != null) {
			contentBytes.getInputStream().close();
		}
		
	}
}
