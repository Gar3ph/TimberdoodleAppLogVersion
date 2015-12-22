package de.tu_darmstadt.adtn.logging.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.spongycastle.util.encoders.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class Utility {

    private static MessageDigest md = null;

    public static String digestBytes(byte[] bytes) {
        try {
            if (md == null) md = MessageDigest.getInstance("MD5");
            byte[] tmp = md.digest(bytes);
            return Hex.toHexString(tmp);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String digestString(String string) {
        try {
            if (md == null) md = MessageDigest.getInstance("MD5");
            byte[] bytes = string.getBytes();
            byte[] tmp = md.digest(bytes);
            return Hex.toHexString(tmp);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static boolean isConnectedToInternet(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifi != null) return wifi.isConnected();
            NetworkInfo mobile = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (mobile != null) return mobile.isConnected();
        }
        return false;
    }
}
