package com.oneandone.devel.modules.pws.pwsp;


import java.io.File;
import java.net.URI;

import org.apache.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import com.oneandone.devel.modules.pws.pwsp.embedder.MavenEmulator;

public class PustefixWorkspaceMavenPluginReplacement  {
    private static Logger LOGGER = Logger.getLogger(PustefixWorkspaceMavenPluginReplacement.class);

    public PustefixWorkspaceMavenPluginReplacement() {
        // TODO Auto-generated constructor stub
    }

    public boolean openWorkspace(File workingDirectory, URI connection) {
        boolean result = false;

        LOGGER.debug("openWorkspace(" + workingDirectory + ", " + connection.toString() + ")");

        LogDelegator delegator = new LogDelegator();
        OpenWorkspaceMojo owsp = new OpenWorkspaceMojo();

        String scmConnection = connection.toString();
        LOGGER.info("scmConnection:" + scmConnection);
        if (!scmConnection.startsWith("scm:svn:")) {
            scmConnection = "scm:svn:" + scmConnection;
        }
        owsp.setConnection(scmConnection);
        owsp.setBasedir(workingDirectory);
        owsp.setLog(delegator);

        try {
            MavenEmulator mavenEmulator = MavenEmulator.createInstance();

            owsp.setSession(mavenEmulator.getMavenSession());

            owsp.setProjectBuilder(mavenEmulator.getProjectBuilder());

            owsp.setWorkspaceProject(new MavenProject());
            owsp.setScmManager(mavenEmulator.getScmManager());
            owsp.setScmUrlSelector(mavenEmulator.getScmUrlSeletor());
            owsp.setLocalRepo(mavenEmulator.getLocalArtifactRepository());
            owsp.setRemoteRepos(mavenEmulator.getRemoteRepositoriesLegacy());

            owsp.execute();
            result = true;
        } catch (MojoExecutionException e) {
            LOGGER.error("MojoExecutionException:", e);
        } catch (MojoFailureException e) {
            LOGGER.error("MojoFailureException:", e);
        }

        return result;
    }


}
