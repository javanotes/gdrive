package com.docview.dto;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class FileNode implements Closeable{
	public FileNode() {
	}
	/**
	 * 
	 * @param file
	 */
	public FileNode(File file) {
		Assert.notNull(file, "File is null");
		Assert.isTrue(file.exists(), "Does not exist");
		Assert.isTrue(file.isFile(), "Not a valid file");
		try {
			BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			setCreatedAt(attr.creationTime().toMillis());
			setLastModifiedAt(attr.lastModifiedTime().toMillis());
			setSize(attr.size());
		} catch (IOException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
		setName(file.getName());
		setContent(new FileSystemResource(file));
		setType(MimePart.ofType(file.toPath()));
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isDir() {
		return isDir;
	}
	public void setDir(boolean isDir) {
		this.isDir = isDir;
	}
	public FileNode getParent() {
		return parent;
	}
	public void setParent(FileNode parent) {
		this.parent = parent;
	}
	public List<FileNode> getChilds() {
		return childs;
	}
	public void setChilds(List<FileNode> childs) {
		this.childs = childs;
	}
	public MimePart getType() {
		return type;
	}
	public void setType(MimePart type) {
		this.type = type;
	}
	public String getAccessControl() {
		return accessControl;
	}
	public void setAccessControl(String accessControl) {
		this.accessControl = accessControl;
	}
	@Override
	public String toString() {
		return " [name=" + name + ", parent=" + (parent != null ? parent.getName() : "nil") + ", childs=" + childs.size() + ", content=" + content
				+ ", type=" + type + ", accessControl=" + accessControl + "]";
	}
	public String getWebLink() {
		return webLink;
	}
	public void setWebLink(String webLink) {
		this.webLink = webLink;
	}
	private String name;
	private boolean isDir;
	private FileNode parent;
	private List<FileNode> childs = new ArrayList<>();
	private MimePart type = MimePart.UNK;
	private String accessControl;
	private String webLink;
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
	public Resource getContent() {
		return content;
	}
	public void setContent(Resource content) {
		this.content = content;
	}
	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	private long createdAt;
	private long lastModifiedAt;
	private Resource content;
	private long size;
	@Override
	public void close() throws IOException {
		if(content != null && content.getInputStream() != null)
			content.getInputStream().close();
	}
}
