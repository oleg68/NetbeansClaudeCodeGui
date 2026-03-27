package org.openbeans.claude.netbeans.tools;

/**
 * Handler for sending asynchronous tool responses.
 * Provided to async tools so they can send responses later.
 *
 * @param <O> the result type produced by the async tool
 */
public interface AsyncHandler<O> {
    /**
     * Send the final response for an async tool call.
     * @param result The result object to serialize and send
     */
    void sendResponse(O result);
}
