package com.lor.fpsunlocker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Locates Legends of Runeterra's Unity playerprefs file inside Meta Wolf
 * (or compatible app-cloning sandboxes) without requiring root permissions.
 */
public final class LorConfigLocator {

    public static final String LOR_PACKAGE = "com.riotgames.legendsofruneterra";
    public static final String META_WOLF_PACKAGE = "top.bienvenido.saas.i18n";

    private static final String[] KNOWN_CONTAINERS = {
            "app_data_anon",
            "app_fozak_apks",
            "app_split"
    };

    private static final String[] KNOWN_PREFS_FILENAMES = {
            "com.riotgames.legendsofruneterra.v2.playerprefs.xml",
            "com.riotgames.legendsofruneterra.v2.playerprefs"
    };

    public static final class ScanResult {
        public final boolean insideCloner;
        public final List<File> configFiles;
        public final List<String> searchedRoots;
        public final List<String> diagnosticNotes;

        public ScanResult(boolean insideCloner,
                          List<File> configFiles,
                          List<String> searchedRoots,
                          List<String> diagnosticNotes) {
            this.insideCloner = insideCloner;
            this.configFiles = configFiles;
            this.searchedRoots = searchedRoots;
            this.diagnosticNotes = diagnosticNotes;
        }
    }

    private LorConfigLocator() {
    }

    /**
     * Checks whether this app appears to be running inside Meta Wolf or another cloner container.
     */
    public static boolean isRunningInsideCloner(String appDataDir) {
        if (appDataDir != null) {
            if (appDataDir.contains(META_WOLF_PACKAGE)) {
                return true;
            }
            for (String container : KNOWN_CONTAINERS) {
                if (appDataDir.contains("/" + container + "/") || appDataDir.contains("\\" + container + "\\")) {
                    return true;
                }
            }
        }
        File metaWolfData = new File("/data/data/" + META_WOLF_PACKAGE);
        if (metaWolfData.exists() && metaWolfData.canRead()) {
            return true;
        }
        File metaWolfUser0 = new File("/data/user/0/" + META_WOLF_PACKAGE);
        return metaWolfUser0.exists() && metaWolfUser0.canRead();
    }

    /**
     * Scans Meta Wolf / cloner directories to find all accessible LoR playerprefs files.
     */
    public static ScanResult scan(String appDataDir) {
        boolean insideCloner = isRunningInsideCloner(appDataDir);
        Set<File> rootsToSearch = new LinkedHashSet<>();
        List<String> searchedRootPaths = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // 1. Dynamically walk up ancestors of our own dataDir (supports Meta Wolf & other cloners)
        if (appDataDir != null && !appDataDir.trim().isEmpty()) {
            File current = new File(appDataDir);
            for (int depth = 0; depth < 5 && current != null; depth++) {
                String path = current.getPath();
                if (!isSystemRootPath(path) && current.isDirectory() && current.canRead()) {
                    rootsToSearch.add(current);
                }
                current = current.getParentFile();
            }
        }

        // 2. Explicit known Meta Wolf base directories
        addIfReadableDir(rootsToSearch, new File("/data/data/" + META_WOLF_PACKAGE));
        addIfReadableDir(rootsToSearch, new File("/data/user/0/" + META_WOLF_PACKAGE));

        Set<String> seenCanonicalPaths = new LinkedHashSet<>();
        List<File> foundFiles = new ArrayList<>();

        for (File root : rootsToSearch) {
            String canonicalRoot = safeCanonicalPath(root);
            if (searchedRootPaths.contains(canonicalRoot)) {
                continue;
            }
            searchedRootPaths.add(canonicalRoot);

            // Check if LoR package directory exists directly inside this root
            inspectLorPackageDir(new File(root, LOR_PACKAGE), seenCanonicalPaths, foundFiles);

            // Check known Meta Wolf container subdirectories
            for (String containerName : KNOWN_CONTAINERS) {
                File containerDir = new File(root, containerName);
                if (containerDir.isDirectory() && containerDir.canRead()) {
                    inspectLorPackageDir(new File(containerDir, LOR_PACKAGE), seenCanonicalPaths, foundFiles);
                }
            }

            // Dynamically check any other app_* container subdirectories (future-proofing)
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && child.getName().startsWith("app_") && child.canRead()) {
                        inspectLorPackageDir(new File(child, LOR_PACKAGE), seenCanonicalPaths, foundFiles);
                    }
                }
            }
        }

        if (foundFiles.isEmpty()) {
            if (!insideCloner) {
                notes.add("Ứng dụng đang chạy bên ngoài Meta Wolf nên không thể truy cập thư mục dữ liệu của LoR.");
            } else {
                notes.add("Đang ở trong Meta Wolf nhưng chưa thấy file playerprefs của LoR. Hãy mở LoR trong Meta Wolf, đăng nhập và tải xong dữ liệu, sau đó tắt hẳn LoR rồi quét lại.");
            }
        }

        return new ScanResult(insideCloner, foundFiles, searchedRootPaths, notes);
    }

    private static void inspectLorPackageDir(File lorDir, Set<String> seenCanonicalPaths, List<File> foundFiles) {
        if (!lorDir.isDirectory() || !lorDir.canRead()) {
            return;
        }

        // Direct shared_prefs inside package dir (some cloners omit /0/)
        inspectSharedPrefsDir(new File(lorDir, "shared_prefs"), seenCanonicalPaths, foundFiles);

        // Clone slot subdirectories like 0, 1, 2...
        File slot0 = new File(lorDir, "0");
        if (slot0.isDirectory()) {
            inspectSharedPrefsDir(new File(slot0, "shared_prefs"), seenCanonicalPaths, foundFiles);
        }

        File[] subdirs = lorDir.listFiles();
        if (subdirs != null) {
            for (File sub : subdirs) {
                if (sub.isDirectory() && isNumeric(sub.getName())) {
                    inspectSharedPrefsDir(new File(sub, "shared_prefs"), seenCanonicalPaths, foundFiles);
                }
            }
        }
    }

    private static void inspectSharedPrefsDir(File sharedPrefsDir, Set<String> seenCanonicalPaths, List<File> foundFiles) {
        if (!sharedPrefsDir.isDirectory() || !sharedPrefsDir.canRead()) {
            return;
        }

        // Check known exact filenames first
        for (String name : KNOWN_PREFS_FILENAMES) {
            File candidate = new File(sharedPrefsDir, name);
            addFoundFile(candidate, seenCanonicalPaths, foundFiles);
        }

        // Fallback: scan for any playerprefs file (excluding .bak backups)
        File[] files = sharedPrefsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String lower = f.getName().toLowerCase(Locale.ROOT);
                    if (lower.contains("playerprefs") && !lower.endsWith(".bak")) {
                        addFoundFile(f, seenCanonicalPaths, foundFiles);
                    }
                }
            }
        }
    }

    private static void addFoundFile(File candidate, Set<String> seenCanonicalPaths, List<File> foundFiles) {
        if (candidate.isFile() && candidate.canRead()) {
            String canonical = safeCanonicalPath(candidate);
            if (seenCanonicalPaths.add(canonical)) {
                foundFiles.add(candidate);
            }
        }
    }

    private static void addIfReadableDir(Set<File> target, File dir) {
        if (dir.isDirectory() && dir.canRead()) {
            target.add(dir);
        }
    }

    private static boolean isSystemRootPath(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.equals("/")
                || normalized.equals("/data")
                || normalized.equals("/data/data")
                || normalized.equals("/data/user")
                || normalized.equals("/data/user/0");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String safeCanonicalPath(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }
}
