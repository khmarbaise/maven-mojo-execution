package com.oneandone.devel.modules.pws.pwsp.embedder;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.tools.ant.AntClassLoader;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.interactivity.DefaultPrompter;
import org.codehaus.plexus.util.Os;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.transfer.TransferListener;

import de.ui.devel.maven.plugins.pfixworkspace.api.DefaultResolver;
import de.ui.devel.maven.plugins.pfixworkspace.ui.DefaultScmUrlSelector;
import de.ui.devel.maven.plugins.pfixworkspace.ui.ScmUrlSelector;

public class MavenEmulator {
    /**
     * Id of maven core class realm
     */
    public static final String MAVEN_CORE_REALM_ID = "plexus.core"; //$NON-NLS-1$

    private static Logger LOGGER = Logger.getLogger(MavenEmulator.class);

    private RepositorySystem repositorySystem;
    // TODO: use a project builder that works without legacy classes, esp. without ArtifactRepository ...
    //       (i didn't find one in mvn 3.0.2)
    private ProjectBuilder projectBuilder;

    private List<RemoteRepository> remoteRepositories;
    private List<ArtifactRepository> remoteRepositoriesLegacy;

    private ArtifactRepository localArtifactRepository;

    private PlexusContainer plexusContainer;

    private MavenRepositorySystemSession mavenRepositorySystemSession;

    private MavenSession mavenSession;

    private Settings settings;

    private MavenExecutionRequest mavenExectutionRequest;
    private MavenRequest mavenRequest;

    private ScmManager scmManager;

    private ScmUrlSelector scmUrlSeletor;

    public static MavenEmulator createInstance () {
        LOGGER.debug("getenv():" + System.getenv("MAVEN_HOME" ));
        ClassWorld world = new ClassWorld(MAVEN_CORE_REALM_ID, Thread.currentThread().getContextClassLoader());
        File mavenHome = new File(System.getenv("MAVEN_HOME" ));

        ClassRealm classRealm = null;
        try {
            classRealm = MavenEmulator.buildClassRealm( mavenHome, world, Thread.currentThread().getContextClassLoader() );
        } catch (MavenEmbedderException e) {
            LOGGER.error("MavenEmbedderException:", e);
        }

        return new MavenEmulator(
                new ExecutionListener(),
                false,
                Repositories.STANDARD,
                0,
                world, //ClassWorld classWorld,
                classRealm //ClassRealm realm
            );
    }


    private MavenEmulator(
            TransferListener transferListener,
            boolean offline,
            List<RemoteRepository> remoteRepositories,
            int loglevel,
            ClassWorld classWorld,
            ClassRealm realm
        ) {

        setMavenRequest(new MavenRequest());
        getMavenRequest().setBaseDirectory("test");
        getMavenRequest().setPom("pom.xml");

        getMavenRequest().setGlobalSettingsFile(MavenCli.DEFAULT_GLOBAL_SETTINGS_FILE.getAbsolutePath());
        getMavenRequest().setUserSettingsFile(MavenCli.DEFAULT_USER_SETTINGS_FILE.getAbsolutePath());

        setRemoteRepositories(remoteRepositories);

        DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        if (classWorld != null) {
            config.setClassWorld(classWorld);
        }

        if (realm != null) {
            config.setRealm(realm);
        }

        try {
            setPlexusContainer(new DefaultPlexusContainer(config));

            SettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
            settingsBuildingRequest.setGlobalSettingsFile(MavenCli.DEFAULT_GLOBAL_SETTINGS_FILE);
            settingsBuildingRequest.setUserSettingsFile(MavenCli.DEFAULT_USER_SETTINGS_FILE);

            setMavenExectutionRequest(new DefaultMavenExecutionRequest());

            settingsBuildingRequest.setUserProperties(getMavenExectutionRequest().getUserProperties());
            settingsBuildingRequest.getSystemProperties().putAll(System.getProperties());
            settingsBuildingRequest.getSystemProperties().putAll(getMavenExectutionRequest().getSystemProperties());
            settingsBuildingRequest.getSystemProperties().putAll(getEnvVars());

            setSettings(getPlexusContainer().lookup(SettingsBuilder.class).build(settingsBuildingRequest).getEffectiveSettings());

            buildMavenExecutionRequest();


            RepositorySystemSession rss = (((DefaultMaven) getPlexusContainer().lookup(Maven.class.getCanonicalName()))).newRepositorySession(getMavenExectutionRequest());
            setMavenSession(new MavenSession( getPlexusContainer(), rss, getMavenExectutionRequest(), new DefaultMavenExecutionResult() ));

//            getPlexusContainer().lookup(LegacySupport.class).setSession(getMavenSession());

            setRepositorySystem(getPlexusContainer().lookup(RepositorySystem.class));

            setMavenRepositorySystemSession(new MavenRepositorySystemSession());
            getMavenRepositorySystemSession().setOffline(offline);

            getMavenExectutionRequest().setLocalRepository(getLocalRepository());
            getMavenExectutionRequest().setLocalRepositoryPath(getLocalRepositoryPath());

            if (transferListener != null) {
                getMavenRepositorySystemSession().setTransferListener(transferListener);
                getMavenRequest().setTransferListener(transferListener);
            }

            setProjectBuilder(getPlexusContainer().lookup(ProjectBuilder.class));

////            DefaultScmUrlSelector scmUrlSelector = new DefaultScmUrlSelector();
////            scmUrlSelector.setPrompter(new DefaultPrompter());
////            scmUrlSelector.setResolver(new DefaultResolver());
//            setScmUrlSeletor(scmUrlSelector);

            setScmManager((ScmManager) getPlexusContainer().lookup( ScmManager.ROLE));

            setLocalArtifactRepository(getMavenExectutionRequest().getLocalRepository());
            setRemoteRepositoriesLegacy(convert(remoteRepositories));

        } catch (PlexusContainerException e) {
            throw new IllegalStateException(e);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        } catch (SettingsBuildingException e) {
            throw new IllegalStateException(e);
        } catch (MavenEmbedderException e) {
            throw new IllegalStateException(e);
        }
    }

    private void buildMavenExecutionRequest() throws MavenEmbedderException,
            ComponentLookupException {
        if (getMavenRequest().getGlobalSettingsFile() != null) {
            getMavenExectutionRequest().setGlobalSettingsFile(new File(
                    this.getMavenRequest().getGlobalSettingsFile()));
        }

        if (getMavenExectutionRequest().getUserSettingsFile() != null) {
            getMavenExectutionRequest().setUserSettingsFile(new File(
                    getMavenRequest().getUserSettingsFile()));
        }

        try {
            getPlexusContainer().lookup(MavenExecutionRequestPopulator.class).populateFromSettings(
                    getMavenExectutionRequest(), getSettings());

            getPlexusContainer().lookup(MavenExecutionRequestPopulator.class).populateDefaults(getMavenExectutionRequest());
        } catch (IllegalStateException e) {
            LOGGER.error("IllegalStateException:", e);
        } catch (MavenExecutionRequestPopulationException e) {
            LOGGER.error("MavenExecutionRequestPopulationException:", e);
        }

        ArtifactRepository localRepository = getLocalRepository();
        getMavenExectutionRequest().setLocalRepository(localRepository);
        getMavenExectutionRequest().setLocalRepositoryPath(localRepository.getBasedir());
        getMavenExectutionRequest().setOffline(getMavenExectutionRequest().isOffline());

        getMavenExectutionRequest().setUpdateSnapshots(this.getMavenRequest().isUpdateSnapshots());

        // TODO check null and create a console one ?
        getMavenExectutionRequest().setTransferListener(getMavenRequest().getTransferListener());

        getMavenExectutionRequest().setCacheNotFound(getMavenRequest().isCacheNotFound());
        getMavenExectutionRequest().setCacheTransferError(true);

        getMavenExectutionRequest().setUserProperties(getMavenRequest().getUserProperties());
        getMavenExectutionRequest().getSystemProperties().putAll(System.getProperties());
        if (getMavenRequest().getSystemProperties() != null) {
            getMavenExectutionRequest().getSystemProperties().putAll(getMavenRequest().getSystemProperties());
        }
        getMavenExectutionRequest().getSystemProperties().putAll(getEnvVars());

//        if (this.mavenHome != null) {
//            getMavenExectutionRequest().getSystemProperties().put("maven.home", this.mavenHome.getAbsolutePath());
//        }

        if (getMavenRequest().getProfiles() != null
                && !getMavenRequest().getProfiles().isEmpty()) {
            for (String id : this.getMavenRequest().getProfiles()) {
                Profile p = new Profile();
                p.setId(id);
                p.setSource("cli");
                getMavenExectutionRequest().addProfile(p);
                getMavenExectutionRequest().addActiveProfile(id);
            }
        }

        // FIXME
        getMavenExectutionRequest().setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_INFO);

        // FIXME
        getPlexusContainer().lookup(org.codehaus.plexus.logging.Logger.class).setThreshold(MavenExecutionRequest.LOGGING_LEVEL_INFO);

        getMavenExectutionRequest()
                .setExecutionListener(getMavenRequest().getExecutionListener())
                .setInteractiveMode(getMavenRequest().isInteractive())
                .setGlobalChecksumPolicy(getMavenRequest().getGlobalChecksumPolicy())
                .setGoals(getMavenRequest().getGoals());

        if (getMavenRequest().getPom() != null) {
            getMavenExectutionRequest().setPom(new File(getMavenRequest().getPom()));
        }

        if (getMavenRequest().getWorkspaceReader() != null) {
            getMavenExectutionRequest().setWorkspaceReader(this.getMavenRequest().getWorkspaceReader());
        }

        // FIXME inactive profiles

        // this.mavenExecutionRequest.set

    }

    public ArtifactRepository getLocalRepository() throws ComponentLookupException {
        try {
            String localRepositoryPath = getLocalRepositoryPath();
            if ( localRepositoryPath != null ) {
                return getPlexusContainer().lookup( RepositorySystem.class ).createLocalRepository( new File( localRepositoryPath ) );
            }
            return getPlexusContainer().lookup( RepositorySystem.class ).createLocalRepository( RepositorySystem.defaultUserLocalRepository );
        } catch ( InvalidRepositoryException e ) {
            // never happened
            throw new IllegalStateException( e );
        }
    }

    public String getLocalRepositoryPath() {
        String path = null;

            Settings settings = getSettings();
            path = settings.getLocalRepository();

        if ( this.getMavenRequest().getLocalRepositoryPath() != null ) {
            path =  this.getMavenRequest().getLocalRepositoryPath();
        }

        if ( path == null ) {
            path = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
        }
        return path;
    }

    public MavenProject readProject(File mavenProject)
            throws ProjectBuildingException, MavenEmbedderException {

        List<MavenProject> projects = readProjects(mavenProject, false);
        return projects == null || projects.isEmpty() ? null : projects.get(0);

    }

    private static ClassRealm buildClassRealm(File mavenHome, ClassWorld world,
            ClassLoader parentClassLoader) throws MavenEmbedderException {

        if (mavenHome == null) {
            throw new IllegalArgumentException("mavenHome cannot be null");
        }
        if (!mavenHome.exists()) {
            throw new IllegalArgumentException("mavenHome must exists");
        }

        // list all jar under mavenHome/lib

        File libDirectory = new File(mavenHome, "lib");
        if (!libDirectory.exists()) {
            throw new IllegalArgumentException(mavenHome.getPath()
                    + " without lib directory");
        }

        File[] jarFiles = libDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });

        AntClassLoader antClassLoader = new AntClassLoader(Thread
                .currentThread().getContextClassLoader(), false);

        for (File jarFile : jarFiles) {
            antClassLoader.addPathComponent(jarFile);
        }

        if (world == null) {
            world = new ClassWorld();
        }

        ClassRealm classRealm = new ClassRealm(world, "plexus.core",
                parentClassLoader == null ? antClassLoader : parentClassLoader);

        for (File jarFile : jarFiles) {
            try {
                classRealm.addURL(jarFile.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new MavenEmbedderException(e.getMessage(), e);
            }
        }
        return classRealm;
    }

    public List<MavenProject> readProjects(File mavenProject, boolean recursive)
            throws ProjectBuildingException, MavenEmbedderException {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            List<ProjectBuildingResult> results = buildProjects(mavenProject,
                    recursive);
            List<MavenProject> projects = new ArrayList<MavenProject>(
                    results.size());
            for (ProjectBuildingResult result : results) {
                projects.add(result.getProject());
            }
            return projects;
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }

    }

    public List<ProjectBuildingResult> buildProjects(File mavenProject,
            boolean recursive) throws ProjectBuildingException,
            MavenEmbedderException {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(
                    getPlexusContainer().getContainerRealm());
            ProjectBuilder projectBuilder = getPlexusContainer().lookup(
                    ProjectBuilder.class);
            ProjectBuildingRequest projectBuildingRequest = getMavenExectutionRequest()
                    .getProjectBuildingRequest();

            projectBuildingRequest.setValidationLevel(this.getMavenRequest()
                    .getValidationLevel());

            RepositorySystemSession repositorySystemSession = buildRepositorySystemSession();

            projectBuildingRequest
                    .setRepositorySession(repositorySystemSession);

            projectBuildingRequest.setProcessPlugins(this.getMavenRequest()
                    .isProcessPlugins());

            projectBuildingRequest.setResolveDependencies(this.getMavenRequest()
                    .isResolveDependencies());

            List<ProjectBuildingResult> results = projectBuilder.build(
                    Arrays.asList(mavenProject), recursive,
                    projectBuildingRequest);

            return results;
        } catch (ComponentLookupException e) {
            throw new MavenEmbedderException(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }

    }

    private RepositorySystemSession buildRepositorySystemSession() throws ComponentLookupException {
        DefaultMaven defaultMaven = (DefaultMaven) getPlexusContainer().lookup( Maven.class );
        return defaultMaven.newRepositorySession( getMavenExectutionRequest());
    }


    private Properties getEnvVars( ) {
        Properties envVars = new Properties();
        boolean caseSensitive = !Os.isFamily( Os.FAMILY_WINDOWS );
        for ( Map.Entry<String, String> entry : System.getenv().entrySet() )
        {
            String key = "env." + ( caseSensitive ? entry.getKey() : entry.getKey().toUpperCase( Locale.ENGLISH ) );
            envVars.setProperty( key, entry.getValue() );
        }
        return envVars;
    }


    private static List<ArtifactRepository> convert(List<RemoteRepository> remoteRepositories) {
        List<ArtifactRepository> result;

        result = new ArrayList<ArtifactRepository>(remoteRepositories.size());
        for (RemoteRepository repository : remoteRepositories) {
            result.add(convert(repository));
        }
        return result;
    }

    private static ArtifactRepository convert(RemoteRepository repository) {
        RepositoryPolicy sp;
        RepositoryPolicy rp;

        LOGGER.debug("convert(): " + repository.getUrl());
        sp = repository.getPolicy(true);
        rp = repository.getPolicy(false);
        return
            new DefaultArtifactRepository(repository.getId(), repository.getUrl(), new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(sp.isEnabled(), sp.getUpdatePolicy(), sp.getChecksumPolicy()),
                new ArtifactRepositoryPolicy(rp.isEnabled(), rp.getUpdatePolicy(), rp.getChecksumPolicy())
            );
    }

    public RepositorySystem getRepositorySystem() {
        return repositorySystem;
    }

    public List<RemoteRepository> getRemoteRepositories() {
        return remoteRepositories;
    }

    public List<ArtifactRepository> getRemoteRepositoriesLegacy() {
        return remoteRepositoriesLegacy;
    }


    public void setPlexusContainer(DefaultPlexusContainer plexusContainer) {
        this.plexusContainer = plexusContainer;
    }


    public PlexusContainer getPlexusContainer() {
        return plexusContainer;
    }

    public void setRepositorySystem(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }


    public void setMavenRepositorySystemSession(
            MavenRepositorySystemSession mavenRepositorySystemSession) {
        this.mavenRepositorySystemSession = mavenRepositorySystemSession;
    }


    public MavenRepositorySystemSession getMavenRepositorySystemSession() {
        return mavenRepositorySystemSession;
    }


    public void setProjectBuilder(ProjectBuilder projectBuilder) {
        this.projectBuilder = projectBuilder;
    }


    public ProjectBuilder getProjectBuilder() {
        return projectBuilder;
    }


    public void setRemoteRepositoriesLegacy(List<ArtifactRepository> remoteRepositoriesLegacy) {
        this.remoteRepositoriesLegacy = remoteRepositoriesLegacy;
    }


    public void setRemoteRepositories(List<RemoteRepository> remoteRepositories) {
        this.remoteRepositories = remoteRepositories;
    }


    public void setMavenSession(MavenSession mavenSession) {
        this.mavenSession = mavenSession;
    }


    public MavenSession getMavenSession() {
        return mavenSession;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setMavenExectutionRequest(MavenExecutionRequest mavenExectutionRequest) {
        this.mavenExectutionRequest = mavenExectutionRequest;
    }

    public MavenExecutionRequest getMavenExectutionRequest() {
        return mavenExectutionRequest;
    }

    public void setMavenRequest(MavenRequest mavenRequest) {
        this.mavenRequest = mavenRequest;
    }

    public MavenRequest getMavenRequest() {
        return mavenRequest;
    }

    public void setScmManager(ScmManager scmManager) {
        this.scmManager = scmManager;
    }

    public ScmManager getScmManager() {
        return scmManager;
    }

    public void setScmUrlSeletor(ScmUrlSelector scmUrlSeletor) {
        this.scmUrlSeletor = scmUrlSeletor;
    }

    public ScmUrlSelector getScmUrlSeletor() {
        return scmUrlSeletor;
    }


    public void setLocalArtifactRepository(ArtifactRepository localArtifactRepository) {
        this.localArtifactRepository = localArtifactRepository;
    }


    public ArtifactRepository getLocalArtifactRepository() {
        return localArtifactRepository;
    }


}
