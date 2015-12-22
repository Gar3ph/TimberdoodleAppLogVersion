package de.tu_darmstadt.adtn.logging.loggers;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import de.tu_darmstadt.adtn.logging.Entry;

import static de.tu_darmstadt.adtn.logging.utils.Constants.GROUP_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.MESSAGE_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.SENDER_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Constants.SENDFILE;
import static de.tu_darmstadt.adtn.logging.utils.Constants.TIME_KEY;
import static de.tu_darmstadt.adtn.logging.utils.Utility.digestBytes;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class SendLogger extends Logger {

    protected static SendLogger instance = null;

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

    private SendLogger() {
        super(SENDFILE);
    }

    /**
     * Creates a logger object and returns it. MAKE ABSOLUTELY SURE THAT YOU CALL
     * THIS METHOD FIRST, BEFORE YOU USE THIS LOGGER.
     * @return returns the logger object.
     */
    public static SendLogger getInstance() {
        if (instance == null) {
            if (instance == null)
                instance = new SendLogger();
        }
        return (SendLogger) instance;
    }

    /**
     * Logs the inputs by creating hashes of them and storing those hashes in a file.
     *
     * @param messageid hash of message that is being sent
     * @param key group key that is used to compute the hash
     */
    public static void log(byte[] messageid, byte[] key) {
        if (instance != null && instance.userSet) {
            final long time = System.currentTimeMillis();
            final String messageID = Hex.toHexString(messageid);
            final String groupID = digestBytes(key);
            Entry entry = new Entry() {
                @Override
                public String getJSON() {
                    JSONObject result = new JSONObject();
                    try {
                        result.put(MESSAGE_KEY, messageID);
                        result.put(TIME_KEY, (int) (time / 1000));
                        result.put(SENDER_KEY, instance.userID);
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
