package net.vincent.CommuniDirect;

import java.lang.annotation.*;

/**
 * Marks a method or constructor as an entry point into a specific subsystem of the application.
 * <p>
 * This annotation is typically used to indicate where control flow begins for a given module,
 * such as command handling, network communication, or UI interaction.
 *
 * @see EntryType
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface EntryPoint {
    /**
     * Specifies the type of entry point this annotation represents.
     *
     * @return the entry type
     */
    EntryType value();
}