package org.gradle.plugins.fsm.classloader

/**
 * URLClassLoader that takes a directory and loads all jar files directly located in it.
 */
class JarClassLoader extends URLClassLoader {
    JarClassLoader(File libDir, ClassLoader parent) {
        super(getUrls(libDir), parent)
    }

    private static URL[] getUrls(File libDir) {
        List<URL> jarFilesUrls = new ArrayList()
        Arrays.asList(libDir.listFiles()).forEach { jarFile ->
            try {
                URL url = new File(jarFile.path).toURL()
                jarFilesUrls.add(url)
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
        jarFilesUrls.toArray(new URL[0])
    }
}
