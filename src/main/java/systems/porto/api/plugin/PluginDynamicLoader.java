package systems.porto.api.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.porto.dto.DynamicDTO;
import systems.porto.dto.External;

import java.io.File;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PluginDynamicLoader {
    private static final Logger logger = LoggerFactory.getLogger(PluginDynamicLoader.class);

    private static final List<String> PLUGIN_TYPES = List.of(
        "common", "dtos", "datasources", "client-adapters", "processors", "entry-adapters", "scheduled-jobs"
    );

    public void addJarDependencies(final String pluginPath, final DynamicDTO descriptor, final ApiPluginClassLoader classLoader) {
        addJar(pluginPath, resolveJarName(descriptor), classLoader);
        if (descriptor.getDependencies() != null && descriptor.getDependencies().getExternal() != null) {
            for (External external : descriptor.getDependencies().getExternal()) {
                addJar(pluginPath, external.getJarFile(), classLoader);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T loadComponent(final String pluginPath, final DynamicDTO descriptor, final ApiPluginClassLoader classLoader) {
        try {
            addJarDependencies(pluginPath, descriptor, classLoader);
            String jarName = resolveJarName(descriptor);
            preloadJar(resolveJar(pluginPath, jarName), classLoader);
            if (descriptor.getDependencies() != null && descriptor.getDependencies().getExternal() != null) {
                for (External external : descriptor.getDependencies().getExternal()) {
                    preloadJar(resolveJar(pluginPath, external.getJarFile()), classLoader);
                }
            }
            if (descriptor.getClassName() == null || descriptor.getClassName().isBlank()) {
                return null;
            }
            Class<?> type = Class.forName(descriptor.getClassName(), true, classLoader);
            return (T) type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Failed to load plugin {}", descriptor.getId(), e);
            throw new RuntimeException("Failed to load plugin " + descriptor.getId(), e);
        }
    }

    private String resolveJarName(final DynamicDTO descriptor) {
        if (descriptor instanceof PluginDescriptor pluginDescriptor
            && pluginDescriptor.getJarFile() != null
            && !pluginDescriptor.getJarFile().isBlank()) {
            return pluginDescriptor.getJarFile();
        }
        return descriptor.getId() + "-" + descriptor.getVersion() + ".jar";
    }

    private void addJar(final String pluginPath, final String jarName, final ApiPluginClassLoader classLoader) {
        File jarFile = resolveJar(pluginPath, jarName);
        try {
            classLoader.addURL(jarFile.toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Failed to add plugin JAR: " + jarFile.getAbsolutePath(), e);
        }
    }

    private File resolveJar(final String pluginPath, final String jarName) {
        File direct = new File(pluginPath, jarName);
        if (direct.exists()) {
            return direct;
        }
        File pluginsRoot = findPluginsRoot(new File(pluginPath));
        if (pluginsRoot != null) {
            File common = new File(pluginsRoot, "common" + File.separator + jarName);
            if (common.exists()) {
                return common;
            }
            File[] typeDirs = pluginsRoot.listFiles(File::isDirectory);
            if (typeDirs != null) {
                for (File typeDir : typeDirs) {
                    if (!PLUGIN_TYPES.contains(typeDir.getName())) {
                        continue;
                    }
                    File inType = new File(typeDir, jarName);
                    if (inType.exists()) {
                        return inType;
                    }
                    File[] appDirs = typeDir.listFiles(File::isDirectory);
                    if (appDirs != null) {
                        for (File appDir : appDirs) {
                            File inApp = new File(appDir, jarName);
                            if (inApp.exists()) {
                                return inApp;
                            }
                        }
                    }
                }
            }
        }
        throw new RuntimeException("Plugin JAR does not exist: " + direct.getAbsolutePath());
    }

    private File findPluginsRoot(final File start) {
        File current = start;
        while (current != null) {
            if ("plugins".equals(current.getName())) {
                return current;
            }
            current = current.getParentFile();
        }
        return start.getParentFile();
    }

    /**
     * Eager-load only Porto plugin types. Third-party shaded deps (AWS, mail, DocuSign, Tika, …)
     * must not be force-initialized here — that breaks classloading and slows startup.
     */
    private void preloadJar(final File jarFile, final ApiPluginClassLoader classLoader) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entry.getName().replace('/', '.').replace(".class", "");
                if (!className.startsWith("systems.porto.")) {
                    continue;
                }
                Class.forName(className, true, classLoader);
            }
        }
    }
}
