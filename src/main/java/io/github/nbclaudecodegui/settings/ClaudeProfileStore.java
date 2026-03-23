package io.github.nbclaudecodegui.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import org.openide.util.NbPreferences;

/**
 * Persistence layer for {@link ClaudeProfile} objects.
 *
 * <p>Profiles are serialised as a JSON array and stored under a single
 * {@link org.openide.util.NbPreferences} key ({@value #KEY_PROFILES}).
 * The built-in <em>Default</em> profile is never persisted — it is
 * constructed programmatically and always returned as the first element
 * of {@link #getProfiles()}.
 *
 * <p>On any JSON parse error the store falls back gracefully: only the
 * Default profile is returned and no exception is thrown.
 *
 * <h2>Thread safety</h2>
 * All public methods may be called from any thread.  Reads and writes
 * go through {@link NbPreferences} which is itself thread-safe.
 */
public final class ClaudeProfileStore {

    private static final Logger LOG =
            Logger.getLogger(ClaudeProfileStore.class.getName());

    /** NbPreferences key under which the JSON array is stored. */
    public static final String KEY_PROFILES = "profiles";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final TypeReference<List<ClaudeProfile>> LIST_TYPE =
            new TypeReference<>() {};

    private ClaudeProfileStore() {}

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns all profiles, with the Default profile always first.
     *
     * <p>If the stored JSON is missing or corrupt, only the Default profile
     * is returned.
     *
     * @return mutable list; first element is always the Default profile
     */
    public static List<ClaudeProfile> getProfiles() {
        List<ClaudeProfile> result = new ArrayList<>();
        result.add(ClaudeProfile.createDefault());

        String json = NbPreferences.forModule(ClaudeProfileStore.class)
                .get(KEY_PROFILES, "[]");
        try {
            List<ClaudeProfile> stored = MAPPER.readValue(json, LIST_TYPE);
            result.addAll(stored);
            LOG.fine(() -> "Loaded " + stored.size() + " profile(s): " + profileNames(stored));
        } catch (Exception e) {
            LOG.warning("Could not parse stored profiles JSON, returning only Default. Cause: " + e);
        }

        return result;
    }

    /**
     * Looks up a profile by name.
     *
     * @param name profile name, or {@code null}/{@code ""} for Default
     * @return the matching profile, or the Default profile if not found
     */
    public static ClaudeProfile findByName(String name) {
        if (name == null || name.isBlank()) {
            return ClaudeProfile.createDefault();
        }
        for (ClaudeProfile p : getProfiles()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return ClaudeProfile.createDefault();
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Persists the given list of profiles (excluding the Default profile,
     * which is always built programmatically).
     *
     * <p>Any profile whose {@link ClaudeProfile#isDefault()} returns
     * {@code true} is silently skipped.
     *
     * @param profiles the profiles to save; {@code null} clears the list
     */
    public static void saveProfiles(List<ClaudeProfile> profiles) {
        List<ClaudeProfile> toSave = new ArrayList<>();
        if (profiles != null) {
            for (ClaudeProfile p : profiles) {
                if (!p.isDefault()) {
                    toSave.add(p);
                }
            }
        }
        try {
            String json = MAPPER.writeValueAsString(toSave);
            NbPreferences.forModule(ClaudeProfileStore.class)
                    .put(KEY_PROFILES, json);
            LOG.fine(() -> "Saved " + toSave.size() + " profile(s): " + profileNames(toSave));
        } catch (Exception e) {
            LOG.severe("Could not serialise profiles: " + e.getMessage());
        }
    }

    /**
     * Saves a single profile, replacing the existing entry with the same id
     * or appending it if not found.
     *
     * @param profile profile to save; must not be the Default profile
     * @throws IllegalArgumentException if {@code profile.isDefault()} is {@code true}
     */
    public static void saveProfile(ClaudeProfile profile) {
        if (profile.isDefault()) {
            throw new IllegalArgumentException("The Default profile cannot be persisted.");
        }
        List<ClaudeProfile> all = getProfiles();
        List<ClaudeProfile> named = new ArrayList<>();
        boolean replaced = false;
        for (ClaudeProfile p : all) {
            if (p.isDefault()) continue;
            if (p.getId().equals(profile.getId())) {
                named.add(profile);
                replaced = true;
            } else {
                named.add(p);
            }
        }
        if (!replaced) {
            named.add(profile);
        }
        saveProfiles(named);
    }

    /**
     * Removes the profile with the given id from persistent storage.
     *
     * <p>Silently does nothing if no such profile exists or if
     * {@code id} is blank (Default).
     *
     * @param id the profile id to remove
     */
    public static void removeProfile(String id) {
        if (id == null || id.isBlank()) return;
        List<ClaudeProfile> all = getProfiles();
        List<ClaudeProfile> updated = new ArrayList<>();
        for (ClaudeProfile p : all) {
            if (!p.isDefault() && !p.getId().equals(id)) {
                updated.add(p);
            }
        }
        saveProfiles(updated);
    }

    /**
     * Clears all persisted profiles (leaves only the Default in memory).
     */
    public static void clearProfiles() {
        try {
            NbPreferences.forModule(ClaudeProfileStore.class)
                    .remove(KEY_PROFILES);
            NbPreferences.forModule(ClaudeProfileStore.class).flush();
        } catch (BackingStoreException e) {
            LOG.warning("Could not flush prefs after clear: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Config-dir resolution
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Custom models — settings.json helpers
    // -------------------------------------------------------------------------

    private static final String[] ALIAS_NAMES = {"sonnet", "opus", "haiku"};
    private static final String[] ALIAS_ENV_KEYS = {
        "ANTHROPIC_DEFAULT_SONNET_MODEL",
        "ANTHROPIC_DEFAULT_OPUS_MODEL",
        "ANTHROPIC_DEFAULT_HAIKU_MODEL"
    };

    /**
     * Reads {@code availableModels} and alias env vars from
     * {@code CLAUDE_CONFIG_DIR/settings.json} for the given profile.
     *
     * @return list of {@link CustomModel} objects; empty list if the file is
     *         absent or contains no model data
     */
    public static List<CustomModel> readCustomModels(ClaudeProfile profile, Path profilesDir) {
        Path settingsFile = resolveSettingsDir(profile, profilesDir).resolve("settings.json");
        if (!Files.exists(settingsFile)) {
            LOG.fine("readCustomModels: settings.json not found at " + settingsFile
                    + ", returning empty list");
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
            ObjectNode root = (ObjectNode) MAPPER.readTree(json);

            // Read alias map: env.ANTHROPIC_DEFAULT_*_MODEL → alias
            Map<String, String> idToAlias = new LinkedHashMap<>();
            if (root.has("env")) {
                ObjectNode env = (ObjectNode) root.get("env");
                for (int i = 0; i < ALIAS_NAMES.length; i++) {
                    if (env.has(ALIAS_ENV_KEYS[i])) {
                        idToAlias.put(env.get(ALIAS_ENV_KEYS[i]).asText(), ALIAS_NAMES[i]);
                    }
                }
            }

            List<CustomModel> result = new ArrayList<>();
            if (root.has("availableModels")) {
                for (var node : root.get("availableModels")) {
                    String id = node.asText();
                    String alias = idToAlias.getOrDefault(id, "");
                    result.add(new CustomModel(id, null, alias));
                }
            }
            // Also include any aliased models not already present in availableModels.
            // This covers OTHER_API (proxy) profiles where availableModels is intentionally
            // absent: the alias env vars are the only source of model IDs in that case.
            Set<String> seen = new LinkedHashSet<>();
            for (CustomModel m : result) seen.add(m.id());
            for (Map.Entry<String, String> e : idToAlias.entrySet()) {
                if (!seen.contains(e.getKey())) {
                    result.add(new CustomModel(e.getKey(), null, e.getValue()));
                }
            }

            LOG.fine("readCustomModels: read " + result.size() + " models from " + settingsFile);
            return result;
        } catch (Exception e) {
            LOG.warning("readCustomModels: failed to read " + settingsFile + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Writes (merges) {@code availableModels} and alias env vars into
     * {@code CLAUDE_CONFIG_DIR/settings.json} for the given profile.
     * Existing keys not managed by this method are preserved.
     *
     * @param models list of models to write; empty list clears the managed keys
     */
    public static void writeCustomModels(ClaudeProfile profile, Path profilesDir,
                                         List<CustomModel> models) throws IOException {
        Path configDir = resolveSettingsDir(profile, profilesDir);
        Files.createDirectories(configDir);
        Path settingsFile = configDir.resolve("settings.json");

        String existing = Files.exists(settingsFile)
                ? Files.readString(settingsFile, StandardCharsets.UTF_8)
                : "{}";

        try {
            ObjectNode root = existing.isBlank()
                    ? MAPPER.createObjectNode()
                    : (ObjectNode) MAPPER.readTree(existing);

            // availableModels — only written for native Claude API profiles.
            // For OTHER_API (proxy) profiles the IDs are in a provider-specific format
            // (e.g. "anthropic/claude-sonnet-4.6") that CC's model-picker validation
            // does not understand, so the picker would show nothing.  Remove the key
            // so CC falls back to its built-in model list.
            if (profile.computeConnectionType() == ClaudeProfile.ConnectionType.OTHER_API) {
                root.remove("availableModels");
            } else {
                ArrayNode available = MAPPER.createArrayNode();
                for (CustomModel m : models) {
                    available.add(m.id());
                }
                root.set("availableModels", available);
            }

            // env — merge/replace alias keys
            ObjectNode env = root.has("env") ? (ObjectNode) root.get("env") : MAPPER.createObjectNode();
            // Build alias→id map from current models
            Map<String, String> aliasToId = new LinkedHashMap<>();
            for (CustomModel m : models) {
                if (m.alias() != null && !m.alias().isBlank()) {
                    aliasToId.put(m.alias(), m.id());
                }
            }
            // Update or remove each known alias env key
            for (int i = 0; i < ALIAS_NAMES.length; i++) {
                String id = aliasToId.get(ALIAS_NAMES[i]);
                if (id != null) {
                    env.put(ALIAS_ENV_KEYS[i], id);
                } else {
                    env.remove(ALIAS_ENV_KEYS[i]);
                }
            }
            root.set("env", env);

            Files.writeString(settingsFile, MAPPER.writeValueAsString(root), StandardCharsets.UTF_8);
            LOG.fine("writeCustomModels: wrote " + models.size() + " models to " + settingsFile
                    + ", aliases: " + aliasToId);
        } catch (Exception e) {
            throw new IOException("writeCustomModels: failed to write " + settingsFile, e);
        }
    }

    /**
     * Removes the {@code availableModels} key from {@code <configDir>/settings.json}
     * if it is present.  Does nothing if the file does not exist or has no such key.
     *
     * @param configDir the profile's {@code CLAUDE_CONFIG_DIR} path
     * @throws IOException if the file exists but cannot be read or written
     */
    public static void removeAvailableModels(Path configDir) throws IOException {
        Path settingsFile = configDir.resolve("settings.json");
        if (!Files.exists(settingsFile)) {
            return;
        }
        try {
            String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
            ObjectNode root = (ObjectNode) MAPPER.readTree(json);
            if (!root.has("availableModels")) {
                return;
            }
            root.remove("availableModels");
            Files.writeString(settingsFile, MAPPER.writeValueAsString(root), StandardCharsets.UTF_8);
            LOG.info("removeAvailableModels: removed availableModels from " + settingsFile);
        } catch (Exception e) {
            throw new IOException("removeAvailableModels: failed to update " + settingsFile, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Returns a comma-separated list of profile names (no credentials). */
    private static String profileNames(List<ClaudeProfile> profiles) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(profiles.get(i).getName());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns the {@code CLAUDE_CONFIG_DIR} path for a non-Default profile.
     *
     * <p>The path is {@code <profilesDir>/<profile.getId()>/}.
     *
     * @param profile    the profile (must not be Default)
     * @param profilesDir base directory for profile config dirs
     * @return absolute path to this profile's config directory
     * @throws IllegalArgumentException if {@code profile.isDefault()} is {@code true}
     */
    public static Path resolveConfigDir(ClaudeProfile profile, Path profilesDir) {
        if (profile.isDefault()) {
            throw new IllegalArgumentException(
                    "Default profile has no isolated config dir; caller must not set CLAUDE_CONFIG_DIR.");
        }
        return profilesDir.resolve(profile.getId());
    }

    /**
     * Returns the settings directory for any profile, including Default.
     * Default profile maps to {@code ~/.claude/}; named profiles map to
     * {@code <profilesDir>/<profile.getId()>/}.
     */
    private static Path resolveSettingsDir(ClaudeProfile profile, Path profilesDir) {
        if (profile.isDefault()) {
            return Path.of(System.getProperty("user.home"), ".claude");
        }
        return profilesDir.resolve(profile.getId());
    }
}
