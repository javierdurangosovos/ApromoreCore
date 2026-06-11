/*-
 * #%L
 * This file is part of "Apromore Core".
 * %%
 * "Apromore" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 * #L%
 */

package org.apromore.osgihelper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class PatchOptionalImports {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new RuntimeException("Usage: PatchOptionalImports <usr-directory> <jar-file-name>");
        }
        Path jarPath = Paths.get(args[0], args[1]);
        if (!Files.isRegularFile(jarPath)) {
            System.out.println("Skipping missing JAR " + jarPath.getFileName());
            return;
        }
        patchJar(jarPath);
        System.out.println("Patched optional imports in " + jarPath.getFileName());
    }

    private static void patchJar(Path jarPath) throws IOException {
        Path temp = Files.createTempFile("patch-osgi-", ".jar");
        try (JarFile jarFile = new JarFile(jarPath.toFile());
             JarOutputStream out = new JarOutputStream(new FileOutputStream(temp.toFile()))) {

            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String imports = manifest.getMainAttributes().getValue("Import-Package");
                if (imports != null) {
                    imports = makeOptional(imports, "org.jivesoftware.smack;");
                    imports = makeOptional(imports, "org.jivesoftware.smack.filter;");
                    imports = makeOptional(imports, "org.jivesoftware.smack.packet;");
                    manifest.getMainAttributes().putValue("Import-Package", imports);
                }
            }

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (JarFile.MANIFEST_NAME.equalsIgnoreCase(entry.getName())) {
                    continue;
                }
                out.putNextEntry(new JarEntry(entry.getName()));
                try (InputStream in = jarFile.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                out.closeEntry();
            }

            if (manifest != null) {
                out.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
                manifest.write(out);
                out.closeEntry();
            }
        }
        Files.move(temp, jarPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String makeOptional(String imports, String packageName) {
        String optional = packageName.replace(";", ";resolution:=optional;");
        if (!imports.contains(packageName) || imports.contains(optional)) {
            return imports;
        }
        return imports.replace(packageName, optional);
    }
}
