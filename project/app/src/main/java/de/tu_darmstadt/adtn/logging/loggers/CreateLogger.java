package de.tu_darmstadt.adtn.logging.loggers;

import org.json.JSONException;
import org.json.JSONObject;

import de.tu_darmstadt.adtn.logging.Entry;

import static de.tu_darmstadt.adtn.logging.utils.Constants.CREATEFILE;
import static de.tu_darmstadt.adtn.logging.utils.Constants.MESSAGE_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.SENDER_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.SIZE_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.TIME_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Utility.digestBytes;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class CreateLogger extends Logger {

    protected static CreateLogger instance = null;

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

    private CreateLogger() {
        super(CREATEFILE);
    }

    /**
     * Creates a logger object and returns it. MAKE ABSOLUTELY SURE THAT YOU CALL
     * THIS METHOD FIRST, BEFORE YOU USE THIS LOGGER.
     *
     * @return returns the logger object.
     */
    public static CreateLogger getInstance() {
        if (instance == null) {
            if (instance == null)
                instance = new CreateLogger();
        }
        return (CreateLogger) instance;
    }

    /**
     * Logs the passed in arguments by creating hashes of them and storing them in a file.
     *
     * @param message The message that was created.
     */
    public static void log(byte[] message) {
        if (instance != null && instance.userSet) {
            final long time = System.currentTimeMillis();
            final String messageID = digestBytes(message);
            final int size = message.length * 8;
            Entry entry = new Entry() {
                @Override
                public String getJSON() {
                    JSONObject result = new JSONObject();
                    try {
                        result.put(MESSAGE_KEY, messageID);
                        result.put(TIME_KEY, (int) (time / 1000));
                        result.put(SENDER_KEY, instance.userID);
                        result.put(SIZE_KEY, size);
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
