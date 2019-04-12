package com.docview.provider;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.docview.DocView;
import com.docview.ServiceProvider;
import com.docview.dto.FileNode;
import com.docview.dto.MimePart;
import com.docview.utils.FileIOException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
/**
 * Core integration with Google drive Java API.
 * @author Sutanu_Dalui
 *
 */
@Service
class GDriveView implements DocView {
	private static Logger log = LoggerFactory.getLogger(GDriveView.class.getSimpleName());
	/**
	 * The meta fields that we need
	 */
	private static final String FIELDS = "nextPageToken, files(id, name, mimeType, size, modifiedTime, createdTime, owners, parents, permissions, version, webContentLink)";
	private static final int DEFAULT_PAGES = 10;
	private static final String ROOT = "My Drive";
	/*
	 * Various search queries
	 */
	private static final String NAME_PARAM = "@param@";
	private static final String NAME_DIR_QUERY = "name = '" + NAME_PARAM + "' and mimeType = '"+MimePart.GDIR.mimeType()+"'";
	private static final String DIR_QUERY = "mimeType = '"+MimePart.GDIR.mimeType()+"'";
	private static final String FILE_QUERY = "mimeType != '"+MimePart.GDIR.mimeType()+"'";
	private static final String NAME_FILE_QUERY = "name = '" + NAME_PARAM + "'  and "+FILE_QUERY;
	private static final String CHILD_QUERY = "'"+NAME_PARAM+"' in parents";
	/**
	 * &1=dir query, &2=parent id
	 */
	private static final String NAME_CHILD_DIR_QUERY = NAME_DIR_QUERY+" and "+CHILD_QUERY; 
	/**
	 * &1=file name, &2=parent id
	 */
	private static final String NAME_CHILD_FILE_QUERY = NAME_FILE_QUERY+" and "+CHILD_QUERY;
	
	@Value("${file.downloadDir}")
	private String downloadDir;
	@Autowired
	ServiceProvider provider;
	private Path downloadsPath;
	@PostConstruct
	private void init() throws IOException {
		downloadsPath = Files.createTempDirectory(downloadDir);
		log.info("Downloads path: "+downloadsPath);
	}
	/**
	 * A resultset type of iterating interface for pulling next available documents on GDrive
	 * @author Sutanu_Dalui
	 *
	 */
	private class GDriveIterator implements Iterator<List<File>>{
		protected GDriveIterator(String query) {
			super();
			this.query = query;
		}
		
		private int pageSize = DEFAULT_PAGES;
		private final String query;
		private com.google.api.services.drive.Drive.Files.List rootBuilder;
		private String nextPageToken = null;
		private final List<File> currentPage = new ArrayList<>();
		
		private Drive service = provider.getInstance();
		private boolean hasMore = true;
		@Override
		public boolean hasNext() {
			if(!hasMore)
				return false;
			currentPage.clear();
			try {
				rootBuilder = service.files().list();
			} catch (IOException e) {
				throw new FileIOException("Unable to initialize file listing", e);
			}
			try {
				FileList list = rootBuilder.setPageSize(pageSize)
						.setQ(query)
				        .setFields(FIELDS)
				        .setPageToken(nextPageToken)
				        .execute();
				
				nextPageToken = list.getNextPageToken();
				currentPage.addAll(list.getFiles());
				hasMore = nextPageToken != null;
				
			} catch (IOException e) {
				throw new FileIOException("Unable to iterate pageset", e);
			}
			return !currentPage.isEmpty();
		}

		@Override
		public List<File> next() {
			return new ArrayList<>(currentPage);
		}
		
	}
	
	private Iterable<List<File>> listGDrive(String query){
		
		return new Iterable<List<File>>() {
			@Override
			public Iterator<List<File>> iterator() {
				return new GDriveIterator(query);
			}
		};
	}
	private static class FileComparator implements Comparator<File>{

		@Override
		public int compare(File o1, File o2) {
			return Long.compare(o1.getVersion(), o2.getVersion());
		}
		
	}
	/**
	 * To get the latest versioned document at a particular level
	 * @param query
	 * @return
	 */
	private List<File> getLatestVersionChilds(String query) {
		Map<String, List<File>> filesGrouped = StreamSupport
				.stream(listGDrive(query).spliterator(), false)
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(File::getId));
		
		
		//google drive keeps multiple versions. take the latest
		return filesGrouped.entrySet().stream().map(e -> {
			TreeSet<File> ordered = new TreeSet<>(new FileComparator());
			ordered.addAll(e.getValue());
			return ordered.last();
		}).collect(Collectors.toList());
	}
	/**
	 * Start walking recursively from the given tree
	 * @param rootDir
	 * @return
	 * @throws IOException
	 */
	private FileNode walk(String rootDir, boolean recursive) throws IOException {
		Drive service = provider.getInstance();
		
		FileNode root = new FileNode();
		
		List<File> files = getLatestVersionChilds(StringUtils.hasText(rootDir) ? NAME_DIR_QUERY.replaceFirst(NAME_PARAM, rootDir) : DIR_QUERY);
		root.setName(StringUtils.hasText(rootDir) ? rootDir : ROOT);
		
		try 
		{
			log.info("found files: "+files.size());
			for(File f : files) {
				log.debug("found file: "+f.getName() +" "+f.getVersion() + " " + f.getMimeType());
				FileNode node = associate(root, f);
				if (node.isDir()) {
					walkTree(f.getId(), service, node, recursive);
				} else {
					log.info("\tFile -- " + node);
				}
			}
			
			return root;
		} 
		catch (NoSuchElementException e) {
			throw new IOException(e);
		}
	}
	/**
	 * Object transformation
	 * @param f
	 * @return
	 */
	private static FileNode newNode(File f) {
		FileNode n = new FileNode();
		n.setId(f.getId());
		n.setName(f.getName());
		n.setType(MimePart.ofType(f.getMimeType()));
		if (f.getCreatedTime() != null) {
			n.setCreatedAt(f.getCreatedTime().getValue());
		}
		if (f.getModifiedTime() != null) {
			n.setLastModifiedAt(f.getModifiedTime().getValue());
		}
		n.setSize(f.getSize() == null ? 0 : f.getSize());
		if (f.getPermissionIds() != null) {
			n.setAccessControl(f.getPermissionIds().toString());
		}
		n.setDir(n.getType() == MimePart.GDIR);
		return n;
	}
	/**
	 * Associate a parent to child. Return the child node
	 * @param parent
	 * @param child
	 * @return
	 */
	private static FileNode associate(FileNode parent, File child) {
		FileNode node = newNode(child);
		node.setParent(parent);
		parent.getChilds().add(node);
		return node;
	}
	/**
	 * A depth first traversal of the directory tree.
	 * @param rootId
	 * @param service
	 * @param root
	 * @param recursive 
	 * @throws IOException
	 */
	private void walkTree(String rootId, Drive service, FileNode root, boolean recursive) throws IOException {
		List<File> files = getLatestVersionChilds(CHILD_QUERY.replaceFirst(NAME_PARAM, rootId));
        if (files == null || files.isEmpty()) {
            return;
        } 
        else 
        {
        	log.info("Entering directory: "+root.getName());
            for (File file : files) {
            	FileNode child = associate(root, file);
            	if(recursive && MimePart.GDIR.mimeType().equals(file.getMimeType())) {
            		walkTree(file.getId(), service, child, recursive);
            	}
            	else {
            		log.info("\tFile -- "+child);
            	}
                
            }
        }
	}
	
	@Override
	public FileNode list(String rootFolder) {
		try {
			return walk(rootFolder, false);
		} catch (IOException e) {
			if(e.getCause() instanceof NoSuchElementException) {
				throw new FileIOException("Root folder not found - "+rootFolder, e);
			}
			throw new FileIOException("Recursive traversal failure", e);
		}
	}
	private static final File NOFILE = new File().setName("NOFILE");
	private static final File NODIR = new File().setName("NODIR");
	
	private File getFile(String name) {
		return StreamSupport
		.stream(listGDrive(NAME_FILE_QUERY.replaceFirst(NAME_PARAM, name)).spliterator(), false)
		.flatMap(List::stream).findFirst().orElse(NOFILE);
	}
	@SuppressWarnings("unused")
	private void doMediaDownload(java.io.File outFile, Drive service, String downloadLink) throws IOException, GeneralSecurityException {
		FileOutputStream out = new FileOutputStream(outFile, true);
		MediaHttpDownloader downloader =
		        new MediaHttpDownloader(GoogleNetHttpTransport.newTrustedTransport(), service.getRequestFactory().getInitializer());
		downloader.setDirectDownloadEnabled(true);
		    //downloader.setProgressListener(new FileDownloadProgressListener());
		downloader.download(new GenericUrl(downloadLink), out);
		out.flush();
		out.close();
	}
	@Override
	public FileNode get(String path) throws FileNotFoundException {
		try 
		{
			String fileName = StringUtils.getFilename(path);
			Assert.notNull(fileName, "File name is not valid: "+path);
			Assert.notNull(StringUtils.getFilenameExtension(fileName), "File name is not valid: "+path);
			
			if(fileName.equals(path)) {
				return getFileNoDir(path);
			}
			else {
				String dirs = path.substring(0, path.indexOf(fileName));
				return getFileWithDir(dirs, fileName);
			}
		} 
		catch (NoSuchElementException e) {
			throw new FileNotFoundException(path);
		}
		catch (Exception e) {
			log.error("Exception encountered on file get()", e);
			throw new FileNotFoundException(path + " => " +e.getMessage());
		}
		
	}

	private FileNode getFileWithDir(String dirs, String fileName) throws IOException {
		// recursively traverse the complete directory structure
		// to extract the parent id
		FileNode root = new FileNode(dirs, null);
		boolean keepTraversing = root.hasChildren() && root.getFirstChild().isDir();
		while (keepTraversing) {
			File f = getDirectory(root.getName(), root.getParent());
			root.setId(f.getId());
			keepTraversing = root.hasChildren() && root.getFirstChild().isDir();
			if (keepTraversing) {
				root = root.getFirstChild();
			}
		}
		
		String qry = NAME_CHILD_FILE_QUERY.replaceFirst(NAME_PARAM, fileName).replaceFirst(NAME_PARAM, root.getId());
				
		File fileElem = StreamSupport
		.stream(listGDrive(qry).spliterator(), false)
		.flatMap(List::stream).findFirst().orElse(NOFILE);
		
		if(fileElem == NOFILE)
			throw new NoSuchElementException(fileName);
		
		FileNode file = download(fileElem);
		file.setParent(root);
		root.getChilds().add(file);
		//now root contains parent and child
		return file;
	}
	private FileNode getFileNoDir(String path) throws FileNotFoundException, IOException {
		File found = getFile(path);
		if(found == NOFILE)
			throw new NoSuchElementException();
		
		return download(found);
	}
	private FileNode download(File found) throws FileNotFoundException, IOException {
		Drive service = provider.getInstance();
		java.io.File outFile = new java.io.File(downloadsPath.toFile(), found.getName());
		try(FileOutputStream out = new FileOutputStream(outFile, true)){
			service.files().get(found.getId()).executeAndDownloadTo(out);
			out.flush();
		}
		
		FileNode file = new FileNode(outFile);
		file.setLastModifiedAt(found.getModifiedTime().getValue());
		file.setCreatedAt(found.getCreatedTime().getValue());
		file.setType(MimePart.ofType(found.getMimeType()));
		file.setSize(found.getSize());
		file.setWebLink(found.getWebContentLink());
		if (found.getPermissionIds() != null) {
			file.setAccessControl(found.getPermissionIds().toString());
		}
		return file;
	}
	@Override
	public String put(FileNode path) {
		try 
		{
			if (path.isDir()) {
				return putFileAndDir(path);
			} 
			else {
				return putFile(path);
			}
		} 
		catch (IOException e) {
			throw new FileIOException("upload to drive failed", e);
		}
	}
	private String putFile(FileNode path) throws IOException {
		File found = getFile(path.getName());
		if(found == NOFILE) {
			return create(path);
		}
		else {
			return update(path, found.getId());
		}
	}
	private String putFileAndDir(FileNode path) throws IOException {
		FileNode next = putDir(path);
		if (next != null) {
			return putFile(next);
		}
		return path.getName();
	}
	/**
	 * recursively creates the directory path
	 * @param root
	 * @return the leaf node (file), or null if there was no file
	 * @throws IOException
	 */
	private FileNode putDir(final FileNode root) throws IOException {
		FileNode next = root;
		while(next != null && next.isDir()) {
			String id = putDir(next.getName(), next.getParent());
			next.setId(id);
			next = next.hasChildren() ? next.getFirstChild() : null;
		}
		return next;
	}
	/**
	 * Get the directory metadata
	 * @param name
	 * @param parent
	 * @return
	 */
	private File getDirectory(String name, FileNode parent) {
		String dirQry = parent != null
				? (NAME_CHILD_DIR_QUERY.replaceFirst(NAME_PARAM, name).replaceFirst(NAME_PARAM, parent.getId()))
				: (NAME_DIR_QUERY.replaceFirst(NAME_PARAM, name));
				
		return StreamSupport
		.stream(listGDrive(dirQry).spliterator(), false)
		.flatMap(List::stream).findFirst().orElse(NODIR);
	}
	
	private String putDir(String name, FileNode parent) throws IOException {
		File dir = getDirectory(name, parent);
		if(dir == NODIR) {
			File fileMetadata = new File();
			fileMetadata.setName(name);
			fileMetadata.setMimeType(MimePart.GDIR.mimeType());
			if(parent != null) {
				fileMetadata.setParents(Collections.singletonList(parent.getId()));
			}

			Drive drive = provider.getInstance();
			File file = drive.files().create(fileMetadata).setFields("id").execute();
			return file.getId();
		}
		
		return dir.getId();
	}
	private String update(FileNode path, String id) throws IOException {
		Drive driveService = provider.getInstance();
		FileContent mediaContent = new FileContent(path.getType().mimeType(), path.getContent().getFile());
		return driveService.files().update(id, null, mediaContent).execute().getId();
	}
	private String create(FileNode path) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setName(path.getName());
		java.io.File filePath = path.getContent().getFile();

		FileContent mediaContent = new FileContent(path.getType().mimeType(), filePath);

		Drive driveService = provider.getInstance();
		File file = driveService.files().create(fileMetadata, mediaContent).setFields("id").execute();
		return file.getId();
	}
	@Override
	public FileNode listFiles() {
		FileNode root = new FileNode();
		List<File> files = getLatestVersionChilds(FILE_QUERY);
		root.setName(ROOT);
		try 
		{
			log.info("found files: "+files.size());
			for(File f : files) {
				log.debug("found file: "+f.getName() +" "+f.getVersion() + " " + f.getMimeType());
				FileNode node = associate(root, f);
				log.info("\tFile -- " + node);
			}
			
			return root;
		} 
		catch (NoSuchElementException e) {
			throw new FileIOException(e);
		}
	}
	@Override
	public boolean delete(String path) {
		File found = getFile(path);
		if(found == NOFILE)
			return false;
		else {
			Drive driveService = provider.getInstance();
			try {
				driveService.files().delete(found.getId()).execute();
			} catch (IOException e) {
				log.warn("Delete failed for: "+path+" => "+e.getMessage());
				log.debug("", e);
				return false;
			}
		}
		return true;
	}
	@Override
	public String mkDirs(FileNode path) {
		try {
			putDir(path);
			return path.getId();
		} catch (IOException e) {
			throw new FileIOException("directory creation failed", e);
		}
	}
	
}
