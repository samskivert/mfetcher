package mfetcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.transfer.TransferEvent;
import org.junit.*;
import static org.junit.Assert.*;

public class DependencyManagerTest {

    public final Path home = Paths.get(System.getProperty("user.home"));
    public final Path m2 = home.resolve(".m2/repository");

    @Test
    public void testInvalidArtifact () {
        DependencyManager dmgr = new DependencyManager(m2, null, false, false);
        Map<Coord,Path> paths = dmgr.resolveDependencies(Arrays.asList(
            new Coord("junit", "junit", "4.11", "jar"),
            new Coord("org.htmlparser", "html-lexer", "2.1", "jar"),
            new Coord("org.ow2.asm", "asm", "5.0.1", "jar")));
        for (Map.Entry<Coord,Path> entry : paths.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }
}
