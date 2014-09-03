package mfetcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.ConservativeAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

/**
 * Handles reading Maven settings from {@code settings.xml}.
 */
public class MavenSettings {

    public final Settings settings;

    public MavenSettings () {
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setUserSettingsFile(LOCAL_M2.resolve("settings.xml").toFile());
        Path mhome = getMavenHome();
        request.setGlobalSettingsFile(mhome == null ? null :
            mhome.resolve("conf").resolve("settings.xml").toFile());
        request.setSystemProperties(getSystemProperties());

        System.out.println("Maven home " + mhome);
        System.out.println("Settings " + LOCAL_M2.resolve("settings.xml"));

        try {
            settings = new DefaultSettingsBuilderFactory().newInstance().build(request).
                getEffectiveSettings();
            SettingsDecrypter settingsDecrypter = newDefaultSettingsDecrypter();
            SettingsDecryptionResult result = settingsDecrypter.decrypt(
                new DefaultSettingsDecryptionRequest(settings));
            settings.setServers(result.getServers());
            settings.setProxies(result.getProxies());

            System.out.println("SETTINGS: " + settings);
        } catch (SettingsBuildingException e) {
            throw new RuntimeException(e);
        }
    }

    public ProxySelector getProxySelector () {
        DefaultProxySelector selector = new DefaultProxySelector();
        for (Proxy proxy : settings.getProxies()) {
            AuthenticationBuilder auth = new AuthenticationBuilder();
            auth.addUsername(proxy.getUsername()).addPassword(proxy.getPassword());
            selector.add(new org.eclipse.aether.repository.Proxy(proxy.getProtocol(),
                                                                 proxy.getHost(), proxy.getPort(),
                                                                 auth.build()),
                         proxy.getNonProxyHosts());
        }
        return selector;
    }

    public MirrorSelector getMirrorSelector () {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) selector.add(
            String.valueOf(mirror.getId()), mirror.getUrl(), mirror.getLayout(), false,
            mirror.getMirrorOf(), mirror.getMirrorOfLayouts());
        return selector;
    }

    public AuthenticationSelector getAuthSelector () {
        DefaultAuthenticationSelector selector = new DefaultAuthenticationSelector();
        for (Server server : settings.getServers()) {
            AuthenticationBuilder auth = new AuthenticationBuilder();
            auth.addUsername(server.getUsername()).addPassword(server.getPassword());
            auth.addPrivateKey(server.getPrivateKey(), server.getPassphrase());
            selector.add(server.getId(), auth.build());
        }
        return new ConservativeAuthenticationSelector(selector);
    }

    private static DefaultSettingsDecrypter newDefaultSettingsDecrypter () {
        // see:
        // http://git.eclipse.org/c/aether/aether-ant.git/tree/src/main/java/org/eclipse/aether/internal/ant/AntSettingsDecryptorFactory.java
        // http://git.eclipse.org/c/aether/aether-ant.git/tree/src/main/java/org/eclipse/aether/internal/ant/AntSecDispatcher.java
        DefaultSecDispatcher secDispatcher = new DefaultSecDispatcher() {
            {
                _configurationFile = "~/.m2/settings-security.xml";
                try {
                    _cipher = new DefaultPlexusCipher();
                } catch (PlexusCipherException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        DefaultSettingsDecrypter dsd = new DefaultSettingsDecrypter();
        try {
            java.lang.reflect.Field field = dsd.getClass().getDeclaredField("securityDispatcher");
            field.setAccessible(true);
            field.set(dsd, secDispatcher);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return dsd;
    }

    private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
    private static final Path LOCAL_M2 = USER_HOME.resolve(".m2");

    private static Path getMavenHome () {
        String menv = System.getenv("M2_HOME");
        if (menv != null && menv.length() > 0) return Paths.get(menv);
        String mprop = System.getProperty("maven.home");
        if (mprop != null && mprop.length() > 0) return Paths.get(mprop);
        return null;
    }

    private static Properties getSystemProperties () {
        Properties props = new Properties();
        boolean envCaseInsensitive = isWindows();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (envCaseInsensitive) key = key.toUpperCase(Locale.ENGLISH);
            key = "env." + key;
            props.put(key, entry.getValue());
        }
        props.putAll(System.getProperties());
        return props;
    }

    private static boolean isWindows () {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }
}
