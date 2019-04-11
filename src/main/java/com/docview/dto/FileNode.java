package com.docview.dto;

import java.util.ArrayList;
import java.util.List;

public class FileNode {
	
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
	public FileContent getContent() {
		return content;
	}
	public void setContent(FileContent content) {
		this.content = content;
	}
	public Mime getType() {
		return type;
	}
	public void setType(Mime type) {
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
	private FileContent content = new FileContent();
	private Mime type;
	private String accessControl;
	private String webLink;
}
