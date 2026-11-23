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

        // Simulated path of our helper app inside Meta Wolf
        File ourAppDataDir = new File(metaWolfDir, "app_data_anon/com.lor.fpsunlocker/0");
        assertTrue(ourAppDataDir.mkdirs());

        // Simulated LoR playerprefs in app_fozak_apks (different container from our app)
        File lorSharedPrefs = new File(metaWolfDir,
                "app_fozak_apks/" + LorConfigLocator.LOR_PACKAGE + "/0/shared_prefs");
        assertTrue(lorSharedPrefs.mkdirs());

        File prefsFile = new File(lorSharedPrefs, "com.riotgames.legendsofruneterra.v2.playerprefs.xml");
        PlayerPrefsPatcher.writeFileUtf8(prefsFile, "<map></map>");

        // Backup file should be ignored by scanner
        File backupFile = new File(lorSharedPrefs, "com.riotgames.legendsofruneterra.v2.playerprefs.xml.bak");
        PlayerPrefsPatcher.writeFileUtf8(backupFile, "<map></map>");

        LorConfigLocator.ScanResult scan = LorConfigLocator.scan(ourAppDataDir.getAbsolutePath());

        assertTrue(scan.insideCloner);
        assertEquals(1, scan.configFiles.size());
        assertEquals(prefsFile.getCanonicalPath(), scan.configFiles.get(0).getCanonicalPath());
    }
}
