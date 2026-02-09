package net.vincent.communidirect;

/**
 * The {@code History} class is the history manager for the
 * command line in CommuniDirect, providing history of commands
 * when pressing the up and down arrow key.
 * <p>
 * This class works like most shells by having a file that store
 * the history data in a {@code Properties} file in the working directory
 * same as the main properties file.
 * </p>
 * @author Vincent
 * @version 1.1
 */
public class History {
	/** Reference to the main {@code CommuniDirect} code instance. */
	CommuniDirect communiDirect;
	
	/**
	 * The constructor for the {@code History} class, which is for external
	 * calling from the main class.
	 * @param communiDirect Main class instance.
	 */
	public History(CommuniDirect communiDirect) {
		this.communiDirect = communiDirect;
		
	}
	
	/**
	 * This function is to load the history from the properties
	 * file to memory and in a array list to increase the speed
	 * of loading, this would only load the recent 64 lines.
	 */
	public void loadHistory() {}
	
	/**
	 * This is the method to get history from the array list.
	 * @param line The line of history
	 */
	public String getHistory(int line) {
		return null;
	}
	
	/**
	 * This is the method to write new history to the array and properties
	 * file, would be called by {@code Command} class.
	 * @param history The command the user typed
	 */
	public void writeHistory(String history) {}
}
