package de.tu_darmstadt.adtn.logging.loggers;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;

import static de.tu_darmstadt.adtn.logging.utils.Constants.sending;

/**
 * Created by Tobias-Wolfgang Otto.
 */
public abstract class Logger {

    protected final String filename;
    private final long interval = 10000;
    protected String userID;
    protected boolean userSet = false;
    protected volatile List<String> entries = new LinkedList<>();
    protected volatile boolean dataSetChanged = false;
    //private Context context;
    protected volatile boolean stop = false;
    private Thread runner = new Thread(new Runnable() {
        /**
         * Starts executing the active part of the class' code. This method is
         * called when a thread is started that has been created with a class which
         * implements {@code Runnable}.
         */
        @Override
        public void run() {
            while (!stop) {
                try {
                    if (dataSetChanged && !sending) {
                        storeDataInLogFile();
                        dataSetChanged = false;
                    }
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    break; // Cancel if interrupted by shutdown()
                }
            }
        }
    });

    /**
     * Constructor for the logger.
     *
     * @param filename Name of the log file.
     */
    protected Logger(String filename) {
        //this.context = context;
        this.filename = filename;
        runner.start();
    }

    /**
     * Stores the data in the log file.
     */
    private synchronized void storeDataInLogFile() {
        try {
                //Open FileOutputStream to write
                File  file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
                FileOutputStream out = new FileOutputStream(file, true);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
                int size = entries.size();
                //Append entries to log file
                for (int i = 0; i < size; i++) {
                    //write
                    writer.write(entries.get(0));
                    writer.newLine();
                    writer.flush();
                    entries.remove(0);
                }
                //Close file stream
                writer.close();
        } catch (IOException e) {
            String TAG = "Logger";
            Log.e(TAG, "Writing to logfile failed");
        }
    }

    /**
     * Stops the logging
     */
    protected void stopLogging() {
        stop = true;
        runner.interrupt();
    }

    /**
     * starts the logging if it was interrupted
     */
    protected void startLogging() {
        if (runner.isInterrupted()) {
            stop = false;
            runner.start();
        }
    }

    /**
     * Writes the json string to the buffer that stores the string in a log file
     *
     * @param input A json String. This method will not check if the string is a
     *              real representation of a json oject or not.
     */
    protected void update(String input) {
        entries.add(input);
        dataSetChanged = true;
    }

    /**
     * Sets the user id
     *
     * @param userID
     */
    protected void setUser(String userID) {
        this.userID = userID;
    }

}
