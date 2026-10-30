package com.winlator.cmod.core;

import java.io.File;

/** RetroForge: the two helpers from Winlator's FileUtils that the X server's socket setup uses. */
public abstract class FileUtils {
    public static String getDirname(String path) {
        if (path == null) return "";
        path = path.replaceAll("/+$", "");
        int index = path.lastIndexOf("/");
        return index > 0 ? path.substring(0, index) : "";
    }

    public static boolean delete(File targetFile) {
        if (targetFile == null || !targetFile.exists()) return false;
        File[] files = targetFile.isDirectory() && !isSymlink(targetFile) ? targetFile.listFiles() : null;
        if (files != null) for (File file : files) delete(file);
        return targetFile.delete();
    }

    private static boolean isSymlink(File file) {
        try {
            return !file.getCanonicalFile().equals(file.getAbsoluteFile());
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
