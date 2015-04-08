package com.github.markxnelson.indexsearch;

import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "search",  requiresDirectInvocation = true, requiresProject = false, requiresDependencyResolution = ResolutionScope.COMPILE)
public class MavenIndexSearchMojo extends AbstractMojo {


	@Parameter
	private String groupId;

	@Parameter
	private String artifactId;

	@Parameter
	private String className;

	public void execute() throws MojoExecutionException {
		getLog().info("hello");
		
		try {
			List<SearchResult> results = SearchHelper.performSearch(groupId, artifactId, className);

			for (SearchResult result : results) {
				System.out.println(result.getGroupId() + ":" + result.getArtifactId() + ":" + result.getVersion() + ":" + result.getPackaging());
				List<String> classnames = result.getClassnames();
				if ((classnames != null) && (classnames.size() > 0)) {
					System.out.println("  Contains the matching class(es):");
					for (String x : classnames) {
						System.out.println("  - " + x);
					}
				}
			}
		} catch (SearchException e) {}

	}

}