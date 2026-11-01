package com.github.matheuscruzsouza.androidserver;

import com.github.matheuscruzsouza.nanospring.http.HttpStatus;
import com.github.matheuscruzsouza.nanospring.storage.ByteRangeHandler;
import com.github.matheuscruzsouza.nanospring.storage.FileResourceRegion;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

import static org.junit.Assert.*;

public class FileRangeSupportTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testByteRangeHandlerFullAndPartial() throws Exception {
        File sampleFile = tempFolder.newFile("video.mp4");
        byte[] content = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(sampleFile)) {
            fos.write(content);
        }

        // 1. Sem header Range -> 200 OK com conteúdo completo e Accept-Ranges: bytes
        IHTTPSession noRangeSession = mockSessionWithHeaders(Collections.emptyMap());
        Response fullRes = ByteRangeHandler.serveFile(noRangeSession, sampleFile, "video/mp4");
        assertEquals(200, fullRes.getStatus().getRequestStatus());
        assertEquals("bytes", fullRes.getHeader("Accept-Ranges"));
        assertEquals("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ", readResponseData(fullRes, content.length));

        // 2. Com Range bytes=10-15 -> 206 Partial Content ("ABCDEF")
        Map<String, String> rangeHeaders = new HashMap<>();
        rangeHeaders.put("range", "bytes=10-15");
        IHTTPSession partialSession = mockSessionWithHeaders(rangeHeaders);
        Response partialRes = ByteRangeHandler.serveFile(partialSession, sampleFile, "video/mp4");
        assertEquals(HttpStatus.PARTIAL_CONTENT, partialRes.getStatus());
        assertEquals("bytes", partialRes.getHeader("Accept-Ranges"));
        assertEquals("bytes 10-15/" + content.length, partialRes.getHeader("Content-Range"));
        assertEquals("ABCDEF", readResponseData(partialRes, 6));

        // 3. Usando FileResourceRegion diretamente com ByteRangeHandler.serveResource
        Map<String, String> openEndHeaders = new HashMap<>();
        openEndHeaders.put("range", "bytes=30-");
        IHTTPSession openEndSession = mockSessionWithHeaders(openEndHeaders);
        Response openEndRes = ByteRangeHandler.serveResource(openEndSession, new FileResourceRegion(sampleFile, "video/mp4"));
        assertEquals(HttpStatus.PARTIAL_CONTENT, openEndRes.getStatus());
        assertEquals("bytes 30-35/" + content.length, openEndRes.getHeader("Content-Range"));
        assertEquals("UVWXYZ", readResponseData(openEndRes, 6));

        // 4. Com Range inválido bytes=100-200 -> 416 Range Not Satisfiable
        Map<String, String> badRangeHeaders = new HashMap<>();
        badRangeHeaders.put("range", "bytes=100-200");
        IHTTPSession badSession = mockSessionWithHeaders(badRangeHeaders);
        Response badRes = ByteRangeHandler.serveFile(badSession, sampleFile, "video/mp4");
        assertEquals(416, badRes.getStatus().getRequestStatus());
        assertEquals("bytes */" + content.length, badRes.getHeader("Content-Range"));
    }

    private String readResponseData(Response res, int length) throws Exception {
        try (InputStream is = res.getData()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[length];
            int remaining = length;
            int r;
            while (remaining > 0 && (r = is.read(buf, 0, remaining)) != -1) {
                bos.write(buf, 0, r);
                remaining -= r;
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private IHTTPSession mockSessionWithHeaders(final Map<String, String> headers) {
        return new IHTTPSession() {
            @Override public void execute() {}
            @Override public NanoHTTPD.CookieHandler getCookies() { return null; }
            @Override public Map<String, String> getHeaders() { return headers; }
            @Override public InputStream getInputStream() { return null; }
            @Override public Method getMethod() { return Method.GET; }
            @Override public Map<String, String> getParms() { return Collections.emptyMap(); }
            @Override public Map<String, List<String>> getParameters() { return Collections.emptyMap(); }
            @Override public String getQueryParameterString() { return ""; }
            @Override public String getUri() { return "/"; }
            @Override public void parseBody(Map<String, String> files) {}
            @Override public String getRemoteIpAddress() { return "127.0.0.1"; }
            @Override public String getRemoteHostName() { return "localhost"; }
        };
    }
}
