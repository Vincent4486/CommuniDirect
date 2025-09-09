package net.vincent.CommuniDirect;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * {@code PropertiesData} handles loading, saving, and creating configuration properties
 * for the CommuniDirect application. It primarily manages the default port setting,
 * persisting it to a local file named {@code communidirect.properties}.
 *
 * <p>This class ensures that the application can remember user-defined settings
 * between sessions by reading and writing to a properties file located in the user's working directory.
 *
 * <p>Example file content:
 * <pre>
 * port=2556
 * </pre>
 *
 * @author Vincent
 * @version 1.0
 */
public class PropertiesData {

    /** Reference to the main CommuniDirect application instance. */
    CommuniDirect communiDirect;

    /** Path to the properties file used for storing configuration. */
    String path = Objects.requireNonNull(System.getProperty("user.dir") + "/communidirect.properties");

    /**
     * Constructs a {@code PropertiesData} instance linked to the given CommuniDirect app.
     *
     * @param communiDirect The main application instance.
     */
    public PropertiesData(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;
    }

    /**
     * Loads configuration from the properties file.
     * If the file does not exist, it creates a new one with default values.
     */
    public void load() {
        try (FileInputStream fileInputStream = new FileInputStream(path)) {
            Properties properties = new Properties();
            properties.load(fileInputStream);
            CommuniDirect.port_ = Integer.parseInt(properties.getProperty("port"));
        } catch (FileNotFoundException e) {
            create();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves the current configuration (e.g., port number) to the properties file.
     */
    public void save() {
        try (FileOutputStream fileOutputStream = new FileOutputStream(path)) {
            Properties properties = new Properties();
            properties.setProperty("port", Integer.toString(CommuniDirect.port_));
            properties.store(fileOutputStream, "properties file");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new properties file with default values.
     * This is typically called when no existing configuration file is found.
     */
    public void create() {
        try (FileOutputStream fileOutputStream = new FileOutputStream(path)) {
            Properties properties = new Properties();
            properties.setProperty("port", "2556");
            properties.store(fileOutputStream, "properties file");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}