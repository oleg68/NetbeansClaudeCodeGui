package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Contract for a single MCP tool exposed to Claude.
 *
 * @param <T> parameter type (deserialized from the JSON {@code arguments} node)
 * @param <O> result type (serialized to JSON for the MCP response)
 */
public interface Tool<T, O> {

    /**
     * Returns the tool name as registered in the MCP manifest.
     *
     * @return tool name
     */
    String getName();

    /**
     * Returns a human-readable description sent to Claude.
     *
     * @return tool description
     */
    String getDescription();

    /**
     * Returns the parameter class type for JSON deserialization.
     * Implementations should return the actual class of their parameter type.
     *
     * @return parameter class
     */
    Class<T> getParameterClass();

    /**
     * Parses the JSON {@code arguments} node into a parameter object using Jackson.
     *
     * @param arguments the JSON node containing the tool arguments
     * @return the deserialized parameter object
     */
    default T parseArguments(JsonNode arguments) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(arguments, getParameterClass());
    }

    /**
     * Execute the tool with the given parameters.
     * @param params Parsed tool parameters
     * @return Either the result object (for sync tools) or an AsyncResponse (for async tools)
     * @throws Exception if the tool execution fails
     */
    O run(T params) throws Exception;
}
