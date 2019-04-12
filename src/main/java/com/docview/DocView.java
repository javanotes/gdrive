package com.docview;

import java.io.FileNotFoundException;

import com.docview.dto.FileNode;
/**
 * Service facade for document viewer operations
 * @author Sutanu_Dalui
 *
 */
public interface DocView {
	/**
	 * download a file content from drive
	 * @param path
	 * @return
	 * @throws FileNotFoundException
	 */
	FileNode get(String path) throws FileNotFoundException;
	/**
	 * Traverse the directory tree upto one level. If no rootFolder is specified
	 * this will only show the documents under a given folder.
	 * @param rootFolder
	 * @return
	 */
	FileNode list(String rootFolder);
	/**
	 * List all files in the drive
	 * @return
	 */
	FileNode listFiles();
	/**
	 * upload a file content to drive
	 * @param path
	 * @return id
	 */
	String put(FileNode path);
	/**
	 * Create a directory structure, if not present, in drive
	 * @param path
	 * @return
	 */
	String mkDirs(FileNode path);
	/**
	 * Delete a file if exists
	 * @param path
	 * @return
	 */
	boolean delete(String path);
}
