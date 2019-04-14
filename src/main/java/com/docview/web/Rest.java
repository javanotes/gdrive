package com.docview.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docview.DocView;
import com.docview.dto.FileNode;
import com.docview.dto.ui.DirList;
import com.docview.dto.ui.FileId;
import com.docview.utils.FileIOException;

@RestController
@RequestMapping("/api")
public class Rest {
	private static final Logger log = LoggerFactory.getLogger("Rest");
	@Autowired
	DocView docView;
	
	@Value("${file.upload-dir}")
    private String uploadDir;
    private Path fileStorageLocation;
    
    @PostConstruct
    private void init() {
        try {
        	fileStorageLocation = Files.createTempDirectory(uploadDir);
        	fileStorageLocation.toFile().deleteOnExit();
        	
        } catch (Exception ex) {
            throw new BeanCreationException("Could not create the directory where the uploaded files will be stored", ex);
        }
    }
    
    private File resolve(MultipartFile file) {
        // Normalize file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Check if the file's name contains invalid characters
            if(fileName.contains("..")) {
                throw new IOException("Filename contains invalid path sequence " + fileName);
            }

            // Copy file to the target location (Replacing existing file with the same name)
            Path targetLocation = fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toFile();
            
        } catch (IOException ex) {
            throw new FileIOException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }
	
	@GetMapping("/files")
	public DirList listRoot() {
		FileNode node = docView.listRoot();
		DirList dir = new DirList();
		dir.setName(DocView.GROOT);
		dir.setId(node.getId());
		for(FileNode child: node.getChilds()) {
			if(child.isDir()) {
				dir.getDirs().add(new FileId(child.getName(), child.getId()));
			}
			else {
				dir.getFiles().add(new FileId(child.getName(), child.getId()));
			}
		}
		
		return dir;
	}
	
	@GetMapping("/files/{id}")
	public DirList listDir(@PathVariable("id") String id)  {
		FileNode node;
		try {
			node = docView.getById(id);
		} catch (FileIOException e) {
			throw new ResourceNotFound(e.getMessage(), e);
		}
		DirList path = new DirList();
		path.setName(node.getName());
		path.setId(node.getId());
		for(FileNode child: node.getChilds()) {
			if(child.isDir()) {
				path.getDirs().add(new FileId(child.getName(), child.getId()));
			}
			else {
				path.getFiles().add(new FileId(child.getName(), child.getId()));
			}
		}
		return path;
	}

	@GetMapping("/files/file/search/{path}")
	public ResponseEntity<Resource> getFile(@PathVariable("path") String file) {
		try {
			FileNode node = docView.get(file);
			return download(node);
		} catch (FileNotFoundException e) {
			throw new ResourceNotFound(e.getMessage(), e);
		}
	}
	
	private ResponseEntity<Resource> download(FileNode node) throws FileNotFoundException {
		Resource resource = node.getContent();
        if(resource == null)
        	throw new FileNotFoundException(node.getName());
        
        String contentType = node.getType().mimeType();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
	}
	@GetMapping("/files/file/{path}")
	public ResponseEntity<Resource> getFileById(@PathVariable("path") String id) {
		try {
			FileNode node = docView.getById(id);
			return download(node);
		} catch (FileNotFoundException e) {
			throw new ResourceNotFound(e.getMessage(), e);
		}
	}
	@PostMapping("/files/file")
	public void putFile(@RequestParam(name = "path", required = false) String path, @RequestParam("file") MultipartFile file) {
		FileNode node = StringUtils.hasText(path) ? new FileNode(path, resolve(file)) : new FileNode(resolve(file));
		String id = docView.put(node);
		log.info("File uploaded: "+id);
	}
	
	@PatchMapping("/files/file/{id}")
	public void updateFile(@PathVariable(name = "id", required = true) String id, @RequestParam("file") MultipartFile file) {
		FileNode node = new FileNode(resolve(file));
		node.setId(id);
		docView.put(node);
		log.info("File updated: "+id);
	}
	
}
