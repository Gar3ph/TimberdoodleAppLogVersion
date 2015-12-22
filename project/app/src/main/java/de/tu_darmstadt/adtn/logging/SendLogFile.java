package de.tu_darmstadt.adtn.logging;

import android.content.Context;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import static de.tu_darmstadt.adtn.logging.utils.Utility.isConnectedToInternet;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class SendLogFile implements Runnable {
    private Context context;
    private Pair<String, String> pair;

    public SendLogFile(Context context, Pair<String, String> pair) {
        this.context = context;
        this.pair = pair;
    }

    private boolean readLogAndSendToServer() {
        if (isConnectedToInternet(context)) {
            sendListToServer(readLog());
            return true;
        } else return false;
    }

    private List<String> readLog() {
        List<String> result = new LinkedList<String>();
        BufferedReader reader;
        try {
            FileInputStream fis = context.openFileInput(pair.first);
            reader = new BufferedReader(new InputStreamReader(fis));
            String currentLine;
            while ((currentLine = reader.readLine()) != null)
                result.add(currentLine);
            reader.close();
            return result;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean sendListToServer(List<String> jsonobjects) {
        try {
            URL url = new URL(pair.second);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            //connection.setReadTimeout(10000);
            connection.setConnectTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.connect();
            OutputStream os = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            for (String object : jsonobjects) {
                writer.write(object);
                writer.flush();
            }
            writer.close();
            connection.disconnect();
            return true;
        } catch (MalformedURLException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Starts executing the active part of the class' code. This method is
     * called when a thread is started that has been created with a class which
     * implements {@code Runnable}.
     */
    @Override
    public void run() {
        readLogAndSendToServer();
    }
}
