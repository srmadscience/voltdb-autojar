package org.voltdb.autojar;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;

/**
 * 
 * Finds all classes in a package that have the
 * 'org.voltdb.autojar.IsAVoltDBProcedure' or 'IsNeededByAVoltDBProcedure'
 * annotation and loads them into the database. This means you can avoid
 * manually creating JAR files and loading them with sqlcmd.
 * 
 * @author VoltDB
 *
 */
public class AutoJar {

    /**
     * Private constructor - we'll never need more than 1 instance of this..
     */
    private static AutoJar instance = null;

    public static AutoJar getInstance() {

        if (instance == null) {
            instance = new AutoJar();
        }

        return instance;
    }

    /**
     * Load all classes in package 'packagename' that have the IsAVoltDBProcedure or
     * IsNeededByAVoltDBProcedure annotation into VoltDB.
     * 
     * @param packageName - e.g. 'com.mybiz.testprocs'
     * @param c           - A VoltDB client object.
     * @param filename    - optional, in case you want to keep JAR file afterwards.
     * @return true if we could create the JAR file and load it.
     * @throws Exception
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean load(String packageName, Client c, String filename) throws Exception {

        boolean retCode = true;
        boolean delFileAfter = true;

        File tempFile = null;

        // Sort out JAR file..
        if (filename != null) {

            delFileAfter = false;
            tempFile = new File(filename);
        } else {
            tempFile = File.createTempFile("AutoJar", ".jar");
        }
        msg("Creating JAR file for " + packageName + " in " + tempFile.getAbsolutePath());

        // Get list of all classes we can see, assuming we are in an IDE
        Class[] matchingClasses = getClasses(packageName);

        // We didn't find anything, so see if we are in a JAR file...
        if (matchingClasses.length == 0) {
            matchingClasses = getClassesJAR(packageName);
        }

        if (matchingClasses.length == 0) {
            retCode = false;
            msg("No classes found...");

        } else {

            // Create a JAR file and get ready to write to it..
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            JarOutputStream newJarFile = new JarOutputStream(new FileOutputStream(tempFile), manifest);

            // Find all classes we can see that have the 'IsAVoltDBProcedure'
            // annotation...
            for (int i = 0; i < matchingClasses.length; i++) {

                msg("Checking class " + matchingClasses[i].getCanonicalName().replace(".", "/") + ".class");

                if (matchingClasses[i].isAnnotationPresent(IsAVoltDBProcedure.class)
                        || matchingClasses[i].isAnnotationPresent(IsNeededByAVoltDBProcedure.class)) {

                    // Add to our JAR file...
                    msg("Adding class " + matchingClasses[i].getCanonicalName().replace(".", "/") + ".class");
                    InputStream is = getClass().getClassLoader()
                            .getResourceAsStream(matchingClasses[i].getCanonicalName().replace(".", "/") + ".class");

                    add(matchingClasses[i].getCanonicalName().replace(".", "/") + ".class", is, newJarFile);
                }
            }

            newJarFile.close();

            // Now load our file into a byte array so we can feed it to
            // UpdateClasses
            byte[] jarFileContents = new byte[(int) tempFile.length()];
            FileInputStream fis = new FileInputStream(tempFile);
            fis.read(jarFileContents);
            fis.close();

            msg("Calling @UpdateClasses to load JAR file containing procedures");
            ClientResponse cr = c.callProcedure("@UpdateClasses", jarFileContents, null);

            if (cr.getStatus() != ClientResponse.SUCCESS) {
                msg("Attempt to execute UpdateClasses failed:" + cr.getStatusString());
                throw new Exception("Attempt to execute UpdateClasses failed:" + cr.getStatusString());
            } else {
                msg(packageName + " classes loaded...");
            }
        }

        if (delFileAfter) {
            tempFile.delete();
            msg("Deleted " + tempFile.getAbsolutePath());
        }

        return retCode;
    }

    /**
     * Obtained From:
     * https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection
     * 
     * Scans all classes accessible from the context class loader which belong to
     * the given package and subpackages.
     *
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    @SuppressWarnings("rawtypes")
    private Class[] getClasses(String packageName) throws ClassNotFoundException, IOException {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class> classes = new ArrayList<Class>();
        for (File directory : dirs) {
            msg("Searching " + directory + " for  " + packageName);
            classes.addAll(findClasses(directory, packageName));

        }

        return classes.toArray(new Class[classes.size()]);
    }

    /**
     * Derived from
     * https://stackoverflow.com/questions/1429172/how-to-list-the-files-inside-a-jar-file
     * 
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    @SuppressWarnings("rawtypes")
    private Class[] getClassesJAR(String packageName) throws ClassNotFoundException, IOException {

        ArrayList<Class> classes = new ArrayList<Class>();
        CodeSource src = AutoJar.class.getProtectionDomain().getCodeSource();
        if (src != null) {
            URL jar = src.getLocation();
            ZipInputStream zip = new ZipInputStream(jar.openStream());
            while (true) {
                ZipEntry e = zip.getNextEntry();
                if (e == null)
                    break;
                String name = e.getName();
                if (name.startsWith(packageName.replace(".", "/")) && name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace("/", ".");
                    classes.add(Class.forName(className));

                }
            }
        } else {
            msg("Unable to find " + packageName);
        }

        return classes.toArray(new Class[classes.size()]);
    }

    /**
     * Obtained from:
     * https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection
     * 
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base
     *                    directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("rawtypes")
    private List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {

        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists()) {
            return classes;
        }

        File[] files = directory.listFiles();
        for (File file : files) {
            msg("Found " + file.getAbsolutePath());
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                classes.add(
                        Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
    }

    /**
     * Add an entry to our JAR file.
     * 
     * @param fileName
     * @param source
     * @param target
     * @throws IOException
     */
    private void add(String fileName, InputStream source, JarOutputStream target) throws IOException {
        BufferedInputStream in = null;
        try {

            JarEntry entry = new JarEntry(fileName.replace("\\", "/"));
            entry.setTime(System.currentTimeMillis());
            target.putNextEntry(entry);
            in = new BufferedInputStream(source);

            byte[] buffer = new byte[1024];
            while (true) {
                int count = in.read(buffer);
                if (count == -1) {
                    break;
                }

                target.write(buffer, 0, count);
            }
            target.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }

        }
    }

    public static void msg(String message) {

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        System.out.println(strDate + ":" + message);

    }

}
