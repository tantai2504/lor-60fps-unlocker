package com.lor.fpsunlocker;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LorConfigLocatorTest {

    @Test
    public void findsPlayerPrefsInsideSimulatedMetaWolfTree() throws Exception {
        File fakeRoot = Files.createTempDirectory("metawolf-sim").toFile();
        File metaWolfDir = new File(fakeRoot, LorConfigLocator.META_WOLF_PACKAGE);

        File ourAppDataDir = new File(metaWolfDir, "app_data_anon/com.lor.fpsunlocker/0");
        assertTrue(ourAppDataDir.mkdirs());

        File lorSharedPrefs = new File(metaWolfDir,
                "app_fozak_apks/" + LorConfigLocator.LOR_PACKAGE + "/0/shared_prefs");
        assertTrue(lorSharedPrefs.mkdirs());

        File prefsFile = new File(lorSharedPrefs, "com.riotgames.legendsofruneterra.v2.playerprefs.xml");
        PlayerPrefsPatcher.writeFileUtf8(prefsFile, "<map></map>");

        File backupFile = new File(lorSharedPrefs, "com.riotgames.legendsofruneterra.v2.playerprefs.xml.bak");
        PlayerPrefsPatcher.writeFileUtf8(backupFile, "<map></map>");

        LorConfigLocator.ScanResult scan = LorConfigLocator.scan(ourAppDataDir.getAbsolutePath());

        assertTrue(scan.insideCloner);
        assertEquals(1, scan.configFiles.size());
        assertEquals(prefsFile.getCanonicalPath(), scan.configFiles.get(0).getCanonicalPath());
    }

    @Test
    public void findsPlayerPrefsViaRecursiveScanInUnusualContainerName() throws Exception {
        File fakeRoot = Files.createTempDirectory("metawolf-nested").toFile();
        File metaWolfDir = new File(fakeRoot, LorConfigLocator.META_WOLF_PACKAGE);

        File ourAppDataDir = new File(metaWolfDir, "app_split/com.lor.fpsunlocker/0");
        assertTrue(ourAppDataDir.mkdirs());

        // Simulate a newer/different Meta Wolf directory structure
        File weirdPath = new File(metaWolfDir,
                "virtual_env/user0/" + LorConfigLocator.LOR_PACKAGE + "/shared_prefs");
        assertTrue(weirdPath.mkdirs());

        File prefsFile = new File(weirdPath, "com.riotgames.legendsofruneterra.v2.playerprefs.xml");
        PlayerPrefsPatcher.writeFileUtf8(prefsFile, "<map></map>");

        LorConfigLocator.ScanResult scan = LorConfigLocator.scan(ourAppDataDir.getAbsolutePath());

        assertEquals(1, scan.configFiles.size());
        assertEquals(prefsFile.getCanonicalPath(), scan.configFiles.get(0).getCanonicalPath());
    }

    @Test
    public void supportsManualCustomFilePath() throws Exception {
        File tempDir = Files.createTempDirectory("custom-path").toFile();
        File customFile = new File(tempDir, "com.riotgames.legendsofruneterra.v2.playerprefs.xml");
        PlayerPrefsPatcher.writeFileUtf8(customFile, "<map></map>");

        LorConfigLocator.ScanResult scan = LorConfigLocator.scan(null, customFile.getAbsolutePath());

        assertEquals(1, scan.configFiles.size());
        assertEquals(customFile.getCanonicalPath(), scan.configFiles.get(0).getCanonicalPath());
    }
}
