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
import java.util.function.Predicate;
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
import com.google.api.services.drive.Drive.Files.Get;
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
	private static final String FIELDS = "id, name, mimeType, size, modifiedTime, createdTime, owners, parents, permissions, version, webContentLink";
	private static final String QRY_FIELDS = "nextPageToken, files("+FIELDS+")";
	private static final int DEFAULT_PAGES = 10;
	
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
	 * &1=dir name, &2=parent id
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
		downloadsPath.toFile().deleteOnExit();
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
				com.google.api.services.drive.Drive.Files.List queryBld = rootBuilder
						.setPageSize(pageSize);
				if(StringUtils.hasText(query)) {
					queryBld.setQ(query);
				}
				FileList list = queryBld.setFields(QRY_FIELDS)
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
	static class RootElementPredicate implements Predicate<File>{

		protected RootElementPredicate(String id) {
			super();
			this.id = id;
			log.info("Root id : "+id);
		}

		private String id;

		@Override
		public boolean test(File t) {
			return t.getParents() != null && t.getParents().contains(id);
		}
		
	}
	private final Predicate<File> MatchAll = t -> true;
	
	/**
	 * To get the latest versioned document at a particular level
	 * @param query
	 * @param predicate 
	 * @return
	 */
	private List<File> getLatestVersion(String query, Predicate<File> predicate) {
		Map<String, List<File>> filesGrouped = StreamSupport
				.stream(listGDrive(query).spliterator(), false)
				.flatMap(List::stream)
				.filter(predicate)
				.collect(Collectors.groupingBy(File::getId));
		
		
		//google drive keeps multiple versions. take the latest
		return filesGrouped.entrySet().stream().map(e -> {
			TreeSet<File> ordered = new TreeSet<>(new FileComparator());
			ordered.addAll(e.getValue());
			return ordered.last();
		})
		.collect(Collectors.toList());
	}
	private List<File> getLatestVersion(String query) {
		return getLatestVersion(query, MatchAll);
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
		List<File> files = getLatestVersion(StringUtils.hasText(rootDir) ? NAME_DIR_QUERY.replaceFirst(NAME_PARAM, rootDir) : DIR_QUERY);
		root.setName(StringUtils.hasText(rootDir) ? rootDir : GROOT);
		
		try 
		{
			log.info("found files: "+files.size());
			for(File f : files) {
				log.info("file: "+f.getName() +", "+f.getParents() + ", " + f.getMimeType());
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
	 * @param meta
	 * @return
	 */
	private static FileNode newNode(File meta, FileNode from) {
		FileNode n = from == null ? new FileNode() : from;
		n.setId(meta.getId());
		n.setName(meta.getName());
		if(meta.getParents() != null && !meta.getParents().isEmpty()) {
			String parent = meta.getParents().get(0);
			FileNode p = new FileNode();
			p.setId(parent);
			p.getChilds().add(n);
			n.setParent(p);
		}
		n.setType(MimePart.ofType(meta.getMimeType()));
		if (meta.getCreatedTime() != null) {
			n.setCreatedAt(meta.getCreatedTime().getValue());
		}
		if (meta.getModifiedTime() != null) {
			n.setLastModifiedAt(meta.getModifiedTime().getValue());
		}
		n.setSize(meta.getSize() == null ? 0 : meta.getSize());
		if (meta.getPermissionIds() != null) {
			n.setAccessControl(meta.getPermissionIds().toString());
		}
		n.setDir(n.getType() == MimePart.GDIR);
		n.setWebLink(meta.getWebContentLink());
		
		return n;
	}
	/**
	 * Associate a parent to child. Return the child node
	 * @param parent
	 * @param child
	 * @return
	 */
	private static FileNode associate(FileNode parent, File child) {
		FileNode node = newNode(child, null);
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
		List<File> files = getLatestVersion(CHILD_QUERY.replaceFirst(NAME_PARAM, rootId));
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
	
	public FileNode listRoot_0() {
		try {
			return walk("", false);
		} catch (IOException e) {
			if(e.getCause() instanceof NoSuchElementException) {
				throw new FileIOException("Root folder not found", e);
			}
			throw new FileIOException("Recursive traversal failure", e);
		}
	}
	
	@Override
	public FileNode listRoot() {

		List<File> files = getLatestVersion(CHILD_QUERY.replaceFirst(NAME_PARAM, "root"));

		FileNode root = new FileNode();
		root.setName(GROOT);
		try {
			log.info("Root files: " + files.size());
			for (File f : files) {
				log.debug("found file: " + f.getName() + " " + f.getParents() + " " + f.getMimeType());
				FileNode node = associate(root, f);
				log.debug("\tFile -- " + node);
			}

			return root;
		} catch (Exception e) {
			throw new FileIOException(e);
		}

	}
	private static final File NOFILE = new File().setName("NOFILE");
	private static final File NODIR = new File().setName("NODIR");
	
	private File getFileByName(String name) throws IOException {
		Drive drive = provider.getInstance();
		return drive.files().list()
		.setQ(NAME_FILE_QUERY.replaceFirst(NAME_PARAM, name))
		.setPageSize(1)
        .setFields(QRY_FIELDS)
        .execute().getFiles().stream().findFirst().orElse(NOFILE);
		
	}
	private File getDirByName(String name) throws IOException {
		Drive drive = provider.getInstance();
		return drive.files().list()
		.setQ(NAME_DIR_QUERY.replaceFirst(NAME_PARAM, name))
		.setPageSize(1)
        .setFields(QRY_FIELDS)
        .execute().getFiles().stream().findFirst().orElse(NODIR);
	}
	
	private File getFileByQuery(String query) throws IOException {
		Drive drive = provider.getInstance();
		return drive.files().list()
		.setQ(query)
		.setPageSize(1)
        .setFields(QRY_FIELDS)
        .execute().getFiles().stream().findFirst().orElse(NOFILE);
		
	}
	private File getDirByQuery(String query) throws IOException {
		Drive drive = provider.getInstance();
		return drive.files().list()
		.setQ(query)
		.setPageSize(1)
        .setFields(QRY_FIELDS)
        .execute().getFiles().stream().findFirst().orElse(NODIR);
		
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
				
		File fileElem = getFileByQuery(qry);
		
		if(fileElem == NOFILE)
			throw new NoSuchElementException(fileName);
		
		FileNode file = download(fileElem);
		file.setParent(root);
		root.getChilds().add(file);
		//now root contains parent and child
		return file;
	}
	private FileNode getFileNoDir(String path) throws FileNotFoundException, IOException {
		File found = getFileByName(path);
		if(found == NOFILE)
			throw new NoSuchElementException();
		
		return download(found);
	}
	private FileNode download(File found) throws FileNotFoundException, IOException {
		Drive service = provider.getInstance();
		java.io.File outFile = new java.io.File(downloadsPath.toFile(), found.getName());
		
		try(FileOutputStream out = new FileOutputStream(outFile, true)){
			Get request = service.files().get(found.getId());
			if (isMediaType(found)) {
				request.getMediaHttpDownloader().setDirectDownloadEnabled(true);
				request.executeMediaAndDownloadTo(out);
			}
			else {
				request.executeAndDownloadTo(out);
			}
			out.flush();
		}
		
		FileNode file = new FileNode(outFile);
		newNode(found, file);
		
		return file;
	}
	
	private static boolean isMediaType(File f) {
		String mime = f.getMimeType();
		return mime != null && (mime.startsWith("image") || mime.startsWith("video"));
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
		File found = getFileByName(path.getName());
		if(found == NOFILE) {
			return create(path);
		}
		else {
			return update(path, found);
		}
	}
	private String putFileAndDir(FileNode path) throws IOException {
		FileNode next = putDir(path);
		if (next != null) {
			String fileId = putFile(next);
			return fileId;
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
	 * @throws IOException 
	 */
	private File getDirectory(String name, FileNode parent) throws IOException {
		String dirQry = parent != null
				? (NAME_CHILD_DIR_QUERY.replaceFirst(NAME_PARAM, name).replaceFirst(NAME_PARAM, parent.getId()))
				: (NAME_DIR_QUERY.replaceFirst(NAME_PARAM, name));
				
		return getDirByQuery(dirQry);
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
			File file = drive.files().create(fileMetadata).setFields("id, parents").execute();
			return file.getId();
		}
		
		return dir.getId();
	}
	private String update(FileNode path, File found) throws IOException {
		//Assert.isTrue(path.getParent().getId().equals(found.getParents().get(0)), "update found mismatch in parent id");
		Drive driveService = provider.getInstance();
		FileContent mediaContent = new FileContent(path.getType().mimeType(), path.getContent().getFile());
		return driveService.files().update(found.getId(), null, mediaContent).execute().getId();
	}
	private String create(FileNode path) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setName(path.getName());
		log.info("uploading file '"+path.getName()+"'");
		if(path.getParent() != null) {
			log.info("creating under parent: "+path.getParent().getName()+"["+path.getParent().getId()+"]");
			fileMetadata.setParents(Collections.singletonList(path.getParent().getId()));
		}
		java.io.File filePath = path.getContent().getFile();
		FileContent mediaContent = new FileContent(path.getType().mimeType(), filePath);

		Drive driveService = provider.getInstance();
		File file = driveService.files().create(fileMetadata, mediaContent)
				.setFields("id, parents").execute();
		return file.getId();
	}
	/**
	 * @deprecated
	 */
	@Override
	public FileNode listFiles() {
		FileNode root = new FileNode();
		List<File> files = getLatestVersion(FILE_QUERY);
		root.setName(GROOT);
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
		File found;
		try {
			found = getFileByName(path);
		} catch (IOException e1) {
			throw new FileIOException("delete execution failed", e1);
		}
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
	@Override
	public FileNode getById(String id) {
		Drive service = provider.getInstance();
		File meta;
		try {
			meta = service.files().get(id).setFields(FIELDS).execute();
			log.debug("meta found: "+meta);
			if(MimePart.GDOC.mimeType().equals(meta.getMimeType())) {
				return list(meta);
			}
			
			return download(meta);
			
		} catch (IOException e) {
			throw new FileIOException("Not found - "+id, e);
		}
		
	}
	private FileNode list(File dir) {
		List<File> childs = getLatestVersion(CHILD_QUERY.replaceFirst(NAME_PARAM, dir.getId()));
		FileNode root = newNode(dir, null);
		for(File child : childs) {
			root.getChilds().add(newNode(child, null));
		}
		return root;
	}
	@Override
	public FileNode list(String dirName) throws FileNotFoundException {
		try {
			File dir = getDirByName(dirName);
			if(dir == NODIR)
				throw new FileNotFoundException(dirName);

			return list(dir);
			
		} catch (IOException e) {
			throw new FileNotFoundException(dirName);
		}
	}
	
}
