package mfetcher;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

public class DependencyManager {

    private static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/";
    private static final String PROP_CONNECT_TIMEOUT = "mfetcher.connect.timeout";
    private static final String PROP_REQUEST_TIMEOUT = "mfetcher.request.timeout";
    private static final String PROP_LOG = "mfetcher.log";

    private static final String propLog = System.getProperty(PROP_LOG, "quiet");
    private static final boolean debug = propLog.equals("debug");
    private static final boolean verbose = debug || propLog.equals("verbose");

    private final boolean forceRefresh;
    private final boolean offline;
    private final RepositorySystem system;
    private final MavenSettings settings;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repos;

    public DependencyManager (Path localRepoPath, List<String> repos,
                              boolean forceRefresh, boolean offline) {
        this.forceRefresh = forceRefresh;
        this.offline = offline;
        this.system = newRepositorySystem();
        this.settings = new MavenSettings();
        this.session = newRepositorySession(system, localRepoPath);

        final RepositoryPolicy policy = new RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        this.repos = new ArrayList<RemoteRepository>();
        if (repos == null) repos = Arrays.asList("central");
        for (String repo : repos) {
            if ("central".equals(repo)) this.repos.add(
                newRemoteRepository("central", MAVEN_CENTRAL_URL, policy));
            else this.repos.add(newRemoteRepository(null, repo, policy));
        }
    }

    /**
     * Resolves {@code coords} and their transitive dependencies, downloading artifacts from Maven
     * repositories as needed.
     *
     * @return a mapping from {@code Coord} to the local path to the artifact for all dependencies
     * in {@code coords} and their transitive dependencies. Any coords which were unable to be
     * resolved will be mapped to {@code null}. The returned map will iterate in the order the
     * dependencies were returned from Maven.
     */
    public Map<Coord,Path> resolveDependencies (List<Coord> coords) {
        CollectRequest req = new CollectRequest((Dependency)null, toDependencies(coords), repos);

        DependencyResult result;
        try {
            result = system.resolveDependencies(session, new DependencyRequest(req, null));
        } catch (DependencyResolutionException e) {
            result = e.getResult();
        }

        List<ArtifactResult> artifactResults = result.getArtifactResults();
        Map<Coord,Path> jars = new LinkedHashMap<Coord,Path>();
        for (ArtifactResult artifactResult : artifactResults) {
            // if this artifact result is a conflict loser, omit it
            if (artifactResult.getRequest().getDependencyNode().getData().get(
                ConflictResolver.NODE_DATA_WINNER) != null) continue;
            Artifact art = artifactResult.getArtifact();
            if (art == null) jars.put(toCoord(artifactResult.getRequest().getArtifact()), null);
            else jars.put(toCoord(art), art.getFile().toPath().toAbsolutePath());
        }
        return jars;
    }

    protected void onRepositoryEvent (String method, RepositoryEvent event) {
        if (verbose) System.out.println(method + " :: " + event);
    }

    protected void onTransferEvent (String method, TransferEvent event) {
        if (verbose) System.out.println(method + " :: " + event);
    }

    private static RepositorySystem newRepositorySystem () {
        // We're using DefaultServiceLocator rather than Guice/Sisu because it's lighter weight.
        // This method pulls together the necessary Aether components and plugins.
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable ex) {
                throw new RuntimeException("Service creation failed for type " + type.getName() +
                    " with impl " + impl, ex);
            }
        });

        locator.addService(org.eclipse.aether.spi.connector.RepositoryConnectorFactory.class,
                           org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.class);
        locator.addService(org.eclipse.aether.spi.connector.transport.TransporterFactory.class,
                           org.eclipse.aether.transport.http.HttpTransporterFactory.class);
        // locator.addService(org.eclipse.aether.spi.connector.transport.TransporterFactory.class,
        //                    org.eclipse.aether.transport.file.FileTransporterFactory.class);

        // Takari (support concurrent downloads)
        locator.setService(org.eclipse.aether.impl.SyncContextFactory.class,
                           LockingSyncContextFactory.class);
        locator.setService(org.eclipse.aether.spi.io.FileProcessor.class,
                           LockingFileProcessor.class);

        return locator.getService(RepositorySystem.class);
    }

    private RepositorySystemSession newRepositorySession (
        RepositorySystem system, Path localRepoPath) {
        final DefaultRepositorySystemSession s = MavenRepositorySystemUtils.newSession();
        final LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());

        s.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT,
                            System.getProperty(PROP_CONNECT_TIMEOUT));
        s.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT,
                            System.getProperty(PROP_REQUEST_TIMEOUT));
        s.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);

        s.setOffline(offline);
        s.setUpdatePolicy(forceRefresh ? RepositoryPolicy.UPDATE_POLICY_ALWAYS :
            RepositoryPolicy.UPDATE_POLICY_NEVER);

        s.setLocalRepositoryManager(system.newLocalRepositoryManager(s, localRepo));
        s.setProxySelector(settings.proxySelector);
        s.setMirrorSelector(settings.getMirrorSelector());
        s.setAuthenticationSelector(settings.getAuthSelector());

        s.setTransferListener((TransferListener)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { TransferListener.class },
            new InvocationHandler() {
                public Object invoke (Object proxy, Method method, Object[] args) {
                    onTransferEvent(method.getName(), (TransferEvent)args[0]);
                    return null;
                }
            }));
        s.setRepositoryListener((RepositoryListener)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { RepositoryListener.class },
            new InvocationHandler() {
                public Object invoke (Object proxy, Method method, Object[] args) {
                    onRepositoryEvent(method.getName(), (RepositoryEvent)args[0]);
                    return null;
                }
            }));

        return s;
    }

    private RemoteRepository newRemoteRepository (
        String name, String url, RepositoryPolicy policy) {
        // this is absurd, but there's no way to use ProxySelector to select a proxy without already
        // *having* a RemoteRepository, but we can't set a Proxy on a RemoteRepository after the
        // fact, we can only set it when *building* a RemoteRepository; so we have to build one
        // without the proxy, use it to select a proxy and then build a new one; go Maven!
        RemoteRepository scratch = new RemoteRepository.Builder(name, "default", url).build();
        Proxy proxy = settings.proxySelector.getProxy(scratch);
        return new RemoteRepository.Builder(name, "default", url).setProxy(proxy).setPolicy(policy).build();
    }

    private static Coord toCoord (Artifact art) {
      Coord coord = new Coord(art.getGroupId(), art.getArtifactId(),
                              art.getVersion(), art.getExtension());
      String cl = art.getClassifier();
      if (cl != null && cl.length() > 0) coord.classifier = cl;
      return coord;
    }

    private static Artifact toArtifact (Coord coord) {
        return new DefaultArtifact(coord.groupId, coord.artifactId, coord.classifier, coord.kind,
                                   coord.version);
    }

    private static Dependency toDependency (Coord coord) {
        return new Dependency(toArtifact(coord), JavaScopes.RUNTIME, false,
                              getExclusions(coord.exclusions));
    }

    private static List<Dependency> toDependencies (List<Coord> coords) {
        final List<Dependency> deps = new ArrayList<Dependency>(coords.size());
        for (Coord c : coords) deps.add(toDependency(c));
        return deps;
    }

    private static List<Exclusion> getExclusions (String excls) {
        if (excls == null) return null;
        final List<String> exclusionPatterns = Arrays.asList(excls.split(","));
        final List<Exclusion> exclusions = new ArrayList<Exclusion>();
        for (String ex : exclusionPatterns) {
            String[] coords = ex.trim().split(":");
            if (coords.length != 2) throw new IllegalArgumentException(
                "Illegal exclusion dependency coordinates: " + excls +
                " (in exclusion " + ex + ")");
            exclusions.add(new Exclusion(coords[0], coords[1], "*", "*"));
        }
        return exclusions;
    }

    // necessary if we want to forgo Guice/Sisu injection and use DefaultServiceLocator instead
    private static final io.takari.filemanager.FileManager takariFileManager =
        new io.takari.filemanager.internal.DefaultFileManager();

    public static class LockingFileProcessor
            extends io.takari.aether.concurrency.LockingFileProcessor {
        public LockingFileProcessor() {
            super(takariFileManager);
        }
    }

    public static class LockingSyncContextFactory
            extends io.takari.aether.concurrency.LockingSyncContextFactory {
        public LockingSyncContextFactory() {
            super(takariFileManager);
        }
    }
}
