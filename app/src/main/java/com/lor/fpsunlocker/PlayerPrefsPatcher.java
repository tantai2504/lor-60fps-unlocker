package com.lor.fpsunlocker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safely reads, inspects, and patches Legends of Runeterra's Unity playerprefs XML file
 * while preserving all existing entries verbatim.
 */
public final class PlayerPrefsPatcher {

    public enum FpsMode {
        FPS_60("fps_60", "60 FPS (Ổn định)"),
        FPS_60_PLUS("fps_60_plus", "60+ FPS (90/120Hz)"),
        RESET_DEFAULT(null, "Mặc định (30 FPS / Dynamic)");

        public final String xmlValue;
        public final String displayName;

        FpsMode(String xmlValue, String displayName) {
            this.xmlValue = xmlValue;
            this.displayName = displayName;
        }
    }

    public static final class InspectResult {
        public final Set<String> accountPrefixes;
        public final Map<String, String> currentFpsValues;

        public InspectResult(Set<String> accountPrefixes, Map<String, String> currentFpsValues) {
            this.accountPrefixes = accountPrefixes;
            this.currentFpsValues = currentFpsValues;
        }

        public String describeCurrentState() {
            if (currentFpsValues.isEmpty()) {
                if (accountPrefixes.isEmpty()) {
                    return "Chưa có dữ liệu tài khoản (hãy đăng nhập LoR trước)";
                }
                return "Mặc định (30 FPS / Dynamic)";
            }
            Set<String> uniqueVals = new LinkedHashSet<>(currentFpsValues.values());
            if (uniqueVals.size() == 1) {
                String val = uniqueVals.iterator().next();
                if ("fps_60".equals(val)) {
                    return "60 FPS (fps_60)";
                }
                if ("fps_60_plus".equals(val)) {
                    return "60+ FPS / 90-120Hz (fps_60_plus)";
                }
                return "Khác (" + val + ")";
            }
            return "Nhiều cấu hình: " + uniqueVals;
        }
    }

    public static final class PatchResult {
        public final boolean success;
        public final String patchedXml;
        public final String message;
        public final int entriesUpdated;

        public PatchResult(boolean success, String patchedXml, String message, int entriesUpdated) {
            this.success = success;
            this.patchedXml = patchedXml;
            this.message = message;
            this.entriesUpdated = entriesUpdated;
        }
    }

    // Matches any prefix before .Video.VideoQuality or .Video.FPS
    private static final Pattern VIDEO_KEY_PREFIX_PATTERN =
            Pattern.compile("name=\"([^\"]+)\\.Video\\.(?:VideoQuality|FPS)\"");

    // Matches standard LoR account prefix like live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.
    private static final Pattern LIVE_UUID_PREFIX_PATTERN =
            Pattern.compile("name=\"(live\\.[0-9a-fA-F-]+)\\.");

    // Matches existing Video.FPS string tags
    private static final Pattern EXISTING_FPS_ENTRY_PATTERN =
            Pattern.compile("<string\\s+name=\"([^\"]+\\.Video\\.FPS)\">([^<]*)</string>");

    // Matches full line containing any Video.FPS string entry (for clean removal on reset)
    private static final Pattern EXISTING_FPS_LINE_PATTERN =
            Pattern.compile("[ \\t]*<string\\s+name=\"[^\"]+\\.Video\\.FPS\">[^<]*</string>\\r?\\n?");

    private PlayerPrefsPatcher() {
    }

    /**
     * Inspects XML content to find all account prefixes and current Video.FPS values.
     */
    public static InspectResult inspect(String xmlContent) {
        Set<String> prefixes = new LinkedHashSet<>();
        Map<String, String> fpsValues = new LinkedHashMap<>();

        if (xmlContent == null || xmlContent.isEmpty()) {
            return new InspectResult(prefixes, fpsValues);
        }

        Matcher videoMatcher = VIDEO_KEY_PREFIX_PATTERN.matcher(xmlContent);
        while (videoMatcher.find()) {
            prefixes.add(videoMatcher.group(1));
        }

        Matcher liveMatcher = LIVE_UUID_PREFIX_PATTERN.matcher(xmlContent);
        while (liveMatcher.find()) {
            prefixes.add(liveMatcher.group(1));
        }

        Matcher fpsMatcher = EXISTING_FPS_ENTRY_PATTERN.matcher(xmlContent);
        while (fpsMatcher.find()) {
            fpsValues.put(fpsMatcher.group(1), fpsMatcher.group(2));
        }

        return new InspectResult(prefixes, fpsValues);
    }

    /**
     * Patches the XML string in-memory according to the requested FPS mode.
     */
    public static PatchResult patchXml(String xmlContent, FpsMode mode) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new PatchResult(false, xmlContent,
                    "File cấu hình đang trống. Hãy mở LoR trong Meta Wolf, đăng nhập vào màn hình chính rồi tắt hẳn LoR.", 0);
        }

        if (!xmlContent.contains("</map>") && !xmlContent.contains("<map")) {
            return new PatchResult(false, xmlContent,
                    "File không đúng định dạng SharedPreferences XML (<map>).", 0);
        }

        if (mode == FpsMode.RESET_DEFAULT) {
            Matcher removeMatcher = EXISTING_FPS_LINE_PATTERN.matcher(xmlContent);
            int removedCount = 0;
            StringBuffer sb = new StringBuffer();
            while (removeMatcher.find()) {
                removedCount++;
                removeMatcher.appendReplacement(sb, "");
            }
            removeMatcher.appendTail(sb);
            if (removedCount == 0) {
                return new PatchResult(true, xmlContent,
                        "File vốn đã ở trạng thái mặc định (không có dòng Video.FPS).", 0);
            }
            return new PatchResult(true, sb.toString(),
                    "Đã xóa " + removedCount + " dòng Video.FPS và khôi phục về mặc định.", removedCount);
        }

        InspectResult inspection = inspect(xmlContent);
        if (inspection.accountPrefixes.isEmpty()) {
            return new PatchResult(false, xmlContent,
                    "Chưa tìm thấy mã tài khoản (live.xxxx...) trong file cấu hình.\n"
                            + "Hãy mở LoR trong Meta Wolf, đăng nhập vào sảnh chính, vào Cài đặt đổi mức đồ họa (ví dụ High -> Ultra), rồi tắt hẳn LoR và bấm lại.",
                    0);
        }

        String lineSep = xmlContent.contains("\r\n") ? "\r\n" : "\n";
        String indent = detectIndentation(xmlContent);
        String result = xmlContent;

        // Expand self-closing <map /> if somehow encountered
        if (!result.contains("</map>")) {
            result = result.replaceFirst("<map\\s*/>", "<map>" + lineSep + "</map>");
        }

        int updatedCount = 0;
        List<String> linesToInsert = new ArrayList<>();

        for (String prefix : inspection.accountPrefixes) {
            String fullKey = prefix + ".Video.FPS";
            Pattern exactKeyPattern = Pattern.compile(
                    "(<string\\s+name=\"" + Pattern.quote(fullKey) + "\">)[^<]*(</string>)");
            Matcher m = exactKeyPattern.matcher(result);
            if (m.find()) {
                result = m.replaceAll("$1" + Matcher.quoteReplacement(mode.xmlValue) + "$2");
            } else {
                linesToInsert.add(indent + "<string name=\"" + fullKey + "\">" + mode.xmlValue + "</string>");
            }
            updatedCount++;
        }

        if (!linesToInsert.isEmpty()) {
            StringBuilder insertionBlock = new StringBuilder();
            for (String line : linesToInsert) {
                insertionBlock.append(line).append(lineSep);
            }
            int closeTagIndex = result.lastIndexOf("</map>");
            if (closeTagIndex < 0) {
                return new PatchResult(false, xmlContent,
                        "Không tìm thấy thẻ đóng </map> trong file cấu hình.", 0);
            }
            String before = result.substring(0, closeTagIndex);
            String after = result.substring(closeTagIndex);
            if (!before.endsWith("\n")) {
                before = before + lineSep;
            }
            result = before + insertionBlock + after;
        }

        return new PatchResult(true, result,
                "Đã áp dụng " + mode.displayName + " cho " + updatedCount + " tài khoản trong file cấu hình!",
                updatedCount);
    }

    /**
     * Reads, backs up (first time only), patches, and writes the target file on disk.
     */
    public static PatchResult applyToFile(File file, FpsMode mode) {
        if (file == null || !file.exists()) {
            return new PatchResult(false, null, "Không tìm thấy file cấu hình.", 0);
        }
        if (!file.canRead() || !file.canWrite()) {
            return new PatchResult(false, null,
                    "Không có quyền đọc/ghi file: " + file.getAbsolutePath()
                            + "\nHãy chắc chắn bạn đang mở app này từ bên trong Meta Wolf.", 0);
        }

        try {
            String originalXml = readFileUtf8(file);
            PatchResult patchResult = patchXml(originalXml, mode);
            if (!patchResult.success) {
                return patchResult;
            }

            // CRITICAL: Never create a "<file>.bak" in shared_prefs!
            // Android's SharedPreferencesImpl.loadFromDisk() treats "<file>.bak" as a crash-recovery
            // backup and will DELETE our patched XML and restore the ".bak" file on next app launch!
            File androidRecoveryBak = new File(file.getParentFile(), file.getName() + ".bak");
            if (androidRecoveryBak.exists()) {
                // Remove any stale .bak so SharedPreferencesImpl won't overwrite our edit
                //noinspection ResultOfMethodCallIgnored
                androidRecoveryBak.delete();
            }

            // Create a safe one-time backup with a non-".bak" extension
            File safeBackupFile = new File(file.getParentFile(), file.getName() + ".orig_backup");
            if (!safeBackupFile.exists()) {
                try {
                    writeFileUtf8(safeBackupFile, originalXml);
                } catch (IOException ignored) {
                    // Non-fatal if backup creation fails
                }
            }

            writeFileUtf8(file, patchResult.patchedXml);

            // Re-read to verify write persisted cleanly
            String verifyXml = readFileUtf8(file);
            InspectResult verified = inspect(verifyXml);
            if (mode != FpsMode.RESET_DEFAULT && !verified.currentFpsValues.containsValue(mode.xmlValue)) {
                return new PatchResult(false, verifyXml,
                        "Đã ghi file nhưng kiểm tra lại chưa thấy giá trị " + mode.xmlValue + ". Hãy tắt hẳn LoR trong Meta Wolf rồi thử lại.", 0);
            }

            return patchResult;
        } catch (IOException e) {
            return new PatchResult(false, null, "Lỗi đọc/ghi file: " + e.getMessage(), 0);
        }
    }

    public static String readFileUtf8(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static void writeFileUtf8(File file, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(bytes);
            fos.flush();
        }
    }

    private static String detectIndentation(String xmlContent) {
        Matcher m = Pattern.compile("\\n([ \\t]+)<(?:string|int|boolean|float|long)\\s").matcher(xmlContent);
        if (m.find()) {
            return m.group(1);
        }
        return "    ";
    }
}
