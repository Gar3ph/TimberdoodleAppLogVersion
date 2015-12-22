package de.tu_darmstadt.adtn.logging.utils;

import android.util.Pair;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class Constants {

    // Define type key and values for the json objects
    public static final String TYPE = "Type";
    public static final int TYPE_USER = 0;
    public static final int TYPE_GROUPS = 1;
    public static final int TYPE_MESSAGE = 2;
    public static final int TYPE_SENT = 3;
    public static final int TYPE_RECEIVED = 4;

    // Define keys for the json objects
    public static final String USER_KEY = "author";
    public static final String GROUP_KEY = "key";
    public static final String MESSAGE_KEY = "messageid";
    public static final String TIME_KEY = "time";
    public static final String RECEIVER_KEY = "receiver";
    public static final String SENDER_KEY = "sender";
    public static final String SIZE_KEY = "size";

    // Log file
    public static final String SENDFILE = "SendLog.txt";
    public static final String RECEIVEFILE = "ReceivedLog.txt";
    public static final String CREATEFILE = "AuthoredMessagesLog.txt";
    public static final String SELECTFILE = "GroupLog.txt";
    // url strings
    public static final String CREATEURL = "http://32c3.seemoo.tu-darmstadt.de/api/create";
    public static final String SELECTURL = "http://32c3.seemoo.tu-darmstadt.de/api/select";
    public static final String SENDURL = "http://32c3.seemoo.tu-darmstadt.de/api/send";
    public static final String RECEIVEURL = "http://32c3.seemoo.tu-darmstadt.de/api/receive";
    // Relation pairs for urls and log files
    public static final Pair<String, String> CREATEPAIR = new Pair<String, String>(CREATEFILE, CREATEURL);
    public static final Pair<String, String> SELECTPAIR = new Pair<String, String>(SELECTFILE, SELECTURL);
    public static final Pair<String, String> SENDPAIR = new Pair<String, String>(SENDFILE, SENDURL);
    public static final Pair<String, String> RECEIVEPAIR = new Pair<String, String>(RECEIVEFILE, RECEIVEURL);
    // Semaphore to stop concurrent file access
    public static volatile boolean sending = false;
}
