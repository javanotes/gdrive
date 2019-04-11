package com.docview;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import com.docview.dto.FileNode;
import com.google.api.services.drive.Drive;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GDocViewTester {

	private static final Logger log = LoggerFactory.getLogger(GDocViewTester.class.getSimpleName());
	@Autowired
	DocView docView;
	@MockBean
	ServiceProvider provider;
	@Autowired
	Drive drive;
	@Before
	public void before() {
		Mockito.when(provider.getInstance())
	      .thenReturn(drive);
	}
	
	@Test
	public void testRootRecursiveTraversalGDrive() {
		log.info("========= testRootRecursiveTraversalGDrive ==========");
		FileNode root = docView.list("");
		assertNotNull(root);
		log.info(root+"");
	}
	@Test
	public void testSelectedFolderTraversalGDrive() {
		log.info("========= testSelectedFolderTraversalGDrive ==========");
		FileNode root = docView.list("Knowledge");
		assertNotNull(root);
		log.info(root+"");
	}
	@Test
	public void testListAllFilesInGDrive() {
		log.info("========= testListAllFilesInGDrive ==========");
		FileNode root = docView.listFiles();
		assertNotNull(root);
		log.info(root+"");
	}
	@Test
	public void testDownloadExistingFileGDrive() throws IOException {
		log.info("========= testDownloadValidFileGDrive ==========");
		try {
			FileNode node = docView.get("Resume_SutanuDalui_0918.doc");
			assertNotNull(node);
			assertNotNull(node.getWebLink());
			assertNotNull(node.getType());
			assertNotNull(node.getContent());
			assertNotNull(node.getContent().getContentBytes());
			node.getContent().close();
			log.info(node+"");
		} catch (FileNotFoundException e) {
			fail("file not found "+e);
		}
	}
	
	@Test(expected = FileNotFoundException.class)
	public void testDownloadInNonExistingFileGDrive() throws IOException {
		log.info("========= testDownloadInValidFileGDrive ==========");
		docView.get("Resume_SutanuDalui_0918_not_present.doc");
	}
}
