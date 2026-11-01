package com.github.matheuscruzsouza.androidserver;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class S3ListObjectsV2LogicTest {

    @Test
    public void testPrefixAndDelimiterSimulation() {
        // Simulação de chaves no bucket
        List<String> keys = new ArrayList<>();
        keys.add("photos/2026/img1.jpg");
        keys.add("photos/2026/img2.jpg");
        keys.add("photos/notes.txt");
        keys.add("document.pdf");
        keys.add("root-file.txt");

        String pfx = "photos/";
        String delim = "/";

        TreeSet<String> commonPrefixes = new TreeSet<>();
        List<String> contents = new ArrayList<>();

        for (String rel : keys) {
            if (!pfx.isEmpty() && !rel.startsWith(pfx)) {
                continue;
            }

            if (!delim.isEmpty()) {
                String sub = rel.substring(pfx.length());
                int idx = sub.indexOf(delim);
                if (idx >= 0) {
                    String prefixGroup = pfx + sub.substring(0, idx + delim.length());
                    commonPrefixes.add(prefixGroup);
                    continue;
                }
            }

            contents.add(rel);
        }

        // Deve conter photos/notes.txt como objeto direto
        assertEquals(1, contents.size());
        assertEquals("photos/notes.txt", contents.get(0));

        // Deve conter photos/2026/ como prefixo comum agrupado
        assertEquals(1, commonPrefixes.size());
        assertTrue(commonPrefixes.contains("photos/2026/"));
    }

    @Test
    public void testRootDelimiterSimulation() {
        List<String> keys = new ArrayList<>();
        keys.add("photos/2026/img1.jpg");
        keys.add("docs/manual.pdf");
        keys.add("readme.txt");

        String pfx = "";
        String delim = "/";

        TreeSet<String> commonPrefixes = new TreeSet<>();
        List<String> contents = new ArrayList<>();

        for (String rel : keys) {
            if (!pfx.isEmpty() && !rel.startsWith(pfx)) {
                continue;
            }

            if (!delim.isEmpty()) {
                String sub = rel.substring(pfx.length());
                int idx = sub.indexOf(delim);
                if (idx >= 0) {
                    String prefixGroup = pfx + sub.substring(0, idx + delim.length());
                    commonPrefixes.add(prefixGroup);
                    continue;
                }
            }

            contents.add(rel);
        }

        assertEquals(1, contents.size());
        assertEquals("readme.txt", contents.get(0));

        assertEquals(2, commonPrefixes.size());
        assertTrue(commonPrefixes.contains("photos/"));
        assertTrue(commonPrefixes.contains("docs/"));
    }
}
