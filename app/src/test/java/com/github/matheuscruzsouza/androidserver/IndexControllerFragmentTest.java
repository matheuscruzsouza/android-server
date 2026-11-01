package com.github.matheuscruzsouza.androidserver;

import org.junit.Test;
import static org.junit.Assert.*;

public class IndexControllerFragmentTest {
    @Test
    public void testBytesFormatting() {
        long bytes = 1048576; // 1 MB
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        String formatted = String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
        assertEquals("1.0 MB", formatted);
    }
}
