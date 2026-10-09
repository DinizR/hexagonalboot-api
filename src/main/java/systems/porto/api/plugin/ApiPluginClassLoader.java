package systems.porto.api.plugin;

import java.net.URL;
import java.net.URLClassLoader;

public final class ApiPluginClassLoader extends URLClassLoader {

    public ApiPluginClassLoader(final ClassLoader parent) {
        super(new URL[0], parent);
    }

    public void addURL(final URL url) {
        super.addURL(url);
    }
}
