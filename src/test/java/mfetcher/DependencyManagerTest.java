package mfetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.transfer.TransferEvent;
import org.junit.*;
import static org.junit.Assert.*;

public class DependencyManagerTest {

    public final Path home = Paths.get(System.getProperty("user.home"));
    public final Path m2 = home.resolve(".m2/repository");

    public final Coord CIO = new Coord("commons-io", "commons-io", "1.2", "jar");
    public final Coord JUNIT = new Coord("junit", "junit", "4.11", "jar");
    public final Coord HTMLP = new Coord("org.htmlparser", "html-lexer", "2.1", "jar");
    public final Coord ASM5 = new Coord("org.ow2.asm", "asm", "5.0.1", "jar");

    @Test
    public void testFetch () throws IOException {
        Path tmp = Paths.get("target/test-repo");
        Files.createDirectories(tmp);
        DependencyManager dmgr = new DependencyManager(tmp, null, false, false) {
            @Override protected void onRepositoryEvent (String method, RepositoryEvent event) {
                System.out.println(method + " :: " + event);
            }
            @Override protected void onTransferEvent (String method, TransferEvent event) {
                System.out.println(method + " :: " + event);
            }
        };
        Map<Coord,Path> paths = dmgr.resolveDependencies(Arrays.asList(CIO));
        for (Map.Entry<Coord,Path> entry : paths.entrySet()) {
            // System.out.println(entry.getKey() + " -> " + entry.getValue());
            assertNotNull(entry.getValue());
            assertTrue(Files.exists(entry.getValue()));
        }
    }

    @Test
    public void testInvalidArtifact () {
        DependencyManager dmgr = new DependencyManager(m2, null, false, false);
        Map<Coord,Path> paths = dmgr.resolveDependencies(Arrays.asList(JUNIT, HTMLP, ASM5));
        // for (Map.Entry<Coord,Path> entry : paths.entrySet()) {
        //     System.out.println(entry.getKey() + " -> " + entry.getValue());
        // }
        assertNotNull(paths.get(JUNIT));
        assertNull(paths.get(HTMLP)); // missing
        assertNotNull(paths.get(ASM5));
    }
}
