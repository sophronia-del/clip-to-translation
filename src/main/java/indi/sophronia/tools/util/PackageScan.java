package indi.sophronia.tools.util;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PackageScan {
    private static final Class<?>[] EMPTY_ARRAY = new Class[]{};

    private static final Map<String, Class<?>[]> CACHE = new ConcurrentHashMap<>();

    public static Class<?>[]
    getClassesByPackageName(String packageName, Predicate<Class<?>> filter) throws IOException {
        String rawName = packageName;
        if (packageName == null || packageName.isEmpty()) {
            return EMPTY_ARRAY;
        }

        if (CACHE.containsKey(rawName)) {
            Class<?>[] result = CACHE.get(rawName);
            if (result != null) {
                return result;
            }
        }

        Set<Class<?>> classes = new LinkedHashSet<>();
        String packageDirName = packageName.replace('.', '/');
        Enumeration<URL> dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
        while (dirs.hasMoreElements()) {
            URL url = dirs.nextElement();
            String protocol = url.getProtocol();
            // file类型
            if ("file".equals(protocol)) {
                String filePath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
                findAndAddClassesInpPackageByFile(packageName, filePath, classes, filter);
            } else if ("jar".equals(protocol)) {
                JarFile jarFile = ((JarURLConnection)url.openConnection()).getJarFile();
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.charAt(0) == '/') {
                        name = name.substring(1);
                    }
                    if (name.startsWith(packageDirName)) {
                        int idx = name.lastIndexOf('/');
                        if (idx != -1) {
                            packageName = name.substring(0, idx).replace('/', '.');
                        }
                        if ((idx != -1) && (name.endsWith(".class") && (!entry.isDirectory()))) {
                            String className = name.substring(packageName.length() + 1, name.length() - 6);

                            tryAddClass(
                                    classes,
                                    Thread.currentThread().getContextClassLoader(),
                                    packageName + '.' + className,
                                    filter
                            );
                        }
                    }
                }
            }
        }

        Class<?>[] results = classes.toArray(new Class[0]);
        CACHE.put(rawName, results);
        return results;
    }

    private static void findAndAddClassesInpPackageByFile(String packageName, String filePath,
                                                          Set<Class<?>> classes, Predicate<Class<?>> filter) {
        File dir = new File(filePath);
        if ((!dir.exists()) || (!dir.isDirectory())) {
            return;
        }
        File[] dirFiles = dir.listFiles();
        if (dirFiles == null) {
            return;
        }

        for (File file : dirFiles) {
            if (file.isDirectory()) {
                findAndAddClassesInpPackageByFile((packageName + "." + file.getName()),
                        file.getAbsolutePath(), classes, filter);
            } else {
                String className = file.getName().substring(0, file.getName().length() - 6);
                tryAddClass(
                        classes,
                        Thread.currentThread().getContextClassLoader(),
                        (packageName + "." + className),
                        filter
                );
            }
        }
    }

    private static void tryAddClass(Set<Class<?>> classes, ClassLoader loader,
                                    String className, Predicate<Class<?>> filter) {
        try {
            Class<?> clazz = loader.loadClass(className);
            if (filter.test(clazz)) {
                classes.add(clazz);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
