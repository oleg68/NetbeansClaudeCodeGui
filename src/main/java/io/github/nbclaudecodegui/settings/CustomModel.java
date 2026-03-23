package io.github.nbclaudecodegui.settings;

/**
 * Represents a single custom model entry in the Other API profile configuration.
 *
 * <p>{@code available} is transient — it reflects the result of the last
 * "Fetch" call and is not written to {@code settings.json}.
 *
 * @param id        model identifier as returned by {@code /v1/models}
 * @param available {@code null} = unknown, {@code true} = reachable, {@code false} = not reachable
 * @param alias     CC standard alias to map to this model: {@code ""}, {@code "sonnet"},
 *                  {@code "opus"}, or {@code "haiku"}
 */
public record CustomModel(String id, Boolean available, String alias) {

    public CustomModel {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (alias == null) alias = "";
    }

    /** Returns a copy with the given availability. */
    public CustomModel withAvailable(Boolean available) {
        return new CustomModel(id, available, alias);
    }

    /** Returns a copy with the given alias. */
    public CustomModel withAlias(String alias) {
        return new CustomModel(id, available, alias);
    }

    /**
     * Validates alias uniqueness across a list of models.
     *
     * @return {@code null} if valid, or an error message naming the duplicate alias
     */
    public static String validateAliasUniqueness(java.util.List<CustomModel> models) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CustomModel m : models) {
            String a = m.alias();
            if (a != null && !a.isBlank()) {
                if (!seen.add(a)) {
                    return "Alias '" + a + "' is assigned to more than one model";
                }
            }
        }
        return null;
    }
}
