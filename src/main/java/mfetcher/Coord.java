package mfetcher;

public class Coord {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String kind; // jar, pom, etc.

    // TEMP: these are not currently set anywhere, but DependencyManager honors them
    public String classifier; // null for default
    public String exclusions; // null for default

    public Coord (String groupId, String artifactId, String version, String kind) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.kind = kind;
    }

    @Override public String toString () {
        return groupId + ":" + artifactId + ":" + version + ":" + kind;
    }

    @Override public int hashCode () {
        return groupId.hashCode() ^ artifactId.hashCode() ^ version.hashCode() ^ kind.hashCode();
    }

    @Override public boolean equals (Object other) {
        if (!(other instanceof Coord)) return false;
        Coord oc = (Coord)other;
        return oc.groupId.equals(groupId) && oc.artifactId.equals(artifactId) &&
            oc.version.equals(version) && oc.kind.equals(kind);
    }
}
