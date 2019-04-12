package com.docview.dto;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class FileNode {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (int) (size ^ (size >>> 32));
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileNode other = (FileNode) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (size != other.size)
			return false;
		if (type != other.type)
			return false;
		return true;
	}
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
	public boolean hasNonRootChild() {
		return childFile.isPresent();
	}
	public FileNode getLeafChild(){
		return childFile.get();
	}
	private Optional<FileNode> childFile = Optional.empty();;
	private void initialize(FileNode parent, File file) {
		if(file != null) {
			Assert.isTrue(file.exists(), "Does not exist");
			Assert.isTrue(file.isFile(), "Not a valid file");
			
			if(parent != null) {
				childFile = Optional.of( new FileNode(file.getName(), parent, MimePart.ofType(file.toPath())));
				childFile.get().initFile(file);
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
