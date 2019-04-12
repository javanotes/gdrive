package com.docview.dto;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class FileNode {
	public FileNode() {
	}
	/**
	 * Constructor for a 'root level' file
	 * @param file
	 */
	public FileNode(File file) {
		Assert.notNull(file, "File is null");
		Assert.isTrue(file.exists(), "Does not exist");
		Assert.isTrue(file.isFile(), "Not a valid file");
		initFile(file);
	}
	
	private void initFile(File file) {
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
	/**
	 * 
	 * @param name
	 * @param parent
	 * @param type
	 */
	private FileNode(String name, FileNode parent, MimePart type) {
		super();
		setName(name);
		setParent(parent);
		setType(type);
		getParent().getChilds().add(this);
	}
	/**
	 * Constructor with parent directory and file (optional)
	 * @param dirPath
	 * @param file may be null
	 */
	public FileNode(String dirPath, File file) {
		FileNode next = null;
		
		if (StringUtils.hasText(dirPath)) {
			String[] dirs = StringUtils.tokenizeToStringArray(dirPath, "/");
			for (String dir : dirs) {
				if (next == null) {
					this.setName(dir);
					this.setType(MimePart.GDIR);
					next = this;
				} else {
					next = new FileNode(dir, next, MimePart.GDIR);
				}
			}
			
		}
		initialize(next, file);
	}
	private void initialize(FileNode next, File file) {
		if(file != null) {
			Assert.isTrue(file.exists(), "Does not exist");
			Assert.isTrue(file.isFile(), "Not a valid file");
			
			if(next != null) {
				next = new FileNode(file.getName(), next, MimePart.ofType(file.toPath()));
				next.initFile(file);
			}
			else {
				initFile(file);
			}
		}
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
		this.childs.clear();
		this.childs.addAll(childs);
	}
	public MimePart getType() {
		return type;
	}
	public void setType(MimePart type) {
		this.type = type;
		setDir(this.type == MimePart.GDIR);
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
	private String id;
	private String name;
	private boolean isDir;
	private FileNode parent;
	private final List<FileNode> childs = new ArrayList<>();
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
	public FileNode getFirstChild() {
		Assert.isTrue(hasChildren(), "Node does not have children");
		return childs.get(0);
	}
	/**
	 * 
	 * @return
	 */
	public boolean hasChildren() {
		return !childs.isEmpty();
	}
	public void close() {
		try {
			if(content != null && content.getInputStream() != null)
				content.getInputStream().close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
}
