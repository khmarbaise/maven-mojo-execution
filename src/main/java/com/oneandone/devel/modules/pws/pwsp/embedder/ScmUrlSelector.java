package com.oneandone.devel.modules.pws.pwsp.embedder;

import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.qooxdoo.sushi.life.Id;

public interface ScmUrlSelector {

    String ROLE = ScmUrlSelector.class.getName();

    /**
     * Asks the user to select or input a valid SCM URL. If a valid SCM URL is provided it is immediately returned. If
     * an HTTP or HTTPS URL is provided it is prefixed with "scm:svn". If maven coordinates are provided possible SCM
     * URLs are resolved and the user is asked to select one Otherwise the user is asked to input a SCM URL.
     * @param connectionOrCoordinates
     *            the connection URL or maven coordinates
     * @param localRepo
     *            the local repository
     * @param remoteRepos
     *            the remote repositories
     * @return the SCM URL
     * @throws Exception
     *             the exception
     */
    String selectScmUrl(String connectionOrCoordinates, ArtifactRepository localRepo,
            List<ArtifactRepository> remoteRepos) throws Exception;

    /**
     * Asks the user to select one of the given coordinates or to manually input an connection or coordinates.
     * @param coordinates
     *            the coordinates
     * @param localRepo
     *            the local repo
     * @param remoteRepos
     *            the remote repos
     * @return the selected coordinate, null if none selected
     * @throws Exception
     *             the exception
     */
    String selectConnectionOrCoordinates(List<Id> coordinates, ArtifactRepository localRepo,
            List<ArtifactRepository> remoteRepos) throws Exception;

    /**
     * Sets the prompter.
     * @param prompter
     *            the prompter
     */
    void setPrompter(Prompter prompter);

}
