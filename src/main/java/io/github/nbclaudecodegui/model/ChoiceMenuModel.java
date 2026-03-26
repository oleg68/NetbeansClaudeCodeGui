package io.github.nbclaudecodegui.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Describes the structure of a numbered-menu prompt from Claude.
 *
 * <p>Supports JSON serialization/deserialization via Jackson.
 */
public final class ChoiceMenuModel {

    /**
     * A single option in the menu.
     *
     * <p>{@code display} is the user-facing label; {@code response} is the string sent back
     * to Claude (usually the item number). {@code description} is an optional subtitle shown
     * below the label — present when Claude renders a description line under the option.
     */
    public record Option(
            @JsonProperty("display") String display,
            @JsonProperty("response") String response,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonProperty("description") String description) {

        @JsonCreator
        public Option {
        }

        /** Convenience constructor without description (backwards-compatible). */
        public Option(String display, String response) {
            this(display, response, null);
        }
    }

    @JsonProperty("text")
    private final String text;
    @JsonProperty("options")
    private final List<Option> options;
    @JsonProperty("defaultOptionIndex")
    private final int defaultOptionIndex;

    @JsonCreator
    public ChoiceMenuModel(
            @JsonProperty("text") String text,
            @JsonProperty("options") List<Option> options,
            @JsonProperty("defaultOptionIndex") int defaultOptionIndex) {
        this.text = text;
        this.options = options;
        this.defaultOptionIndex = defaultOptionIndex;
    }

    public String text() { return text; }
    public List<Option> options() { return options; }
    public int defaultOptionIndex() { return defaultOptionIndex; }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() throws Exception {
        return MAPPER.writeValueAsString(this);
    }

    public static ChoiceMenuModel fromJson(String json) throws Exception {
        return MAPPER.readValue(json, ChoiceMenuModel.class);
    }
}
