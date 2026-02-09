package net.vincent.communidirect;

import java.io.*;
import java.util.Properties;

/**
 * Handles loading, saving, and creating the application's
 * configuration file ({@code communidirect.properties}).
 *
 * <p>Currently the only persisted setting is the server port:
 * <pre>
 * port=2556
 * </pre>
 *
 * @author Vincent
 * @version 1.1
 */
public class PropertiesData {

    /** Key used for the port property. */
    private static final String PORT_KEY = "port";

    /** Reference to the main application instance. */
    private final CommuniDirect communiDirect;

    /** Path to the properties file. */
    private final String path;

    /**
     * Creates a new instance linked to the given application.
     *
     * @param communiDirect the main application instance
     */
    public PropertiesData(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;
        this.path = System.getProperty("user.dir") + "/communidirect.properties";
    }

    /**
     * Loads configuration from the properties file.
     * If the file does not exist a new one is created with defaults.
     */
    public void load() {
        try (FileInputStream in = new FileInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            CommuniDirect.port = Integer.parseInt(props.getProperty(PORT_KEY));
        } catch (FileNotFoundException e) {
            create();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves the current port to the properties file.
     */
    public void save() {
        try (FileOutputStream out = new FileOutputStream(path)) {
            Properties props = new Properties();
            props.setProperty(PORT_KEY, Integer.toString(CommuniDirect.port));
            props.store(out, "CommuniDirect configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new properties file populated with default values.
     */
    private void create() {
        try (FileOutputStream out = new FileOutputStream(path)) {
            Properties props = new Properties();
            props.setProperty(PORT_KEY, String.valueOf(CommuniDirect.DEFAULT_PORT));
            props.store(out, "CommuniDirect configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
