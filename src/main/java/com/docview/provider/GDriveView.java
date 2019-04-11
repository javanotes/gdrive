package com.docview.provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.docview.DocView;
import com.docview.ServiceProvider;
import com.docview.dto.FileNode;
import com.docview.dto.Mime;
import com.docview.utils.FileIOException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
class GDriveView implements DocView {
	private static Logger log = LoggerFactory.getLogger(GDriveView.class.getSimpleName());
	
	private static final String FIELDS = "nextPageToken, files(id, name, mimeType, size, modifiedTime, createdTime, owners, parents, permissions, version, webContentLink)";
	private static final int DEFAULT_PAGES = 10;
	private static final String ROOT = "My Drive";
	
	private static final String NAME_PARAM = "@param@";
	private static final String NAME_DIR_QUERY = "name = '" + NAME_PARAM + "' and mimeType = '"+Mime.GDIR.mimeType()+"'";
	private static final String DIR_QUERY = "mimeType = '"+Mime.GDIR.mimeType()+"'";
	private static final String FILE_QUERY = "mimeType != '"+Mime.GDIR.mimeType()+"'";
	private static final String NAMED_FILE_QUERY = "name = '" + NAME_PARAM + "'  and "+FILE_QUERY;
	private static final String CHILD_QUERY = "'"+NAME_PARAM+"' in parents";
	@Autowired
	ServiceProvider provider;
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
		n.setName(f.getName());
		n.setType(Mime.valueOfMime(f.getMimeType()));
		n.getContent().setCreatedAt(f.getCreatedTime().getValue());
		n.getContent().setLastModifiedAt(f.getModifiedTime().getValue());
		n.getContent().setSize(f.getSize() == null ? 0 : f.getSize());
		if (f.getPermissionIds() != null) {
			n.setAccessControl(f.getPermissionIds().toString());
		}
		n.setDir(n.getType() == Mime.GDIR);
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
            	if(recursive && Mime.GDIR.mimeType().equals(file.getMimeType())) {
            		walkTree(file.getId(), service, child, recursive);
            	}
            	else {
            		log.info("\tFile -- "+child);
            	}
                
            }
        }
	}
	
	/*@Autowired
	BeanFactory beanFactory;*/
	/*@Autowired
	GDriveConnect connect;*/
	
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
	private File getFile(String name) {
		return StreamSupport
		.stream(listGDrive(NAMED_FILE_QUERY.replaceFirst(NAME_PARAM, name)).spliterator(), false)
		.flatMap(List::stream).findFirst().orElse(NOFILE);
	}
	
	@Override
	public FileNode get(String path) throws FileNotFoundException {
		try {
			File found = getFile(path);
			if(found == NOFILE)
				throw new NoSuchElementException();
			Drive service = provider.getInstance();
			InputStream in = service.files().get(found.getId()).executeAsInputStream();
			FileNode file = newNode(found);
			file.getContent().setContentBytes(new InputStreamResource(in));
			file.setWebLink(found.getWebContentLink());
			return file;
		} 
		catch (NoSuchElementException e) {
			throw new FileNotFoundException(path);
		}
		catch (Exception e) {
			FileNotFoundException fne = new FileNotFoundException(path);
			fne.initCause(e);
			throw fne;
		}
		
	}

	@Override
	public String put(FileNode path) {
		try {
			File found = getFile(path.getName());
			if(found == NOFILE) {
				return create(path);
			}
			else {
				return update(path, found.getId());
			}
		} 
		catch (IOException e) {
			throw new FileIOException("upload to drive failed", e);
		}

		
	}
	private String update(FileNode path, String id) throws IOException {
		Drive driveService = provider.getInstance();
		FileContent mediaContent = new FileContent(path.getType().mimeType(), path.getContent().getContentBytes().getFile());
		return driveService.files().update(id, null, mediaContent).execute().getId();
	}
	private String create(FileNode path) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setName(path.getName());
		java.io.File filePath = path.getContent().getContentBytes().getFile();

		FileContent mediaContent = new FileContent(path.getType().mimeType(), filePath);

		Drive driveService = provider.getInstance();
		File file;

		file = driveService.files().create(fileMetadata, mediaContent).setFields("id").execute();
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
	public boolean delete(FileNode path) {
		File found = getFile(path.getName());
		if(found == NOFILE)
			return false;
		else {
			
		}
		return true;
	}
	
	/** Downloads a file using either resumable or direct media download. */
	  /*private void downloadFile(boolean useDirectDownload, File uploadedFile)
	      throws IOException {
	    // create parent directory (if necessary)
		  Drive service = beanFactory.getBean(Drive.class);
		  service.files().g
	    java.io.File parentDir = new java.io.File(DIR_FOR_DOWNLOADS);
	    if (!parentDir.exists() && !parentDir.mkdirs()) {
	      throw new IOException("Unable to create parent directory");
	    }
	    OutputStream out = new FileOutputStream(new java.io.File(parentDir, uploadedFile.getTitle()));
	    
	    MediaHttpDownloader downloader =
	        new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());
	    downloader.setDirectDownloadEnabled(useDirectDownload);
	    downloader.setProgressListener(new FileDownloadProgressListener());
	    downloader.download(new GenericUrl(uploadedFile.getDownloadUrl()), out);
	}*/

}
