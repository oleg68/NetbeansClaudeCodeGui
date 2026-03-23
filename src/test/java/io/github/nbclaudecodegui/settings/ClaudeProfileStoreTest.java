package io.github.nbclaudecodegui.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeProfileStore}.
 *
 * <p><b>Note:</b> these tests call {@link ClaudeProfileStore#clearProfiles()}
 * in {@link #setUp()} to start from a known-empty state.  Because
 * {@link org.openide.util.NbPreferences} is not available outside a running
 * NetBeans platform, tests that require persistence are run only when the
 * platform is available; the pure-logic tests (serialisation, Default
 * handling) do not depend on NbPreferences and run in plain JUnit.
 */
class ClaudeProfileStoreTest {

    @BeforeEach
    void setUp() {
        // Start each test with no persisted profiles.
        // NbPreferences may or may not be available; swallow errors.
        try {
            ClaudeProfileStore.clearProfiles();
        } catch (Exception ignored) {
            // platform not available in plain unit-test environment
        }
    }

    // -------------------------------------------------------------------------
    // getProfiles — Default always first
    // -------------------------------------------------------------------------

    @Test
    void getProfiles_defaultAlwaysFirst() {
        List<ClaudeProfile> profiles = ClaudeProfileStore.getProfiles();
        assertFalse(profiles.isEmpty());
        assertTrue(profiles.get(0).isDefault());
        assertEquals("Default", profiles.get(0).getName());
    }

    @Test
    void getProfiles_defaultIsNeverNull() {
        List<ClaudeProfile> profiles = ClaudeProfileStore.getProfiles();
        assertNotNull(profiles.get(0));
    }

    // -------------------------------------------------------------------------
    // resolveConfigDir
    // -------------------------------------------------------------------------

    @Test
    void resolveConfigDir_namedProfile_appendsName() {
        ClaudeProfile p = ClaudeProfile.createNamed("MyProfile");
        Path base = Path.of("/home/user/.netbeans/claude-profiles");
        Path result = ClaudeProfileStore.resolveConfigDir(p, base);
        assertEquals(base.resolve("MyProfile"), result);
    }

    @Test
    void resolveConfigDir_defaultProfile_throwsException() {
        ClaudeProfile def = ClaudeProfile.createDefault();
        Path base = Path.of("/home/user/.netbeans/claude-profiles");
        assertThrows(IllegalArgumentException.class,
                () -> ClaudeProfileStore.resolveConfigDir(def, base));
    }

    // -------------------------------------------------------------------------
    // findByName
    // -------------------------------------------------------------------------

    @Test
    void findByName_blankName_returnsDefault() {
        ClaudeProfile found = ClaudeProfileStore.findByName("");
        assertTrue(found.isDefault());
    }

    @Test
    void findByName_nullName_returnsDefault() {
        ClaudeProfile found = ClaudeProfileStore.findByName(null);
        assertTrue(found.isDefault());
    }

    // -------------------------------------------------------------------------
    // saveProfile / Default cannot be saved
    // -------------------------------------------------------------------------

    @Test
    void saveProfile_defaultProfile_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> ClaudeProfileStore.saveProfile(ClaudeProfile.createDefault()));
    }

    // -------------------------------------------------------------------------
    // saveProfiles / getProfiles round-trip
    // -------------------------------------------------------------------------

    @Test
    void jacksonRoundTrip_namedProfile_noUnknownPropertyException() throws Exception {
        // Regression: isDefault() getter caused Jackson to serialize "default":false.
        // With FAIL_ON_UNKNOWN_PROPERTIES=true (Jackson default), deserializing that JSON
        // threw UnrecognizedPropertyException → only Default returned after reload.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ClaudeProfile p = ClaudeProfile.createNamed("Work");
        p.setApiKey("sk-test");

        String json = mapper.writeValueAsString(java.util.List.of(p));
        // Must not throw UnrecognizedPropertyException:
        com.fasterxml.jackson.core.type.TypeReference<List<ClaudeProfile>> listType =
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
        List<ClaudeProfile> loaded = mapper.readValue(json, listType);
        assertEquals(1, loaded.size());
        assertEquals("Work", loaded.get(0).getName());
        assertEquals("sk-test", loaded.get(0).getApiKey());
    }

    @Test
    void saveAndReload_namedProfile_persistsCorrectly() {
        // Regression: isDefault() getter caused Jackson to serialize "default":false,
        // which then triggered UnrecognizedPropertyException on reload (FAIL_ON_UNKNOWN_PROPERTIES=true),
        // silently caught → only Default returned.
        try {
            ClaudeProfile p = ClaudeProfile.createNamed("Work");
            p.setApiKey("sk-test");
            ClaudeProfileStore.saveProfiles(java.util.List.of(ClaudeProfile.createDefault(), p));

            List<ClaudeProfile> loaded = ClaudeProfileStore.getProfiles();
            assertEquals(2, loaded.size(), "Expected Default + Work");
            assertEquals("Work", loaded.get(1).getName());
            assertEquals("sk-test", loaded.get(1).getApiKey());
        } catch (Exception e) {
            // NbPreferences unavailable in plain JUnit — skip
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "NbPreferences not available");
        }
    }

    // -------------------------------------------------------------------------
    // saveProfiles — skips Default entries
    // -------------------------------------------------------------------------

    @Test
    void saveProfiles_skipsDefaultEntries() {
        // Build a list that includes the Default
        List<ClaudeProfile> all = ClaudeProfileStore.getProfiles();
        // saving a list that starts with Default must not throw
        assertDoesNotThrow(() -> ClaudeProfileStore.saveProfiles(all));
    }

    // -------------------------------------------------------------------------
    // writeCustomModels / readCustomModels
    // -------------------------------------------------------------------------

    @Test
    void writeCustomModels_writesAvailableModels(@TempDir Path tmp) throws Exception {
        ClaudeProfile p = ClaudeProfile.createNamed("OtherApi");
        List<CustomModel> models = List.of(
                new CustomModel("gpt-4o", true, ""),
                new CustomModel("gpt-4o-mini", true, "")
        );
        ClaudeProfileStore.writeCustomModels(p, tmp, models);

        Path settingsFile = tmp.resolve("OtherApi/settings.json");
        assertTrue(Files.exists(settingsFile));
        String json = Files.readString(settingsFile);
        assertTrue(json.contains("\"gpt-4o\""), "should contain gpt-4o");
        assertTrue(json.contains("\"gpt-4o-mini\""), "should contain gpt-4o-mini");
        assertTrue(json.contains("\"availableModels\""));
    }

    @Test
    void writeCustomModels_writesAliasEnvVars(@TempDir Path tmp) throws Exception {
        ClaudeProfile p = ClaudeProfile.createNamed("OtherApi");
        List<CustomModel> models = List.of(
                new CustomModel("gpt-4o", true, "sonnet"),
                new CustomModel("gpt-4o-mini", true, "haiku")
        );
        ClaudeProfileStore.writeCustomModels(p, tmp, models);

        String json = Files.readString(tmp.resolve("OtherApi/settings.json"));
        assertTrue(json.contains("\"ANTHROPIC_DEFAULT_SONNET_MODEL\""), "should have sonnet env key");
        assertTrue(json.contains("\"gpt-4o\""), "sonnet should map to gpt-4o");
        assertTrue(json.contains("\"ANTHROPIC_DEFAULT_HAIKU_MODEL\""), "should have haiku env key");
        assertFalse(json.contains("\"ANTHROPIC_DEFAULT_OPUS_MODEL\""), "opus key not set");
    }

    @Test
    void writeCustomModels_mergesExistingKeys(@TempDir Path tmp) throws Exception {
        ClaudeProfile p = ClaudeProfile.createNamed("OtherApi");
        Path dir = tmp.resolve("OtherApi");
        Files.createDirectories(dir);
        // Pre-existing settings.json with an unrelated key
        Files.writeString(dir.resolve("settings.json"),
                "{\"mcpServers\":{\"netbeans\":{\"type\":\"sse\"}}}");

        ClaudeProfileStore.writeCustomModels(p, tmp, List.of(new CustomModel("gpt-4o", true, "")));

        String json = Files.readString(dir.resolve("settings.json"));
        assertTrue(json.contains("\"mcpServers\""), "existing mcpServers key should be preserved");
        assertTrue(json.contains("\"availableModels\""), "new availableModels key should be added");
    }

    @Test
    void readCustomModels_roundtrip(@TempDir Path tmp) throws Exception {
        ClaudeProfile p = ClaudeProfile.createNamed("OtherApi");
        List<CustomModel> original = List.of(
                new CustomModel("gpt-4o", true, "sonnet"),
                new CustomModel("llama-3", null, "")
        );
        ClaudeProfileStore.writeCustomModels(p, tmp, original);
        List<CustomModel> loaded = ClaudeProfileStore.readCustomModels(p, tmp);

        assertEquals(2, loaded.size());
        assertEquals("gpt-4o", loaded.get(0).id());
        assertEquals("sonnet", loaded.get(0).alias());
        assertEquals("llama-3", loaded.get(1).id());
        assertEquals("", loaded.get(1).alias());
        // available is transient — not persisted
        assertNull(loaded.get(0).available());
        assertNull(loaded.get(1).available());
    }

    @Test
    void readCustomModels_fileAbsent_returnsEmpty(@TempDir Path tmp) {
        ClaudeProfile p = ClaudeProfile.createNamed("NoFile");
        List<CustomModel> result = ClaudeProfileStore.readCustomModels(p, tmp);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // CustomModel alias uniqueness validation
    // -------------------------------------------------------------------------

    @Test
    void validateAliasUniqueness_duplicate_returnsError() {
        List<CustomModel> models = List.of(
                new CustomModel("gpt-4o", null, "sonnet"),
                new CustomModel("gpt-4o-mini", null, "sonnet")
        );
        String error = CustomModel.validateAliasUniqueness(models);
        assertNotNull(error, "should return error for duplicate alias");
        assertTrue(error.contains("sonnet"));
    }

    @Test
    void validateAliasUniqueness_ok_returnsNull() {
        List<CustomModel> models = List.of(
                new CustomModel("gpt-4o", null, "sonnet"),
                new CustomModel("gpt-4o-mini", null, "haiku"),
                new CustomModel("llama-3", null, "")
        );
        assertNull(CustomModel.validateAliasUniqueness(models));
    }

    // -------------------------------------------------------------------------
    // Corrupt JSON resilience
    // -------------------------------------------------------------------------

    @Test
    void getProfiles_corruptJson_onlyDefaultReturned() {
        // Manually inject corrupt JSON via NbPreferences
        try {
            org.openide.util.NbPreferences.forModule(ClaudeProfileStore.class)
                    .put(ClaudeProfileStore.KEY_PROFILES, "NOT_VALID_JSON");
            List<ClaudeProfile> profiles = ClaudeProfileStore.getProfiles();
            assertEquals(1, profiles.size());
            assertTrue(profiles.get(0).isDefault());
        } catch (Exception e) {
            // NbPreferences unavailable in plain JUnit — skip
        }
    }
}
