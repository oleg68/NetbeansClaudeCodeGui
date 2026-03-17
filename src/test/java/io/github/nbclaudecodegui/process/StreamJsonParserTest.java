package io.github.nbclaudecodegui.process;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StreamJsonParser}.
 */
class StreamJsonParserTest {

    // -------------------------------------------------------------------------
    // extractString
    // -------------------------------------------------------------------------

    @Test
    void testExtractSimpleString() {
        String json = "{\"type\":\"system\",\"subtype\":\"init\"}";
        assertEquals("system", StreamJsonParser.extractString(json, "type"));
        assertEquals("init",   StreamJsonParser.extractString(json, "subtype"));
    }

    @Test
    void testExtractStringWithEscapes() {
        String json = "{\"result\":\"line1\\nline2\\ttabbed\"}";
        assertEquals("line1\nline2\ttabbed",
                StreamJsonParser.extractString(json, "result"));
    }

    @Test
    void testExtractStringWithEscapedQuote() {
        String json = "{\"result\":\"say \\\"hello\\\"\"}";
        assertEquals("say \"hello\"",
                StreamJsonParser.extractString(json, "result"));
    }

    @Test
    void testExtractStringWithUnicode() {
        String json = "{\"text\":\"caf\\u00e9\"}";
        assertEquals("café", StreamJsonParser.extractString(json, "text"));
    }

    @Test
    void testExtractStringMissingField() {
        String json = "{\"type\":\"result\"}";
        assertNull(StreamJsonParser.extractString(json, "missing"));
    }

    @Test
    void testExtractStringNonStringValue() {
        // field exists but value is a number, not a string
        String json = "{\"cost\":0.001}";
        assertNull(StreamJsonParser.extractString(json, "cost"));
    }

    // -------------------------------------------------------------------------
    // extractSessionId
    // -------------------------------------------------------------------------

    @Test
    void testExtractSessionIdCamelCase() {
        // system event uses camelCase sessionId
        String json = "{\"type\":\"system\",\"subtype\":\"init\","
                + "\"sessionId\":\"abc-123-def\"}";
        assertEquals("abc-123-def", StreamJsonParser.extractSessionId(json));
    }

    @Test
    void testExtractSessionIdSnakeCase() {
        // result event uses snake_case session_id
        String json = "{\"type\":\"result\",\"subtype\":\"success\","
                + "\"session_id\":\"xyz-789\",\"total_cost_usd\":0.001}";
        assertEquals("xyz-789", StreamJsonParser.extractSessionId(json));
    }

    @Test
    void testExtractSessionIdMissing() {
        String json = "{\"type\":\"assistant\"}";
        assertNull(StreamJsonParser.extractSessionId(json));
    }

    @Test
    void testExtractSessionIdPrefersCamelCase() {
        // if both are somehow present, camelCase is checked first
        String json = "{\"sessionId\":\"camel\",\"session_id\":\"snake\"}";
        assertEquals("camel", StreamJsonParser.extractSessionId(json));
    }

    // -------------------------------------------------------------------------
    // realistic event lines
    // -------------------------------------------------------------------------

    @Test
    void testSystemInitEvent() {
        String line = "{\"type\":\"system\",\"subtype\":\"init\","
                + "\"sessionId\":\"01JNZQFAKE12345\","
                + "\"model\":\"claude-sonnet-4-5\","
                + "\"cwd\":\"/home/user/project\"}";

        assertEquals("system", StreamJsonParser.extractString(line, "type"));
        assertEquals("init",   StreamJsonParser.extractString(line, "subtype"));
        assertEquals("01JNZQFAKE12345", StreamJsonParser.extractSessionId(line));
    }

    // -------------------------------------------------------------------------
    // isPromptRequest
    // -------------------------------------------------------------------------

    @Test
    void testIsPromptRequestPlainQuestion() {
        assertTrue(StreamJsonParser.isPromptRequest("Allow this action? (y/n)"));
        assertTrue(StreamJsonParser.isPromptRequest("Proceed? [y/n]"));
        assertTrue(StreamJsonParser.isPromptRequest("Choose [1/2/3]"));
    }

    @Test
    void testIsPromptRequestJsonSubtype() {
        assertTrue(StreamJsonParser.isPromptRequest(
                "{\"type\":\"system\",\"subtype\":\"question\",\"question\":\"Allow?\"}"));
        assertTrue(StreamJsonParser.isPromptRequest(
                "{\"type\":\"system\",\"subtype\":\"permission_request\"}"));
    }

    @Test
    void testIsPromptRequestNegative() {
        assertFalse(StreamJsonParser.isPromptRequest(
                "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"Hi\"}"));
        assertFalse(StreamJsonParser.isPromptRequest("normal output line"));
        assertFalse(StreamJsonParser.isPromptRequest(null));
        assertFalse(StreamJsonParser.isPromptRequest(""));
    }

    // -------------------------------------------------------------------------
    // extractPromptOptions
    // -------------------------------------------------------------------------

    @Test
    void testExtractPromptOptionsParentheses() {
        var opts = StreamJsonParser.extractPromptOptions("Allow? (y/n/always)");
        assertEquals(List.of("y", "n", "always"), opts);
    }

    @Test
    void testExtractPromptOptionsBrackets() {
        var opts = StreamJsonParser.extractPromptOptions("Choose [1/2/3]");
        assertEquals(List.of("1", "2", "3"), opts);
    }

    @Test
    void testExtractPromptOptionsYesNo() {
        var opts = StreamJsonParser.extractPromptOptions("Continue? (yes/no)");
        assertEquals(List.of("yes", "no"), opts);
    }

    @Test
    void testExtractPromptOptionsNone() {
        var opts = StreamJsonParser.extractPromptOptions("Just a plain sentence.");
        assertTrue(opts.isEmpty());
    }

    // -------------------------------------------------------------------------
    // realistic event lines
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // extractAssistantText
    // -------------------------------------------------------------------------

    @Test
    void testExtractAssistantTextFromTextEvent() {
        String line = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_01\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Hello **world**\"}]}}";
        assertEquals("Hello **world**", StreamJsonParser.extractAssistantText(line));
    }

    @Test
    void testExtractAssistantTextReturnsNullForThinkingEvent() {
        String line = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_02\","
                + "\"content\":[{\"type\":\"thinking\",\"thinking\":\"Let me think\"}]}}";
        assertNull(StreamJsonParser.extractAssistantText(line));
    }

    @Test
    void testExtractAssistantTextReturnsNullForToolUseEvent() {
        String line = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_03\","
                + "\"content\":[{\"type\":\"tool_use\",\"name\":\"Glob\",\"input\":{}}]}}";
        assertNull(StreamJsonParser.extractAssistantText(line));
    }

    @Test
    void testExtractAssistantTextWithThinkingAndText() {
        String line = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_04\","
                + "\"content\":["
                + "{\"type\":\"thinking\",\"thinking\":\"Let me answer\"},"
                + "{\"type\":\"text\",\"text\":\"The answer is 42\"}"
                + "]}}";
        assertEquals("The answer is 42", StreamJsonParser.extractAssistantText(line));
    }

    @Test
    void testExtractAssistantTextReturnsNullForNonAssistantEvent() {
        String line = "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"done\"}";
        assertNull(StreamJsonParser.extractAssistantText(line));
    }

    // -------------------------------------------------------------------------
    // realistic event lines
    // -------------------------------------------------------------------------

    @Test
    void testResultSuccessEvent() {
        String line = "{\"type\":\"result\",\"subtype\":\"success\","
                + "\"result\":\"Hello, world!\","
                + "\"session_id\":\"sess-abc\","
                + "\"total_cost_usd\":\"0.00123\"}";

        assertEquals("result",        StreamJsonParser.extractString(line, "type"));
        assertEquals("success",       StreamJsonParser.extractString(line, "subtype"));
        assertEquals("Hello, world!", StreamJsonParser.extractString(line, "result"));
        assertEquals("sess-abc",      StreamJsonParser.extractSessionId(line));
        assertEquals("0.00123",       StreamJsonParser.extractString(line, "total_cost_usd"));
    }
}
