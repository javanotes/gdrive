package com.docview.dto.ui;

import java.util.ArrayList;
import java.util.List;

public class DirList extends FileId{
	
	public List<FileId> getDirs() {
		return dirs;
	}
	public void setDirs(List<FileId> dirs) {
		this.dirs = dirs;
	}
	public List<FileId> getFiles() {
		return files;
	}
	public void setFiles(List<FileId> files) {
		this.files = files;
	}
	private List<FileId> dirs = new ArrayList<>();
	private List<FileId> files = new ArrayList<>();
}
