package com.by122006.zircon.testing;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public abstract class CompilerTestCase {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    protected CompilerTestSupport.Result compile(String source, String... plugins) throws Exception {
        return CompilerTestSupport.compile(temporary.newFolder(), "CacheCase", source, plugins);
    }

    protected void assertRuns(String expected, String source, boolean allPlugins) throws Exception {
        try (CompilerTestSupport.Result result = compile(source,
                allPlugins ? CompilerTestSupport.ALL : CompilerTestSupport.STRING)) {
            assertEquals(expected, result.assertCompiled().run("CacheCase", "run"));
        }
    }
}
