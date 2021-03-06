package com.sismics.music.rest;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.sismics.music.core.util.DirectoryUtil;
import com.sismics.util.filter.TokenBasedSecurityFilter;

/**
 * Exhaustive test of the import resource.
 * 
 * @author bgamard
 */
public class TestImportResource extends BaseJerseyTest {
    /**
     * Test the import resource.
     * youtube-dl is not available on Travis, can't be tested systematically.
     *
     * @throws Exception
     */
    @Test
    @Ignore // youtube-dl is not installed on Travis
    public void testImportResource() throws Exception {
        // Login users
        String adminAuthenticationToken = clientUtil.login("admin", "admin", false);
        
        // This test is destructive, copy the test music to a temporary directory
        Path sourceDir = Paths.get(getClass().getResource("/music/").toURI());
        File destDir = Files.createTempDir();
        FileUtils.copyDirectory(sourceDir.toFile(), destDir);
        destDir.deleteOnExit();
        
        // Admin adds a directory to the collection
        JsonObject json = target().path("/directory").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .put(Entity.form(new Form()
                        .param("location", destDir.toPath().toString())), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Admin import a new URL
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .put(Entity.form(new Form()
                        .param("url", "https://soundcloud.com/monstercat/au5-follow-you-volant")
                        .param("quality", "128K")
                        .param("format", "mp3")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Admin lists imported files
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        JsonArray files = json.getJsonArray("files");
        Assert.assertEquals(0, files.size());
        
        // Admin checks import progression
        boolean stop = false;
        while (!stop) {
            json = target().path("/import/progress").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                    .get(JsonObject.class);
            JsonArray imports = json.getJsonArray("imports");
            Assert.assertEquals(1, imports.size());
            System.out.println(imports.getJsonObject(0));
            
            if (imports.getJsonObject(0).getString("status").equals("DONE")) {
                stop = true;
            }
            
            if (!stop) {
                // Admin lists imported files
                json = target().path("/import").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                        .get(JsonObject.class);
                files = json.getJsonArray("files");
                Assert.assertEquals(0, files.size());
            }
            
            Thread.sleep(200);
        }
        
        // Admin lists imported files
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        files = json.getJsonArray("files");
        Assert.assertEquals(1, files.size());
        Assert.assertEquals("Au5 - Follow You (feat Danyka Nadeau) (Volant Remix).mp3", files.getJsonObject(0).getString("file"));
        
        // Admin move the imported file to the main directory
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .post(Entity.form(new Form()
                        .param("file", "Au5 - Follow You (feat Danyka Nadeau) (Volant Remix).mp3")
                        .param("artist", "Au5")
                        .param("album_artist", "Remixer")
                        .param("album", "Unsorted")
                        .param("title", "Follow You (feat Danyka Nadeau)")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Admin cleanup imports
        json = target().path("/import/progress/cleanup").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .post(Entity.form(new Form()), JsonObject.class);
        
        // Wait for watching service to index our new music
        Thread.sleep(3000);
        
        // Admin import a new URL
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .put(Entity.form(new Form()
                        .param("url", "https://soundcloud.com/monstercat/au5-follow-you-volant")
                        .param("quality", "128K")
                        .param("format", "mp3")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));

        // Wait for the process to start
        Thread.sleep(1000);
        
        // Admin check import progession
        json = target().path("/import/progress").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        JsonArray imports = json.getJsonArray("imports");
        Assert.assertEquals(1, imports.size());
        Assert.assertEquals("INPROGRESS", imports.getJsonObject(0).getString("status"));
        
        // Admin kills the current import
        json = target().path("/import/progress/" + imports.getJsonObject(0).getString("id") + "/kill").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .post(Entity.form(new Form()), JsonObject.class);
        
        // Wait for the process to be killed
        Thread.sleep(3000);
        
        // Admin check import progession
        json = target().path("/import/progress").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        imports = json.getJsonArray("imports");
        Assert.assertEquals(1, imports.size());
        Assert.assertEquals("ERROR", imports.getJsonObject(0).getString("status"));
    }
    
    /**
     * Test the import resource (retry).
     * youtube-dl is not available on Travis, can't be tested systematically.
     *
     * @throws Exception
     */
    @Test
    @Ignore // youtube-dl is not installed on Travis
    public void testImportResourceRetry() throws Exception {
        // Login users
        String adminAuthenticationToken = clientUtil.login("admin", "admin", false);
        
        // Admin import a new URL
        JsonObject json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .put(Entity.form(new Form()
                        .param("url", "fakeurl")
                        .param("quality", "128K")
                        .param("format", "mp3")), JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Admin lists imported files
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        JsonArray files = json.getJsonArray("files");
        Assert.assertEquals(0, files.size());
        
        // Admin checks import progression
        json = target().path("/import/progress").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        JsonArray imports = json.getJsonArray("imports");
        Assert.assertEquals(1, imports.size());
        Assert.assertEquals("INPROGRESS", imports.getJsonObject(0).getString("status"));
        
        Thread.sleep(5000);
        
        // Admin checks import progression
        json = target().path("/import/progress").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        imports = json.getJsonArray("imports");
        Assert.assertEquals(1, imports.size());
        JsonObject imp = imports.getJsonObject(0);
        Assert.assertEquals("ERROR", imp.getString("status"));
        
        // Retry the failed import
        json = target().path("/import/progress/" + imp.getString("id") + "/retry").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .post(null, JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Admin checks import progression
        json = target().path("/import/progress").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        imports = json.getJsonArray("imports");
        Assert.assertEquals(1, imports.size());
        Assert.assertEquals("INPROGRESS", imports.getJsonObject(0).getString("status"));
        
        Thread.sleep(5000);
        
        // Admin checks import progression
        json = target().path("/import/progress").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        imports = json.getJsonArray("imports");
        Assert.assertEquals(1, imports.size());
        imp = imports.getJsonObject(0);
        Assert.assertEquals("ERROR", imp.getString("status"));
    }
    
    @Test
    @Ignore // youtube-dl is not installed on Travis
    public void testDependecies() throws Exception {
        // Login users
        String adminAuthenticationToken = clientUtil.login("admin", "admin", false);
        
        // Admin checks dependencies
        JsonObject json = target().path("/import/dependencies").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        System.out.println(json);
    }
    
    /**
     * Test the import resource (upload).
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("resource")
    public void testImportUpload() throws Exception {
        // Login users
        String adminAuthenticationToken = clientUtil.login("admin", "admin", false);
        
        // Admin import a ZIP
        try (InputStream is = Resources.getResource("music-album.zip").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "music-album.zip");
            JsonObject json = target()
                    .register(MultiPartFeature.class)
                    .path("/import/upload").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                    .put(Entity.entity(new FormDataMultiPart().bodyPart(streamDataBodyPart),
                            MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
            Assert.assertEquals("ok", json.getString("status"));
        }
        
        // Admin lists imported files
        JsonObject json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        JsonArray files = json.getJsonArray("files");
        Assert.assertEquals(3, files.size());
        
        // Admin import a single track
        try (InputStream is = Resources.getResource("music/Kevin MacLeod - Robot Brain/Robot Brain A.mp3").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "Robot Brain A.mp3");
            json = target()
                    .register(MultiPartFeature.class)
                    .path("/import/upload").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                    .put(Entity.entity(new FormDataMultiPart().bodyPart(streamDataBodyPart),
                            MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
            Assert.assertEquals("ok", json.getString("status"));
        }
        
        // Admin lists imported files
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        files = json.getJsonArray("files");
        Assert.assertEquals(4, files.size());
        
        // Admin import a non audio
        try (InputStream is = Resources.getResource("log4j.properties").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "log4j.properties");
            Response response = target()
                    .register(MultiPartFeature.class)
                    .path("/import/upload").request()
                    .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                    .put(Entity.entity(new FormDataMultiPart().bodyPart(streamDataBodyPart),
                            MediaType.MULTIPART_FORM_DATA_TYPE));
            Assert.assertEquals(Status.INTERNAL_SERVER_ERROR, Status.fromStatusCode(response.getStatus()));
            json = response.readEntity(JsonObject.class);
            Assert.assertEquals("ImportError", json.getString("type"));
            Assert.assertEquals("File not supported", json.getString("message"));
        }
        
        // Admin lists imported files
        json = target().path("/import").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminAuthenticationToken)
                .get(JsonObject.class);
        files = json.getJsonArray("files");
        Assert.assertEquals(4, files.size());
        
        // Cleanup imported files
        for (File file : DirectoryUtil.getImportAudioDirectory().listFiles()) {
            file.delete();
        }
        DirectoryUtil.getImportAudioDirectory().delete();
    }
}
