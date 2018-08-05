package gallerymine.backend.analyzer;

import static org.junit.Assert.*;

public class IgnorableFileAnayserTest {

    IgnorableFileAnayser analyser = new IgnorableFileAnayser();

    @org.junit.Test
    public void accepts() {
        assertTrue("System file was not ignored", analyser.accepts(".git"));
        assertTrue("Mac thumb file was not ignored", analyser.accepts(".DS_Store"));
        assertTrue("Text read.me file was not ignored", analyser.accepts("read.me"));
        assertTrue("Git readme.md file was not ignored", analyser.accepts("README.md"));
        assertTrue("Text file was not ignored", analyser.accepts("sample.txt"));
        assertFalse("JPEG file was ignored", analyser.accepts("sample.jpg"));
    }
}