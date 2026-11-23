package com.lor.fpsunlocker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
    public static final String LOR_PACKAGE_VN = "com.riotgames.legendsofruneterravn";
    public static final String META_WOLF_PACKAGE = "top.bienvenido.saas.i18n";

    private static final String[] KNOWN_LOR_PACKAGES = {
            LOR_PACKAGE,
            LOR_PACKAGE_VN
    };

    private static final String[] KNOWN_CONTAINERS = {
            "app_data_anon",
            "app_fozak_apks",
            "app_split"
    };

    private static final String[] KNOWN_PREFS_FILENAMES = {
            "com.riotgames.legendsofruneterra.v2.playerprefs.xml",
            "com.riotgames.legendsofruneterra.v2.playerprefs",
            "com.riotgames.legendsofruneterravn.v2.playerprefs.xml",
            "com.riotgames.legendsofruneterravn.v2.playerprefs"
    };

    // Skip heavy/irrelevant folders during recursive scan so scanning stays instant (<50ms)
    private static final Set<String> SKIP_DIR_NAMES = new HashSet<>(Arrays.asList(
            "cache", "code_cache", "lib", "oat", "dex", "dalvik-cache",
            "no_backup", "databases", "app_webview", "app_textures",
            "Unity", "il2cpp", "AssetBundles", "assets", "res", "obb"
    ));

    private static final int MAX_RECURSIVE_DEPTH = 6;
    private static final int MAX_VISITED_DIRS = 800;

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
     * Scans Meta Wolf / cloner directories (plus an optional custom user-provided path)
     * to find all accessible LoR playerprefs files.
     */
    public static ScanResult scan(String appDataDir) {
        return scan(appDataDir, null);
    }

    public static ScanResult scan(String appDataDir, String customPath) {
        boolean insideCloner = isRunningInsideCloner(appDataDir);
        Set<File> rootsToSearch = new LinkedHashSet<>();
        List<String> searchedRootPaths = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> seenFileKeys = new LinkedHashSet<>();
        List<File> foundFiles = new ArrayList<>();

        // 0. If user specified a custom file or folder path, check it first!
        if (customPath != null && !customPath.trim().isEmpty()) {
            String trimmed = customPath.trim();
            File customTarget = new File(trimmed);
            if (customTarget.isFile()) {
                addFoundFile(customTarget, seenFileKeys, foundFiles);
                searchedRootPaths.add(trimmed);
                if (foundFiles.isEmpty()) {
                    notes.add("File nhập thủ công không đọc được: " + trimmed);
                }
            } else if (customTarget.isDirectory()) {
                rootsToSearch.add(customTarget);
            } else {
                notes.add("Đường dẫn nhập thủ công không tồn tại hoặc không có quyền đọc: " + trimmed);
            }
        }

        // 1. Explicit known Meta Wolf base directories (scan BOTH /data/data and /data/user/0
        //    because virtualized IO hooks can treat them differently)
        addIfReadableDir(rootsToSearch, new File("/data/data/" + META_WOLF_PACKAGE));
        addIfReadableDir(rootsToSearch, new File("/data/user/0/" + META_WOLF_PACKAGE));

        // 2. Dynamically walk up ancestors of our own dataDir (supports Meta Wolf & other cloners)
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

        // Phase A: Fast targeted lookup on known container layouts
        Set<String> seenRootAbsPaths = new LinkedHashSet<>();
        for (File root : rootsToSearch) {
            String absRoot = root.getAbsolutePath();
            if (!seenRootAbsPaths.add(absRoot)) {
                continue;
            }
            searchedRootPaths.add(absRoot);

            for (String pkg : KNOWN_LOR_PACKAGES) {
                inspectLorPackageDir(new File(root, pkg), seenFileKeys, foundFiles);
            }

            for (String containerName : KNOWN_CONTAINERS) {
                File containerDir = new File(root, containerName);
                if (containerDir.isDirectory() && containerDir.canRead()) {
                    for (String pkg : KNOWN_LOR_PACKAGES) {
                        inspectLorPackageDir(new File(containerDir, pkg), seenFileKeys, foundFiles);
                    }
                }
            }

            // Also check direct shared_prefs if user pasted .../0 or .../shared_prefs
            inspectSharedPrefsDir(root, seenFileKeys, foundFiles);
            inspectSharedPrefsDir(new File(root, "shared_prefs"), seenFileKeys, foundFiles);
        }

        // Phase B: Bounded recursive scan if targeted lookup didn't find anything
        if (foundFiles.isEmpty()) {
            int[] visitedCounter = new int[]{0};
            Set<String> visitedDirs = new HashSet<>();
            for (File root : rootsToSearch) {
                recursiveSearch(root, 0, visitedCounter, visitedDirs, seenFileKeys, foundFiles);
            }
        }

        // Diagnostics when nothing is found (helps troubleshoot different Meta Wolf builds)
        if (foundFiles.isEmpty()) {
            if (!insideCloner) {
                notes.add("Ứng dụng đang chạy bên ngoài Meta Wolf nên không thể truy cập thư mục dữ liệu của LoR.");
            } else {
                notes.add("Chưa thấy file playerprefs của LoR trong Meta Wolf. Hãy kiểm tra: bạn đã Import LoR vào Meta Wolf, mở LoR đăng nhập vào sảnh chính, rồi Kill LoR chưa?");
                collectDirectoryDiagnostics(rootsToSearch, notes);
            }
        }

        return new ScanResult(insideCloner, foundFiles, searchedRootPaths, notes);
    }

    private static void recursiveSearch(File dir,
                                        int depth,
                                        int[] visitedCounter,
                                        Set<String> visitedDirs,
                                        Set<String> seenFileKeys,
                                        List<File> foundFiles) {
        if (dir == null || depth > MAX_RECURSIVE_DEPTH || visitedCounter[0] >= MAX_VISITED_DIRS) {
            return;
        }
        if (!dir.isDirectory() || !dir.canRead()) {
            return;
        }
        String canonical = safeCanonicalPath(dir);
        if (!visitedDirs.add(canonical)) {
            return;
        }
        visitedCounter[0]++;

        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }

        for (File entry : entries) {
            String name = entry.getName();
            if (entry.isFile()) {
                if (isLorPlayerPrefsFile(entry)) {
                    addFoundFile(entry, seenFileKeys, foundFiles);
                }
            } else if (entry.isDirectory()) {
                if (SKIP_DIR_NAMES.contains(name) || name.startsWith(".")) {
                    continue;
                }
                // Skip our own app's directory to avoid noise
                if (name.equals("com.lor.fpsunlocker")) {
                    continue;
                }
                recursiveSearch(entry, depth + 1, visitedCounter, visitedDirs, seenFileKeys, foundFiles);
            }
        }
    }

    private static void collectDirectoryDiagnostics(Set<File> rootsToSearch, List<String> notes) {
        for (File root : rootsToSearch) {
            String name = root.getName();
            if (name.equals(META_WOLF_PACKAGE) || name.equals("app_split")
                    || name.equals("app_data_anon") || name.equals("app_fozak_apks")) {
                File[] children = root.listFiles();
                if (children == null) {
                    notes.add("Thư mục " + root.getAbsolutePath() + " -> listFiles() = null (không có quyền liệt kê)");
                } else {
                    StringBuilder sb = new StringBuilder();
                    int limit = Math.min(children.length, 20);
                    for (int i = 0; i < limit; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(children[i].getName());
                    }
                    if (children.length > limit) {
                        sb.append(", ... (+").append(children.length - limit).append(")");
                    }
                    notes.add("Bên trong " + root.getAbsolutePath() + " (" + children.length + " mục): [" + sb + "]");
                }
            }
        }
    }

    private static void inspectLorPackageDir(File lorDir, Set<String> seenFileKeys, List<File> foundFiles) {
        if (!lorDir.isDirectory() || !lorDir.canRead()) {
            return;
        }

        inspectSharedPrefsDir(new File(lorDir, "shared_prefs"), seenFileKeys, foundFiles);

        File slot0 = new File(lorDir, "0");
        if (slot0.isDirectory()) {
            inspectSharedPrefsDir(new File(slot0, "shared_prefs"), seenFileKeys, foundFiles);
        }

        File[] subdirs = lorDir.listFiles();
        if (subdirs != null) {
            for (File sub : subdirs) {
                if (sub.isDirectory() && isNumeric(sub.getName())) {
                    inspectSharedPrefsDir(new File(sub, "shared_prefs"), seenFileKeys, foundFiles);
                }
            }
        }
    }

    private static void inspectSharedPrefsDir(File sharedPrefsDir, Set<String> seenFileKeys, List<File> foundFiles) {
        if (!sharedPrefsDir.isDirectory() || !sharedPrefsDir.canRead()) {
            return;
        }

        for (String name : KNOWN_PREFS_FILENAMES) {
            File candidate = new File(sharedPrefsDir, name);
            addFoundFile(candidate, seenFileKeys, foundFiles);
        }

        File[] files = sharedPrefsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isLorPlayerPrefsFile(f)) {
                    addFoundFile(f, seenFileKeys, foundFiles);
                }
            }
        }
    }

    private static boolean isLorPlayerPrefsFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".bak") || lower.endsWith(".bak2") || lower.endsWith(".orig_backup")
                || !lower.contains("playerprefs")) {
            return false;
        }
        String fullLower = file.getAbsolutePath().toLowerCase(Locale.ROOT);
        return lower.contains("legendsofruneterra") || fullLower.contains("legendsofruneterra");
    }

    private static void addFoundFile(File candidate, Set<String> seenFileKeys, List<File> foundFiles) {
        if (candidate.isFile() && candidate.canRead()) {
            String canonical = safeCanonicalPath(candidate);
            if (seenFileKeys.add(canonical)) {
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
