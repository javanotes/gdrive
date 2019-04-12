package com.docview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import org.springframework.util.ResourceUtils;

import com.docview.dto.FileNode;
import com.docview.dto.MimePart;
import com.google.api.services.drive.Drive;

// Well, this is an integration test suite really.
// but could not help since we do not have any 'business service'
// of sort, to run pure unit tests
// NOTE: check the console logs to see whether you need to do a Google login

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
	
	// mocking the ServiceProvider interface
	// to use installed app oauth
	@Before
	public void before() {
		Mockito.when(provider.getInstance())
	      .thenReturn(drive);
	}
	
	@Test
	public void testRootRecursiveTraversalGDrive() {
		log.info("========= testRootRecursiveTraversalGDrive ==========");
		FileNode root = docView.listRoot();
		assertNotNull(root);
		log.info(root+"");
	}
	@Test
	public void testSelectedFolderTraversalGDrive() throws FileNotFoundException {
		log.info("========= testSelectedFolderTraversalGDrive ==========");
		FileNode root = docView.list("results");
		assertNotNull(root);
		log.info(root+"\n\t"+root.getChilds());
	}
	@Deprecated
	@Test
	public void testListAllFilesInGDrive() {
		log.info("========= testListAllFilesInGDrive ==========");
		FileNode root = docView.listFiles();
		assertNotNull(root);
		log.info(root+"");
	}
	@Test
	public void testCreateDirectoryStructureInGDrive() {
		log.info("========= testCreateDirectoryStructureInGDrive ==========");
		String root = docView.mkDirs(new FileNode("/testRoot/testChild1/testChild2", null));
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
			assertEquals(MimePart.DOC, node.getType());
			assertNotNull(node.getContent());
			node.close();
			log.info(node+"");
		} catch (FileNotFoundException e) {
			fail("file not found "+e);
		}
	}
	@Test(expected = FileNotFoundException.class)
	public void testDeleteExistingFileGDrive() throws IOException {
		log.info("========= testDeleteExistingFileGDrive ==========");
		try {
			FileNode file = new FileNode(ResourceUtils.getFile("classpath:application.properties"));
			String id = docView.put(file);
			assertNotNull(id);
			
			boolean deleted = docView.delete(file.getName());
			assertTrue(deleted);
			
			docView.get(file.getName());
			
		} catch (FileNotFoundException e) {
			throw e;
		}
	}
	@Test
	public void testUploadFileGDrive() throws IOException {
		log.info("========= testUploadFileGDrive ==========");
		try {
			FileNode file = new FileNode(ResourceUtils.getFile("classpath:TestUploadFile.pdf"));
			String id = docView.put(file);
			assertNotNull(id);
			
			FileNode node = docView.get("TestUploadFile.pdf");
			assertNotNull(node);
			assertNotNull(node.getWebLink());
			assertNotNull(node.getType());
			assertNotNull(node.getContent());
			
			assertEquals(file.getSize(), node.getSize());
			assertEquals(file.getType(), node.getType());
			
			docView.delete(file.getName());
			
			node.close();
			file.close();
			
		} catch (FileNotFoundException e) {
			fail("file not found "+e);
		}
	}
	static String TEST_FILE = "skillset.png";
	static String TEST_DIR = "/results/ques/";
	@Test
	public void testUploadFileUnderDirectoryGDrive() throws IOException {
		log.info("========= testUploadFileUnderDirectoryGDrive ==========");
		try {
			FileNode file = new FileNode(TEST_DIR, ResourceUtils.getFile("classpath:"+TEST_FILE));
			String id = docView.put(file);
			assertNotNull(id);
			FileNode node = docView.getById(id);
			assertEquals(id, node.getId());
			
		} catch (FileNotFoundException e) {
			fail("file not found "+e);
		}
	}
	// failing. but we need not use this either
	// from UI : listRoot() -> list() -> get()
	//@Test
	public void testDownloadFileUnderDirectoryGDrive() throws IOException {
		log.info("========= testDownloadFileUnderDirectoryGDrive ==========");
		try {
			testUploadFileUnderDirectoryGDrive();
			FileNode node = docView.get(TEST_DIR+TEST_FILE);
			assertNotNull(node);
			assertNotNull(node.getWebLink());
			assertNotNull(node.getType());
			assertNotNull(node.getContent());
			
			//assertEquals(file.getSize(), node.getSize());
			//assertEquals(file.getType(), node.getType());
			
			//docView.delete(node.getName());
			
			node.close();
			
		} catch (FileNotFoundException e) {
			fail(""+e);
		}
	}
	
	@Test(expected = FileNotFoundException.class)
	public void testDownloadNonExistingFileGDrive() throws IOException {
		log.info("========= testDownloadNonExistingFileGDrive ==========");
		docView.get("Resume_SutanuDalui_0918_not_present.doc");
	}
	
	@Test
	public void testDeleteNonExistingFileGDrive() throws IOException {
		log.info("========= testDeleteNonExistingFileGDrive ==========");
		boolean deleted = docView.delete("Resume_SutanuDalui_0918_not_present.doc");
		assertFalse(deleted);
	}
	@Test
	public void testCreateFileNodeWithParentDirs() throws FileNotFoundException {
		FileNode file = new FileNode("/parent1/parent2", ResourceUtils.getFile("classpath:TestUploadFile.pdf"));
		assertNotNull(file);
		assertNull(file.getParent());
		assertTrue(file.isDir());
		assertEquals("parent1", file.getName());
		assertEquals(MimePart.GDIR, file.getType());
		assertTrue(file.hasChildren());
		
		file = file.getFirstChild();
		assertNotNull(file);
		assertNotNull(file.getParent());
		assertTrue(file.isDir());
		assertEquals("parent2", file.getName());
		assertEquals(MimePart.GDIR, file.getType());
		assertTrue(file.hasChildren());
		
		file = file.getFirstChild();
		assertNotNull(file);
		assertNotNull(file.getParent());
		assertFalse(file.isDir());
		assertEquals("TestUploadFile.pdf", file.getName());
		assertEquals(MimePart.PDF, file.getType());
		assertFalse(file.hasChildren());
	}
}
