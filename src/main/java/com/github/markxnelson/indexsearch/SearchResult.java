package com.github.markxnelson.indexsearch;

import java.util.List;

/**
 * Bean to hold a search result.
 */
public class SearchResult {

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private List<String> classnames;

    public SearchResult(String groupId, String artifactId, String version, String packaging, List<String> classnames) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.classnames = classnames;
        System.out.println("++ SearchResult constructor: " + groupId + ":" + artifactId + ":" + version + ":" + packaging);
    }

    public String getGroupId() {
        return this.groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return this.version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return this.packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public List<String> getClassnames() {
        return this.classnames;
    }

    public void setClassnames(List<String> classnames) {
        this.classnames = classnames;
    }

}
