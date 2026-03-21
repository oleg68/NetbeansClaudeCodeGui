package io.github.nbclaudecodegui.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Describes the structure of a numbered-menu prompt from Claude.
 *
 * <p>Supports JSON serialization/deserialization via Jackson.
 */
public final class ChoiceMenuModel {

    public record Option(
            @JsonProperty("display") String display,
            @JsonProperty("response") String response) {

        @JsonCreator
        public Option {
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
