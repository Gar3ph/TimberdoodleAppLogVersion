package de.tu_darmstadt.adtn.logging.loggers;

import org.json.JSONException;
import org.json.JSONObject;

import de.tu_darmstadt.adtn.logging.Entry;

import static de.tu_darmstadt.adtn.logging.utils.Constants.GROUP_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.MESSAGE_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.RECEIVEFILE;
import static de.tu_darmstadt.adtn.logging.utils.Constants.RECEIVER_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.TIME_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Utility.digestBytes;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class ReceiveLogger extends Logger {

    protected static ReceiveLogger instance = null;

    /**
     * Call this to stop logging and release all resources
     */
    public static void removeInstance() {
        stop();
        if (instance != null) {
            instance = null;
        }
    }

    /**
     * Sets the user id. Call this immediately after getInstance(Context context).
     *
     * @param key user's public key.
     */
    public static void setUserID(byte[] key) {
        if (instance != null && !instance.userSet) {
            String user = digestBytes(key);
            instance.setUser(user);
            instance.userSet = true;
        }
    }

    /**
     * Stops the logging
     */
    public static void stop() {
        if (instance != null) instance.stopLogging();
    }

    /**
     * Starts the logging if it was stopped previously
     */
    public static void start() {
        if (instance != null) instance.startLogging();
    }

    private ReceiveLogger() {
        super(RECEIVEFILE);
    }

    /**
     * Creates a logger object and returns it. MAKE ABSOLUTELY SURE THAT YOU CALL
     * THIS METHOD FIRST, BEFORE YOU USE THIS LOGGER.
     *
     * @return returns the logger object.
     */
    public static ReceiveLogger getInstance() {
        if (instance == null) {
            if (instance == null)
                instance = new ReceiveLogger();
        }
        return (ReceiveLogger) instance;
    }

    /**
     * Logs the passed in arguments by creating hashes of them and storing them in a file.
     *
     * @param message
     * @param key
     */
    public static void log(byte[] message, byte[] key) {
        if (instance != null && instance.userSet) {
            final long time = System.currentTimeMillis();
            final String messageID = digestBytes(message);
            final String groupID = digestBytes(key);
            Entry entry = new Entry() {
                @Override
                public String getJSON() {
                    JSONObject result = new JSONObject();
                    try {
                        result.put(MESSAGE_KEY, messageID);
                        result.put(TIME_KEY, (int) (time / 1000));
                        result.put(RECEIVER_KEY, instance.userID);
                        result.put(GROUP_KEY, groupID);
                    } catch (JSONException e) {
                        return null;
                    }
                    return result.toString();
                }
            };
            instance.update(entry.getJSON());
        }
    }

}