package bluetoothlibrary.bluetooth.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import bluetoothlibrary.bus.BluetoothCommunicator;
import bluetoothlibrary.bus.ServerConnectionFail;
import bluetoothlibrary.bus.ServerConnectionSuccess;
import de.greenrobot.event.EventBus;
import de.tu_darmstadt.adtn.ProtocolConstants;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothServer implements Runnable {

    public String mClientAddress;
    public volatile boolean isConnected = false;
    private boolean CONTINUE_READ_WRITE = true;
    private UUID mUUID;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private boolean secure = true;

    public BluetoothServer(BluetoothAdapter bluetoothAdapter, String clientAddress, boolean secure) {
        mBluetoothAdapter = bluetoothAdapter;
        mClientAddress = clientAddress;
        this.secure = secure;
        mUUID = UUID.fromString("e0917680-d427-11e4-8830-" + mClientAddress.replace(":", ""));
        //mUUID = UUID.fromString("ed812085-1095-4247-b388-22e95cb249a");
    }

    @Override
    public void run() {
        try {
            if (secure)
                mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("BLTServer", mUUID);
            else
                mServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BLTServer", mUUID);
            mSocket = mServerSocket.accept();
            mInputStream = mSocket.getInputStream();
            mOutputStream = mSocket.getOutputStream();

            int bufferSize = ProtocolConstants.MAX_PACKET_SIZE;
            int bytesRead = -1;
            byte[] buffer = new byte[bufferSize];
            isConnected = true;
            EventBus.getDefault().post(new ServerConnectionSuccess(mClientAddress));
            Log.i("Server", "Connection was established successfully. Client is: " + mClientAddress + ". I am " + mBluetoothAdapter.getAddress());
            while (CONTINUE_READ_WRITE) {
                //final StringBuilder sb = new StringBuilder();
                bytesRead = mInputStream.read(buffer);
                if (bytesRead != -1) {
                    /*String result = "";
                    while ((bytesRead == bufferSize) && (buffer[bufferSize-1] != 0)) {
                        result = result + new String(buffer, 0, bytesRead);
                        bytesRead = mInputStream.read(buffer);
                    }
                    result = result + new String(buffer, 0, bytesRead);
                    sb.append(result);*/
                }
                EventBus.getDefault().post(new BluetoothCommunicator(buffer));
                Log.i("Server", " I received a message");
            }
        } catch (IOException e) {
            Log.e("", "ERROR : " + e.getMessage());
            EventBus.getDefault().post(new ServerConnectionFail(mClientAddress));
        }
    }

    public void write(byte[] message) {
        try {
            if (mOutputStream != null) {
                mOutputStream.write(message);
                mOutputStream.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientAddress() {
        return mClientAddress;
    }

    public void closeConnection() {
        isConnected = false;
        CONTINUE_READ_WRITE = false;
        if (mSocket != null) {
            try {
                mInputStream.close();
                mInputStream = null;
                mOutputStream.close();
                mOutputStream = null;
                mSocket.close();
                mSocket = null;
                mServerSocket.close();
                mServerSocket = null;
                CONTINUE_READ_WRITE = false;
            } catch (Exception e) {
            }
        }
    }
}
