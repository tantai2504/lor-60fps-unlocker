package com.lor.fpsunlocker;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class PlayerPrefsPatcherTest {

    private static final String SAMPLE_XML_WITHOUT_FPS =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                    + "<map>\n"
                    + "    <string name=\"live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.Audio.Volume.SFX\">1</string>\n"
                    + "    <string name=\"live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.AnnouncementModalLastSeen\">1737948992</string>\n"
                    + "    <int name=\"__UNITY_PLAYERPREFS_VERSION__\" value=\"1\" />\n"
                    + "    <string name=\"live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.General.Language\">en_us</string>\n"
                    + "    <int name=\"Screenmanager%20Fullscreen%20mode\" value=\"1\" />\n"
                    + "</map>\n";

    @Test
    public void insertsFps60PlusWhenMissingAndNoVideoQualityExists() {
        PlayerPrefsPatcher.PatchResult result =
                PlayerPrefsPatcher.patchXml(SAMPLE_XML_WITHOUT_FPS, PlayerPrefsPatcher.FpsMode.FPS_60_PLUS);

        assertTrue(result.success);
        assertEquals(1, result.entriesUpdated);
        assertTrue(result.patchedXml.contains(
                "    <string name=\"live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.Video.FPS\">fps_60_plus</string>"));
        // Preserves Unity percent-encoded keys verbatim
        assertTrue(result.patchedXml.contains("Screenmanager%20Fullscreen%20mode"));
    }

    @Test
    public void updatesExistingFpsInPlaceWithoutDuplicates() {
        PlayerPrefsPatcher.PatchResult step1 =
                PlayerPrefsPatcher.patchXml(SAMPLE_XML_WITHOUT_FPS, PlayerPrefsPatcher.FpsMode.FPS_60_PLUS);
        PlayerPrefsPatcher.PatchResult step2 =
                PlayerPrefsPatcher.patchXml(step1.patchedXml, PlayerPrefsPatcher.FpsMode.FPS_60);

        assertTrue(step2.success);
        assertTrue(step2.patchedXml.contains(
                "<string name=\"live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.Video.FPS\">fps_60</string>"));
        assertFalse(step2.patchedXml.contains("fps_60_plus"));

        PlayerPrefsPatcher.InspectResult inspected = PlayerPrefsPatcher.inspect(step2.patchedXml);
        assertEquals("fps_60", inspected.currentFpsValues.get("live.9c3abf96-e6d1-582c-8e1f-d688b74c6776.Video.FPS"));
    }

    @Test
    public void resetRemovesVideoFpsLineCleanly() {
        PlayerPrefsPatcher.PatchResult enabled =
                PlayerPrefsPatcher.patchXml(SAMPLE_XML_WITHOUT_FPS, PlayerPrefsPatcher.FpsMode.FPS_60);
        PlayerPrefsPatcher.PatchResult reset =
                PlayerPrefsPatcher.patchXml(enabled.patchedXml, PlayerPrefsPatcher.FpsMode.RESET_DEFAULT);

        assertTrue(reset.success);
        assertEquals(1, reset.entriesUpdated);
        assertFalse(reset.patchedXml.contains("Video.FPS"));
        assertTrue(reset.patchedXml.contains("Audio.Volume.SFX"));
    }

    @Test
    public void failsGracefullyWhenNoAccountPrefixPresent() {
        String emptyMap = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
                + "    <int name=\"__UNITY_PLAYERPREFS_VERSION__\" value=\"1\" />\n</map>\n";

        PlayerPrefsPatcher.PatchResult res =
                PlayerPrefsPatcher.patchXml(emptyMap, PlayerPrefsPatcher.FpsMode.FPS_60);

        assertFalse(res.success);
        assertEquals(0, res.entriesUpdated);
    }

    @Test
    public void applyToFileCreatesBackupAndPersistsChange() throws Exception {
        File tempDir = Files.createTempDirectory("lor-test").toFile();
        File prefsFile = new File(tempDir, "com.riotgames.legendsofruneterra.v2.playerprefs.xml");
        PlayerPrefsPatcher.writeFileUtf8(prefsFile, SAMPLE_XML_WITHOUT_FPS);

        PlayerPrefsPatcher.PatchResult res =
                PlayerPrefsPatcher.applyToFile(prefsFile, PlayerPrefsPatcher.FpsMode.FPS_60_PLUS);

        assertTrue(res.success);
        File backup = new File(tempDir, prefsFile.getName() + ".bak");
        assertTrue(backup.exists());
        assertEquals(SAMPLE_XML_WITHOUT_FPS, PlayerPrefsPatcher.readFileUtf8(backup));
        assertTrue(PlayerPrefsPatcher.readFileUtf8(prefsFile).contains("fps_60_plus"));
    }
}
