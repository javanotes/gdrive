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
	 * Get a file item by id.
	 * @param id
	 * @return
	 */
	FileNode getById(String id);
	/**
	 * download a file content from drive
	 * @param path
	 * @return
	 * @throws FileNotFoundException
	 */
	FileNode get(String path) throws FileNotFoundException;
	/**
	 * List the root directory contents.
	 * @param rootFolder
	 * @return
	 */
	FileNode listRoot();
	/**
	 * List the content of this dir.
	 * @param dir
	 * @return
	 * @throws FileNotFoundException 
	 */
	FileNode list(String dir) throws FileNotFoundException;
	/**
	 * @deprecated Not sure if this is correct
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
