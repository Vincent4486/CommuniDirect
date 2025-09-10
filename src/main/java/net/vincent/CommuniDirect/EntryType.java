package net.vincent.CommuniDirect;

/**
 * Represents the various categories of entry points within the application.
 * <p>
 * These types help classify annotated methods or constructors based on their role
 * in the system architecture.
 */
public enum EntryType {
    /**
     * Entry point for command execution logic.
     */
    COMMAND,

    /**
     * Entry point for network-related operations.
     */
    NETWORK,

    /**
     * Entry point for user interface interactions.
     */
    UI,

    /**
     * Entry point for background tasks or asynchronous operations.
     */
    BACKGROUND,

    /**
     * Entry point for utility functions or helper methods.
     */
    UTILITY,

    /**
     * Entry point for input/output operations.
     */
    IO
}
