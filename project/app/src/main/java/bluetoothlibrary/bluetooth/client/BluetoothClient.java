package bluetoothlibrary.bluetooth.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.UUID;

import bluetoothlibrary.bus.BluetoothCommunicator;
import bluetoothlibrary.bus.ClientConnectionFail;
import bluetoothlibrary.bus.ClientConnectionSuccess;
import de.greenrobot.event.EventBus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothClient implements Runnable {

    private boolean CONTINUE_READ_WRITE = true;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private UUID mUuid;
    private String mAdressMac;

    private BluetoothSocket mSocket;
    private InputStream mInputStream;
    private OutputStreamWriter mOutputStreamWriter;

    private BluetoothConnector mBluetoothConnector;
    private boolean secure = true;
    private volatile boolean abort = false;


    public BluetoothClient(BluetoothAdapter bluetoothAdapter, String adressMac, boolean secure) {
        mBluetoothAdapter = bluetoothAdapter;
        mAdressMac = adressMac;
        this.secure = secure;
        mUuid = UUID.fromString("e0917680-d427-11e4-8830-" + bluetoothAdapter.getAddress().replace(":", ""));
        //mUuid = UUID.fromString("ed812085-1095-4247-b388-22e95cb249a4");
    }

    @Override
    public void run() {

        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mAdressMac);
//        List<UUID> uuidCandidates = new ArrayList<UUID>();
//        uuidCandidates.add(mUuid);

        while (mInputStream == null && !abort) {
            mBluetoothConnector = new BluetoothConnector(mBluetoothDevice, secure, mBluetoothAdapter, mUuid);

            try {
                mSocket = mBluetoothConnector.connect().getUnderlyingSocket();
                mInputStream = mSocket.getInputStream();
            } catch (IOException e1) {
                Log.e("", "===> mSocket IOException", e1);
                EventBus.getDefault().post(new ClientConnectionFail());
                e1.printStackTrace();
            }
        }

        if (mSocket == null) {
            Log.e("", "===> mSocket == Null");
            return;
        }

        try {

            mOutputStreamWriter = new OutputStreamWriter(mSocket.getOutputStream());

            int bufferSize = 1500;
            int bytesRead = -1;
            byte[] buffer = new byte[bufferSize];

            EventBus.getDefault().post(new ClientConnectionSuccess());
            Log.i("Client", "I connected successfully as a client");
            while (CONTINUE_READ_WRITE) {

                final StringBuilder sb = new StringBuilder();
                bytesRead = mInputStream.read(buffer);
                if (bytesRead != -1) {
                    String result = "";
                    while ((bytesRead == bufferSize) && (buffer[bufferSize] != 0)) {
                        result = result + new String(buffer, 0, bytesRead);
                        bytesRead = mInputStream.read(buffer);
                    }
                    result = result + new String(buffer, 0, bytesRead);
                    sb.append(result);
                }

                EventBus.getDefault().post(new BluetoothCommunicator(sb.toString()));

            }
        } catch (IOException e) {
            Log.e("", "===> Client run");
            e.printStackTrace();
            EventBus.getDefault().post(new ClientConnectionFail());
        }
    }

    public void write(String message) {
        try {
            mOutputStreamWriter.write(message);
            mOutputStreamWriter.flush();
        } catch (IOException e) {
            Log.e("", "===> Client write");
            e.printStackTrace();
        }
    }

    public String getAddress() {
        return mAdressMac;
    }

    public void closeConnexion() {
        if (mSocket != null) {
            try {
                mInputStream.close();
                mInputStream = null;
                mOutputStreamWriter.close();
                mOutputStreamWriter = null;
                mSocket.close();
                mSocket = null;
                mBluetoothConnector.close();
            } catch (Exception e) {
                Log.e("", "===> Client closeConnexction");
            }
            CONTINUE_READ_WRITE = false;
        }
    }
}
