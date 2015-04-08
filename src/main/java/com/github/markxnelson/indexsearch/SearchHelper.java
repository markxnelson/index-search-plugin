package com.github.markxnelson.indexsearch;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.http.client.config.RequestConfig;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.apache.maven.wagon.providers.http.HttpConfiguration;
import org.apache.maven.wagon.providers.http.HttpMethodConfiguration;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

/**
 * A helper class that will perform searches of the Maven index.
 */
public final class SearchHelper {

    // singleton

    private static SearchHelper INSTANCE = null;
    private static PlexusContainer plexusContainer;
    private static Indexer indexer;
    private static IndexUpdater indexUpdater;
    private static Wagon httpWagon;
    private static IndexingContext oracleContext;
    private static ProxyInfo proxyInfo;

    private static Log log = LogFactory.getLog(SearchHelper.class);

    private SearchHelper() {
        // do not allow direct instantiation
        // do not allow subclassing

        // set everything up
        try {
            init();
        } catch (SearchException e) {
            log.error("excpetion in SearchHelper.init(): " + e.getStackTrace());
        }
    }

    /**
     * Set up the SearchHelper singleton and update the index (if needed).
     */
    private void init()
    throws SearchException {

        try {

            // set up the indexer
            this.plexusContainer = new DefaultPlexusContainer();
            this.indexer = plexusContainer.lookup(Indexer.class);
            this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
            this.httpWagon = plexusContainer.lookup(Wagon.class, "http");

            // configure httpWagon for circular redirects..
            HttpConfiguration httpConfig = ((HttpWagon) httpWagon).getHttpConfiguration();
            if (httpConfig == null) {
                httpConfig = new HttpConfiguration();
            }
            HttpMethodConfiguration hmc = new HttpMethodConfiguration()
                .addParam("http.protocol.allow-circular-redirects", "%b,true");
            httpConfig.setAll(hmc);
            ((HttpWagon) httpWagon).setHttpConfiguration(httpConfig);

            // // set up proxyInfo
            // proxyInfo = new ProxyInfo();
            // proxyInfo.setHost("www-proxy.us.oracle.com");
            // proxyInfo.setPort(80);
            // proxyInfo.setType(ProxyInfo.PROXY_HTTP);

            // files where the index is stored
            String homeDir = System.getProperty("user.home");
            File oracleLocalCache = new File(homeDir + File.separator + ".index" + File.separator + "oracle-cache");
            File oracleIndexDir = new File(homeDir + File.separator + ".index" + File.separator + "oracle-index");

            // define search fields
            List<IndexCreator> indexers = new ArrayList<IndexCreator>();
            indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));
            indexers.add(plexusContainer.lookup(IndexCreator.class, "jarContent"));
            indexers.add(plexusContainer.lookup(IndexCreator.class, "maven-plugin"));

            // create context for index
            oracleContext = indexer.createIndexingContext("oracle-context",
                                                          "oracle",
                                                          oracleLocalCache,
                                                          oracleIndexDir,
                                                          "https://maven.oracle.com/",
                                                          null,
                                                          true,
                                                          true,
                                                          indexers);

            // update the index
            TransferListener listener = new AbstractTransferListener()
                {
                    public void transferStarted(TransferEvent transferEvent) {
                        log.info("Downloading " + transferEvent.getResource().getName());
                    }

                    public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {}

                    public void transferCompleted(TransferEvent transferEvent) {
                        log.info("Done");
                    }
                };
            ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, listener, null, proxyInfo);

            Date oracleContextCurrentTimestamp = oracleContext.getTimestamp();
            IndexUpdateRequest updateRequest = new IndexUpdateRequest(oracleContext, resourceFetcher);
            log.info("Updating index...");
            IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
            if (updateResult.isFullUpdate()) {
                log.info("Full update done");
            } else if (updateResult.getTimestamp().equals(oracleContextCurrentTimestamp)) {
                log.info("No update needed");
            } else {
                log.info("Incremental update done: " + oracleContextCurrentTimestamp + " to " + updateResult.getTimestamp());
            }

        } catch (IOException e) {
            throw new SearchException(e);
        } catch (ComponentLookupException e) {
            throw new SearchException(e);
        } catch (PlexusContainerException e) {
            throw new SearchException(e);
        }

    }

    /**
     * Get an instance of the SearchHelper singleton.
     */
    public synchronized static SearchHelper getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new SearchHelper();
            } catch (Exception e) {
                INSTANCE = null;
            }
        }
        return INSTANCE;
    }

    /**
     * Search the index for artifacts that match the given groupId and/or artifactId and/or contain at
     * least one class that matches the given className.
     *
     * @param groupId The groupId to match.
     * @param artifactId The artifactId to match.
     * @param className The class and package (partial) names to match.
     * @return A list of artifacts that satisfy the given search criteria.
     */
    public static List<SearchResult> performSearch(String groupId, String artifactId, String className)
        throws SearchException {

        // check if we need to update the index...
        getInstance();

        // do the actual search...
        List<SearchResult> results = new ArrayList<SearchResult>();
        try {
            BooleanQuery bq = new BooleanQuery();

            Query gidQ = null;
            if ((groupId != null) && (groupId.length() > 0)) {
                gidQ = indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression(groupId));
                bq.add(gidQ, Occur.MUST);
            }

            Query aidQ;
            if ((artifactId != null) && (artifactId.length() > 0)) {
                aidQ = indexer.constructQuery(MAVEN.ARTIFACT_ID, new SourcedSearchExpression(artifactId));
                bq.add(aidQ, Occur.MUST);
            }

            Query clsQ;
            if ((className != null) && (className.length() > 0)) {
                clsQ = indexer.constructQuery(MAVEN.CLASSNAMES, new UserInputSearchExpression(className));
                bq.add(clsQ, Occur.MUST);
            }

            log.info("Received query: " + bq.toString());

            FlatSearchResponse response = indexer.searchFlat(new FlatSearchRequest(bq, oracleContext));

            for (ArtifactInfo ai :  response.getResults()) {

                results.add(new SearchResult(ai.getFieldValue(MAVEN.GROUP_ID),
                                             ai.getFieldValue(MAVEN.ARTIFACT_ID),
                                             ai.getFieldValue(MAVEN.VERSION),
                                             ai.getFieldValue(MAVEN.PACKAGING),
                                             (((className == null) || (className.length() <1))
                                              ? null
                                              : filter(ai.getFieldValue(MAVEN.CLASSNAMES), className))));

            }


        } catch (Exception e) {
            throw new SearchException(e);
        }

        return results;
    }

    /**
     * This method takes a newline-separated string, breaks it down into a set of strings, and returns
     * each of those strings that match the pattern.
     *
     * @param str The newline-separated string to search.
     * @param pattern The pattern to look for.
     * @return A list of strings that match the pattern.
     */
    private static List<String> filter(String str, String pattern) {

        if ((str == null) || (pattern == null)) {
            return new ArrayList<String>();
        }
        List<String> result = new ArrayList<String>();
        String[] strs = str.replaceAll("/", ".").split("\n");
        for (String s : strs) {
            if (s.toLowerCase().contains(pattern.toLowerCase().replaceAll("\\*", ""))) {
                if (s.startsWith(".")) {
                    result.add(s.substring(1));
                } else {
                    result.add(s);
                }
            }
        }
        return result;

    }

}
