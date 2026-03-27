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
     *
     * @param display     user-facing label shown in the menu
     * @param response    string sent back to Claude when this option is selected
     * @param description optional subtitle shown below the label; may be {@code null}
     */
    public record Option(
            @JsonProperty("display") String display,
            @JsonProperty("response") String response,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonProperty("description") String description) {

        /** Jackson deserialization constructor. */
        @JsonCreator
        public Option {
        }

        /**
         * Convenience constructor without description (backwards-compatible).
         *
         * @param display  user-facing label
         * @param response string sent back to Claude
         */
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

    /**
     * Creates a choice-menu model.
     *
     * @param text               prompt text displayed above the options
     * @param options            list of selectable options
     * @param defaultOptionIndex index of the pre-selected option
     */
    @JsonCreator
    public ChoiceMenuModel(
            @JsonProperty("text") String text,
            @JsonProperty("options") List<Option> options,
            @JsonProperty("defaultOptionIndex") int defaultOptionIndex) {
        this.text = text;
        this.options = options;
        this.defaultOptionIndex = defaultOptionIndex;
    }

    /**
     * Returns the prompt text.
     *
     * @return prompt text
     */
    public String text() { return text; }

    /**
     * Returns the list of selectable options.
     *
     * @return option list
     */
    public List<Option> options() { return options; }

    /**
     * Returns the index of the pre-selected option.
     *
     * @return default option index
     */
    public int defaultOptionIndex() { return defaultOptionIndex; }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Serializes this model to a JSON string.
     *
     * @return JSON representation
     * @throws Exception if serialization fails
     */
    public String toJson() throws Exception {
        return MAPPER.writeValueAsString(this);
    }

    /**
     * Deserializes a {@code ChoiceMenuModel} from a JSON string.
     *
     * @param json JSON string
     * @return deserialized model
     * @throws Exception if deserialization fails
     */
    public static ChoiceMenuModel fromJson(String json) throws Exception {
        return MAPPER.readValue(json, ChoiceMenuModel.class);
    }
}
