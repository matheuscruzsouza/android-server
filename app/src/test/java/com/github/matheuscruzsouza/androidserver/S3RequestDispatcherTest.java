package com.github.matheuscruzsouza.androidserver.s3;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class S3RequestDispatcherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File rootDir;
    private StorageManager storageManager;
    private IamService iamService;
    private S3RequestDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        rootDir = tempFolder.newFolder("s4_storage");
        storageManager = new StorageManager(null) {
            @Override
            public File getRootDir() {
                return rootDir;
            }
        };

        iamService = new IamService() {
            @Override
            public IamUser authenticate(IHTTPSession session) {
                if (session == null || session.getHeaders() == null) return null;
                String auth = session.getHeaders().get("authorization");
                if ("Bearer valid-token".equals(auth)) {
                    IamUser user = new IamUser();
                    user.setUsername("testuser");
                    user.setAccessKey("AKTEST");
                    user.setPolicy("Admin");
                    user.setEnabled(true);
                    return user;
                }
                return null;
            }

            @Override
            public boolean canPerformInBucket(IamUser user, String action, String bucketName, String userDefaultBucket) {
                if (user == null || !user.isEnabled()) return false;
                return "Admin".equalsIgnoreCase(user.getPolicy());
            }
        };

        dispatcher = new S3RequestDispatcher(storageManager, iamService);
    }

    private IHTTPSession createMockSession(NanoHTTPD.Method method, String uri, Map<String, String> headers) {
        return new MockSession(method, uri, headers);
    }

    @Test
    public void testIsS3RequestRoutingDistinction() {
        // Reservadas do sistema
        assertFalse(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/", null)));
        assertFalse(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/web", null)));
        assertFalse(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/web/files", null)));
        assertFalse(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/api/iam/users", null)));
        assertFalse(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/static/css/style.css", null)));
        assertFalse(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/swagger-ui.html", null)));

        // S3 nativo na raiz
        assertTrue(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/mybucket", null)));
        assertTrue(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/mybucket/file.txt", null)));

        // S3 alias legado
        assertTrue(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/s3", null)));
        assertTrue(dispatcher.isS3Request(createMockSession(NanoHTTPD.Method.GET, "/s3/mybucket", null)));
    }

    @Test
    public void testHeadBucketUnauthenticatedIsForbiddenByDefault() {
        storageManager.createBucket("secure-bucket");

        IHTTPSession unauthSession = createMockSession(NanoHTTPD.Method.HEAD, "/secure-bucket", new HashMap<>());
        Response res = dispatcher.dispatch(unauthSession);

        assertNotNull(res);
        assertEquals(Response.Status.FORBIDDEN.getRequestStatus(), res.getStatus().getRequestStatus());
    }

    @Test
    public void testHeadBucketAuthenticatedSuccessAndNotFound() {
        storageManager.createBucket("existing-bucket");

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("authorization", "Bearer valid-token");

        // Bucket existe
        IHTTPSession sessionSuccess = createMockSession(NanoHTTPD.Method.HEAD, "/existing-bucket", authHeaders);
        Response resSuccess = dispatcher.dispatch(sessionSuccess);
        assertNotNull(resSuccess);
        assertEquals(Response.Status.OK.getRequestStatus(), resSuccess.getStatus().getRequestStatus());
        assertEquals("us-east-1", resSuccess.getHeader("x-amz-bucket-region"));

        // Bucket não existe -> 404 NoSuchBucket
        IHTTPSession sessionNotFound = createMockSession(NanoHTTPD.Method.HEAD, "/unknown-bucket", authHeaders);
        Response resNotFound = dispatcher.dispatch(sessionNotFound);
        assertNotNull(resNotFound);
        assertEquals(Response.Status.NOT_FOUND.getRequestStatus(), resNotFound.getStatus().getRequestStatus());
    }

    @Test
    public void testHeadObjectMetadata() throws Exception {
        storageManager.createBucket("databucket");
        File file = storageManager.resolveFile("databucket", "docs/test.txt");
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Hello S3 from S4 Server!");
        }

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("authorization", "Bearer valid-token");

        IHTTPSession session = createMockSession(NanoHTTPD.Method.HEAD, "/databucket/docs/test.txt", authHeaders);
        Response res = dispatcher.dispatch(session);

        assertNotNull(res);
        assertEquals(Response.Status.OK.getRequestStatus(), res.getStatus().getRequestStatus());
        assertEquals(String.valueOf(file.length()), res.getHeader("Content-Length"));
        assertNotNull(res.getHeader("ETag"));
        assertNotNull(res.getHeader("Last-Modified"));
        assertEquals("bytes", res.getHeader("Accept-Ranges"));
    }

    @Test
    public void testListObjectsV2PaginationWithContinuationToken() throws Exception {
        storageManager.createBucket("pagebucket");
        for (int i = 1; i <= 5; i++) {
            File f = storageManager.resolveFile("pagebucket", "file" + i + ".txt");
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                w.write("data " + i);
            }
        }

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("authorization", "Bearer valid-token");

        // 1. Primeira página com max-keys=2
        Map<String, String> parms = new HashMap<>();
        parms.put("list-type", "2");
        parms.put("max-keys", "2");

        MockSession page1Session = new MockSession(NanoHTTPD.Method.GET, "/pagebucket", authHeaders, parms);
        Response page1Res = dispatcher.dispatch(page1Session);

        assertNotNull(page1Res);
        assertEquals(Response.Status.OK.getRequestStatus(), page1Res.getStatus().getRequestStatus());

        // Extrai o NextContinuationToken da resposta simulando cliente S3
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        page1Res.send(out);
        String xml1 = out.toString();

        assertTrue(xml1.contains("<IsTruncated>true</IsTruncated>"));
        assertTrue(xml1.contains("<Key>file1.txt</Key>"));
        assertTrue(xml1.contains("<Key>file2.txt</Key>"));
        assertFalse(xml1.contains("<Key>file3.txt</Key>"));
        assertTrue(xml1.contains("<NextContinuationToken>"));

        int tokenStart = xml1.indexOf("<NextContinuationToken>") + "<NextContinuationToken>".length();
        int tokenEnd = xml1.indexOf("</NextContinuationToken>");
        String nextToken = xml1.substring(tokenStart, tokenEnd);

        // 2. Segunda página usando o ContinuationToken retornado
        Map<String, String> parms2 = new HashMap<>();
        parms2.put("list-type", "2");
        parms2.put("max-keys", "2");
        parms2.put("continuation-token", nextToken);

        MockSession page2Session = new MockSession(NanoHTTPD.Method.GET, "/pagebucket", authHeaders, parms2);
        Response page2Res = dispatcher.dispatch(page2Session);

        java.io.ByteArrayOutputStream out2 = new java.io.ByteArrayOutputStream();
        page2Res.send(out2);
        String xml2 = out2.toString();

        assertTrue(xml2.contains("<IsTruncated>true</IsTruncated>"));
        assertTrue(xml2.contains("<Key>file3.txt</Key>"));
        assertTrue(xml2.contains("<Key>file4.txt</Key>"));
        assertFalse(xml2.contains("<Key>file1.txt</Key>"));
    }

    @Test
    public void testCompleteS3CrudLifecycleOnRootDispatcher() throws Exception {
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("authorization", "Bearer valid-token");

        // 1. CreateBucket PUT /crudbucket
        MockSession createSession = new MockSession(NanoHTTPD.Method.PUT, "/crudbucket", authHeaders);
        Response createRes = dispatcher.dispatch(createSession);
        assertNotNull(createRes);
        assertEquals(Response.Status.OK.getRequestStatus(), createRes.getStatus().getRequestStatus());
        assertEquals("/crudbucket", createRes.getHeader("Location"));
        assertTrue(storageManager.bucketExists("crudbucket"));

        // 2. PutObject PUT /crudbucket/sample.txt
        byte[] payload = "Streaming S3 data directly to disk without memory buffer".getBytes();
        MockSession putSession = new MockSession(NanoHTTPD.Method.PUT, "/crudbucket/sample.txt", authHeaders, new HashMap<>(), new java.io.ByteArrayInputStream(payload), (long) payload.length);
        Response putRes = dispatcher.dispatch(putSession);
        assertNotNull(putRes);
        assertEquals(Response.Status.OK.getRequestStatus(), putRes.getStatus().getRequestStatus());
        assertNotNull(putRes.getHeader("ETag"));

        // 3. GetObject GET /crudbucket/sample.txt
        MockSession getSession = new MockSession(NanoHTTPD.Method.GET, "/crudbucket/sample.txt", authHeaders);
        Response getRes = dispatcher.dispatch(getSession);
        assertNotNull(getRes);
        assertEquals(Response.Status.OK.getRequestStatus(), getRes.getStatus().getRequestStatus());
        assertEquals(putRes.getHeader("ETag"), getRes.getHeader("ETag"));

        // 4. DeleteObject DELETE /crudbucket/sample.txt -> 204 No Content
        MockSession delObjSession = new MockSession(NanoHTTPD.Method.DELETE, "/crudbucket/sample.txt", authHeaders);
        Response delObjRes = dispatcher.dispatch(delObjSession);
        assertNotNull(delObjRes);
        assertEquals(Response.Status.NO_CONTENT.getRequestStatus(), delObjRes.getStatus().getRequestStatus());

        // 5. DeleteBucket DELETE /crudbucket -> 204 No Content
        MockSession delBucketSession = new MockSession(NanoHTTPD.Method.DELETE, "/crudbucket", authHeaders);
        Response delBucketRes = dispatcher.dispatch(delBucketSession);
        assertNotNull(delBucketRes);
        assertEquals(Response.Status.NO_CONTENT.getRequestStatus(), delBucketRes.getStatus().getRequestStatus());
        assertFalse(storageManager.bucketExists("crudbucket"));
    }

    @Test
    public void testMultipartUploadLifecycle() throws Exception {
        storageManager.createBucket("mpbucket");
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("authorization", "Bearer valid-token");

        // 1. InitiateMultipartUpload: POST /mpbucket/bigfile.bin?uploads
        Map<String, String> initParms = new HashMap<>();
        initParms.put("uploads", "");
        MockSession initSession = new MockSession(NanoHTTPD.Method.POST, "/mpbucket/bigfile.bin", authHeaders, initParms);
        Response initRes = dispatcher.dispatch(initSession);
        assertNotNull(initRes);
        assertEquals(Response.Status.OK.getRequestStatus(), initRes.getStatus().getRequestStatus());

        java.io.ByteArrayOutputStream initOut = new java.io.ByteArrayOutputStream();
        initRes.send(initOut);
        String initXml = initOut.toString();
        assertTrue(initXml.contains("<UploadId>"));

        int idStart = initXml.indexOf("<UploadId>") + "<UploadId>".length();
        int idEnd = initXml.indexOf("</UploadId>");
        String uploadId = initXml.substring(idStart, idEnd);

        // 2. UploadPart 1: PUT /mpbucket/bigfile.bin?uploadId=...&partNumber=1
        byte[] part1 = "Part 1 chunk content; ".getBytes();
        Map<String, String> p1Parms = new HashMap<>();
        p1Parms.put("uploadId", uploadId);
        p1Parms.put("partNumber", "1");
        MockSession p1Session = new MockSession(NanoHTTPD.Method.PUT, "/mpbucket/bigfile.bin", authHeaders, p1Parms, new java.io.ByteArrayInputStream(part1), (long) part1.length);
        Response p1Res = dispatcher.dispatch(p1Session);
        assertNotNull(p1Res);
        assertEquals(Response.Status.OK.getRequestStatus(), p1Res.getStatus().getRequestStatus());
        assertNotNull(p1Res.getHeader("ETag"));

        // 3. UploadPart 2: PUT /mpbucket/bigfile.bin?uploadId=...&partNumber=2
        byte[] part2 = "Part 2 second chunk content.".getBytes();
        Map<String, String> p2Parms = new HashMap<>();
        p2Parms.put("uploadId", uploadId);
        p2Parms.put("partNumber", "2");
        MockSession p2Session = new MockSession(NanoHTTPD.Method.PUT, "/mpbucket/bigfile.bin", authHeaders, p2Parms, new java.io.ByteArrayInputStream(part2), (long) part2.length);
        Response p2Res = dispatcher.dispatch(p2Session);
        assertNotNull(p2Res);
        assertEquals(Response.Status.OK.getRequestStatus(), p2Res.getStatus().getRequestStatus());
        assertNotNull(p2Res.getHeader("ETag"));

        // 4. CompleteMultipartUpload: POST /mpbucket/bigfile.bin?uploadId=...
        Map<String, String> compParms = new HashMap<>();
        compParms.put("uploadId", uploadId);
        MockSession compSession = new MockSession(NanoHTTPD.Method.POST, "/mpbucket/bigfile.bin", authHeaders, compParms);
        Response compRes = dispatcher.dispatch(compSession);
        assertNotNull(compRes);
        assertEquals(Response.Status.OK.getRequestStatus(), compRes.getStatus().getRequestStatus());

        java.io.ByteArrayOutputStream compOut = new java.io.ByteArrayOutputStream();
        compRes.send(compOut);
        String compXml = compOut.toString();
        assertTrue(compXml.contains("<CompleteMultipartUploadResult"));
        assertTrue(compXml.contains("<ETag>"));

        // 5. Verifica se o arquivo final foi montado corretamente com todas as partes
        File destFile = storageManager.resolveFile("mpbucket", "bigfile.bin");
        assertTrue(destFile.exists());
        assertEquals(part1.length + part2.length, destFile.length());
    }

    @Test
    public void testDeleteObjectsBulk() throws Exception {
        storageManager.createBucket("bulkbucket");
        File f1 = storageManager.resolveFile("bulkbucket", "item1.txt");
        File f2 = storageManager.resolveFile("bulkbucket", "item2.txt");
        f1.createNewFile();
        f2.createNewFile();

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("authorization", "Bearer valid-token");

        Map<String, String> parms = new HashMap<>();
        parms.put("delete", "");

        String xmlPayload = "<Delete><Object><Key>item1.txt</Key></Object><Object><Key>item2.txt</Key></Object></Delete>";
        Map<String, String> files = new HashMap<>();
        files.put("postData", xmlPayload);

        MockSession delSession = new MockSession(NanoHTTPD.Method.POST, "/bulkbucket", authHeaders, parms) {
            @Override
            public void parseBody(Map<String, String> output) {
                output.putAll(files);
            }
        };

        Response res = dispatcher.dispatch(delSession);
        assertNotNull(res);
        assertEquals(Response.Status.OK.getRequestStatus(), res.getStatus().getRequestStatus());

        assertFalse(f1.exists());
        assertFalse(f2.exists());
    }

    // Mock simples de IHTTPSession para testes unitários isolados
    private static class MockSession implements IHTTPSession {
        private final NanoHTTPD.Method method;
        private final String uri;
        private final Map<String, String> headers;
        private final Map<String, String> parms;
        private final java.io.InputStream inputStream;

        public MockSession(NanoHTTPD.Method method, String uri, Map<String, String> headers) {
            this(method, uri, headers, new HashMap<>());
        }

        public MockSession(NanoHTTPD.Method method, String uri, Map<String, String> headers, Map<String, String> parms) {
            this(method, uri, headers, parms, null, 0);
        }

        public MockSession(NanoHTTPD.Method method, String uri, Map<String, String> headers, Map<String, String> parms, java.io.InputStream inputStream, long contentLength) {
            this.method = method;
            this.uri = uri;
            this.headers = headers != null ? headers : new HashMap<>();
            if (contentLength > 0) {
                this.headers.put("content-length", String.valueOf(contentLength));
            }
            this.parms = parms != null ? parms : new HashMap<>();
            this.inputStream = inputStream;
        }

        @Override public NanoHTTPD.Method getMethod() { return method; }
        @Override public String getUri() { return uri; }
        @Override public Map<String, String> getHeaders() { return headers; }
        @Override public void execute() {}
        @Override public fi.iki.elonen.NanoHTTPD.CookieHandler getCookies() { return null; }
        @Override public Map<String, String> getParms() { return parms; }
        @Override public String getQueryParameterString() { return ""; }
        @Override public java.io.InputStream getInputStream() { return inputStream; }
        @Override public void parseBody(Map<String, String> files) {}
        @Override public String getRemoteIpAddress() { return "127.0.0.1"; }
        @Override public String getRemoteHostName() { return "localhost"; }
    }
}
