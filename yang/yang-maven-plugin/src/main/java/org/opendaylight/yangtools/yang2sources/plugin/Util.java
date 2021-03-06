/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang2sources.plugin;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.util.NamedFileInputStream;
import org.apache.maven.repository.RepositorySystem;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

final class Util {

    /**
     * It isn't desirable to create instances of this class
     */
    private Util() {
    }

    static final String YANG_SUFFIX = "yang";

    private static final int CACHE_SIZE = 10;
    // Cache for listed directories and found yang files. Typically yang files
    // are utilized twice. First: code is generated during generate-sources
    // phase Second: yang files are copied as resources during
    // generate-resources phase. This cache ensures that yang files are listed
    // only once.
    private static Map<File, Collection<File>> cache = Maps.newHashMapWithExpectedSize(CACHE_SIZE);

    /**
     * List files recursively and return as array of String paths. Use cache of
     * size 1.
     */
    static Collection<File> listFiles(File root) throws FileNotFoundException {
        if (cache.get(root) != null) {
            return cache.get(root);
        }

        if (!root.exists()) {
            throw new FileNotFoundException(root.toString());
        }

        Collection<File> yangFiles = FileUtils.listFiles(root, new String[] { YANG_SUFFIX }, true);

        toCache(root, yangFiles);
        return yangFiles;
    }

    static Collection<File> listFiles(File root, File[] excludedFiles, Log log) throws FileNotFoundException {
        if (!root.exists()) {
            if (log != null) {
                log.warn(Util.message("YANG source directory %s not found. No code will be generated.", YangToSourcesProcessor.LOG_PREFIX, root.toString()));
            }
            return Collections.emptyList();
        }
        Collection<File> result = new ArrayList<>();
        Collection<File> yangFiles = FileUtils.listFiles(root, new String[] { YANG_SUFFIX }, true);
        for (File f : yangFiles) {
            boolean excluded = false;
            for (File ex : excludedFiles) {
                if (ex.equals(f)) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) {
                if (log != null) {
                    log.info(Util.message("%s file excluded %s", YangToSourcesProcessor.LOG_PREFIX,
                            Util.YANG_SUFFIX.toUpperCase(), f));
                }
            } else {
                result.add(f);
            }
        }

        return result;
    }

    private static void toCache(final File rootDir, final Collection<File> yangFiles) {
        cache.put(rootDir, yangFiles);
    }

    /**
     * Instantiate object from fully qualified class name
     */
    static <T> T getInstance(String codeGeneratorClass, Class<T> baseType) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException {
        return baseType.cast(resolveClass(codeGeneratorClass, baseType).newInstance());
    }

    private static Class<?> resolveClass(String codeGeneratorClass, Class<?> baseType) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(codeGeneratorClass);

        if (!isImplemented(baseType, clazz)) {
            throw new IllegalArgumentException("Code generator " + clazz + " has to implement " + baseType);
        }
        return clazz;
    }

    private static boolean isImplemented(Class<?> expectedIface, Class<?> byClazz) {
        for (Class<?> iface : byClazz.getInterfaces()) {
            if (iface.equals(expectedIface)) {
                return true;
            }
        }
        return false;
    }

    static String message(String message, String logPrefix, Object... args) {
        String innerMessage = String.format(message, args);
        return String.format("%s %s", logPrefix, innerMessage);
    }

    static List<File> getClassPath(MavenProject project) {
        List<File> dependencies = Lists.newArrayList();
        for (Artifact element : project.getArtifacts()) {
            File asFile = element.getFile();
            if (isJar(asFile) || asFile.isDirectory()) {
                dependencies.add(asFile);
            }
        }
        return dependencies;
    }

    /**
     * Read current project dependencies and check if it don't grab incorrect
     * artifacts versions which could be in conflict with plugin dependencies.
     *
     * @param project
     *            current project
     * @param repoSystem
     *            repository system
     * @param localRepo
     *            local repository
     * @param remoteRepos
     *            remote repositories
     * @param log
     *            logger
     */
    static void checkClasspath(MavenProject project, RepositorySystem repoSystem, ArtifactRepository localRepo,
            List<ArtifactRepository> remoteRepos, Log log) {
        Plugin plugin = project.getPlugin(YangToSourcesMojo.PLUGIN_NAME);
        if (plugin == null) {
            log.warn(message("%s not found, dependencies version check skipped", YangToSourcesProcessor.LOG_PREFIX,
                    YangToSourcesMojo.PLUGIN_NAME));
        } else {
            Map<Artifact, Collection<Artifact>> pluginDependencies = new HashMap<>();
            getPluginTransitiveDependencies(plugin, pluginDependencies, repoSystem, localRepo, remoteRepos, log);

            Set<Artifact> projectDependencies = project.getDependencyArtifacts();
            for (Map.Entry<Artifact, Collection<Artifact>> entry : pluginDependencies.entrySet()) {
                checkArtifact(entry.getKey(), projectDependencies, log);
                for (Artifact dependency : entry.getValue()) {
                    checkArtifact(dependency, projectDependencies, log);
                }
            }
        }
    }

    /**
     * Read transitive dependencies of given plugin and store them in map.
     *
     * @param plugin
     *            plugin to read
     * @param map
     *            map, where founded transitive dependencies will be stored
     * @param repoSystem
     *            repository system
     * @param localRepository
     *            local repository
     * @param remoteRepos
     *            list of remote repositories
     * @param log
     *            logger
     */
    private static void getPluginTransitiveDependencies(Plugin plugin, Map<Artifact, Collection<Artifact>> map,
            RepositorySystem repoSystem, ArtifactRepository localRepository, List<ArtifactRepository> remoteRepos,
            Log log) {

        List<Dependency> pluginDependencies = plugin.getDependencies();
        for (Dependency dep : pluginDependencies) {
            Artifact artifact = repoSystem.createDependencyArtifact(dep);

            ArtifactResolutionRequest request = new ArtifactResolutionRequest();
            request.setArtifact(artifact);
            request.setResolveTransitively(true);
            request.setLocalRepository(localRepository);
            request.setRemoteRepositories(remoteRepos);

            ArtifactResolutionResult result = repoSystem.resolve(request);
            Set<Artifact> pluginDependencyDependencies = result.getArtifacts();
            map.put(artifact, pluginDependencyDependencies);
        }
    }

    /**
     * Check artifact against collection of dependencies. If collection contains
     * artifact with same groupId and artifactId, but different version, logs a
     * warning.
     *
     * @param artifact
     *            artifact to check
     * @param dependencies
     *            collection of dependencies
     * @param log
     *            logger
     */
    private static void checkArtifact(Artifact artifact, Collection<Artifact> dependencies, Log log) {
        for (org.apache.maven.artifact.Artifact d : dependencies) {
            if (artifact.getGroupId().equals(d.getGroupId()) && artifact.getArtifactId().equals(d.getArtifactId())) {
                if (!(artifact.getVersion().equals(d.getVersion()))) {
                    log.warn(message("Dependency resolution conflict:", YangToSourcesProcessor.LOG_PREFIX));
                    log.warn(message("'%s' dependency [%s] has different version than one "
                            + "declared in current project [%s]. It is recommended to fix this problem "
                            + "because it may cause compilation errors.", YangToSourcesProcessor.LOG_PREFIX,
                            YangToSourcesMojo.PLUGIN_NAME, artifact, d));
                }
            }
        }
    }

    private static final String JAR_SUFFIX = ".jar";

    private static boolean isJar(File element) {
        return (element.isFile() && element.getName().endsWith(JAR_SUFFIX)) ? true : false;
    }

    static <T> T checkNotNull(T obj, String paramName) {
        return Preconditions.checkNotNull(obj, "Parameter " + paramName + " is null");
    }

    static final class YangsInZipsResult implements Closeable {
        private final List<InputStream> yangStreams;
        private final List<Closeable> zipInputStreams;

        private YangsInZipsResult(List<InputStream> yangStreams, List<Closeable> zipInputStreams) {
            this.yangStreams = yangStreams;
            this.zipInputStreams = zipInputStreams;
        }

        @Override
        public void close() throws IOException {
            for (InputStream is : yangStreams) {
                is.close();
            }
            for (Closeable is : zipInputStreams) {
                is.close();
            }
        }

        public List<InputStream> getYangStreams() {
            return this.yangStreams;
        }
    }

    static YangsInZipsResult findYangFilesInDependenciesAsStream(Log log, MavenProject project)
            throws MojoFailureException {
        List<InputStream> yangsFromDependencies = new ArrayList<>();
        List<Closeable> zips = new ArrayList<>();
        try {
            List<File> filesOnCp = Util.getClassPath(project);
            log.info(Util.message("Searching for yang files in following dependencies: %s",
                    YangToSourcesProcessor.LOG_PREFIX, filesOnCp));

            for (File file : filesOnCp) {
                List<String> foundFilesForReporting = new ArrayList<>();
                // is it jar file or directory?
                if (file.isDirectory()) {
                    //FIXME: code duplicate
                    File yangDir = new File(file, YangToSourcesProcessor.META_INF_YANG_STRING);
                    if (yangDir.exists() && yangDir.isDirectory()) {
                        File[] yangFiles = yangDir.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                return name.endsWith(".yang") && new File(dir, name).isFile();
                            }
                        });
                        for (File yangFile : yangFiles) {
                            yangsFromDependencies.add(new NamedFileInputStream(yangFile, YangToSourcesProcessor.META_INF_YANG_STRING + File.separator + yangFile.getName()));
                        }
                    }

                } else {
                    ZipFile zip = new ZipFile(file);
                    zips.add(zip);

                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String entryName = entry.getName();

                        if (entryName.startsWith(YangToSourcesProcessor.META_INF_YANG_STRING_JAR)
                                && !entry.isDirectory() && entryName.endsWith(".yang")) {
                            foundFilesForReporting.add(entryName);
                            // This will be closed after all streams are
                            // parsed.
                            InputStream entryStream = zip.getInputStream(entry);
                            yangsFromDependencies.add(entryStream);
                        }
                    }
                }
                if (foundFilesForReporting.size() > 0) {
                    log.info(Util.message("Found %d yang files in %s: %s", YangToSourcesProcessor.LOG_PREFIX,
                            foundFilesForReporting.size(), file, foundFilesForReporting));
                }

            }
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        return new YangsInZipsResult(yangsFromDependencies, zips);
    }

    static Collection<File> findYangFilesInDependencies(Log log, MavenProject project) throws MojoFailureException {
        final List<File> yangsFilesFromDependencies = new ArrayList<>();

        try {
            List<File> filesOnCp = Util.getClassPath(project);
            log.info(Util.message("Searching for yang files in following dependencies: %s",
                    YangToSourcesProcessor.LOG_PREFIX, filesOnCp));

            for (File file : filesOnCp) {
                // is it jar file or directory?
                if (file.isDirectory()) {
                    //FIXME: code duplicate
                    File yangDir = new File(file, YangToSourcesProcessor.META_INF_YANG_STRING);
                    if (yangDir.exists() && yangDir.isDirectory()) {
                        File[] yangFiles = yangDir.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                return name.endsWith(".yang") && new File(dir, name).isFile();
                            }
                        });

                        yangsFilesFromDependencies.addAll(Arrays.asList(yangFiles));
                    }
                } else {
                    try (ZipFile zip = new ZipFile(file)) {

                        final Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            String entryName = entry.getName();

                            if (entryName.startsWith(YangToSourcesProcessor.META_INF_YANG_STRING_JAR)
                                    && !entry.isDirectory() && entryName.endsWith(".yang")) {
                                log.debug(Util.message("Found a YANG file in %s: %s", YangToSourcesProcessor.LOG_PREFIX,
                                        file, entryName));
                                yangsFilesFromDependencies.add(file);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoFailureException("Failed to scan for YANG files in depedencies", e);
        }
        return yangsFilesFromDependencies;
    }

    static final class ContextHolder {
        private final SchemaContext context;
        private final Set<Module> yangModules;

        ContextHolder(SchemaContext context, Set<Module> yangModules) {
            this.context = context;
            this.yangModules = yangModules;
        }

        SchemaContext getContext() {
            return context;
        }

        Set<Module> getYangModules() {
            return yangModules;
        }
    }

}
