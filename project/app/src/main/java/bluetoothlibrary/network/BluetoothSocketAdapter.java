package bluetoothlibrary.network;

import android.util.Log;

import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;

import de.tu_darmstadt.adtn.AdtnSocketException;
import de.tu_darmstadt.adtn.ISocket;
import de.tu_darmstadt.adtn.ProtocolConstants;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class BluetoothSocketAdapter implements ISocket{

    private BTNetworkService service;

    private final String TAG = "BluetoothSocketAdapter";

    public BluetoothSocketAdapter(BTNetworkService service){
        this.service = service;
    }

    /**
     * Receives data from the socket.
     *
     * @param buffer The buffer to put the received data in.
     * @param offset The offset in buffer.
     */
    @Override
    public void receive(byte[] buffer, int offset) {
        byte[] b = service.receive();
        if(b != null) {
            System.arraycopy(b, 0, buffer, offset, ProtocolConstants.MAX_PACKET_SIZE);
            Log.i(TAG, "Received: " + Hex.toHexString(b));
        }
    }

    /**
     * @param buffer The buffer containing the data to be sent.
     * @param offset The offset in buffer.
     */
    @Override
    public void send(byte[] buffer, int offset) throws AdtnSocketException{
        try{
            Log.i(TAG, "Sending: " + new String(buffer, "UTF-8"));
            service.sendMessage(buffer);
        } catch (NetworkNotConnectedException| UnsupportedEncodingException e){
            Log.i(TAG, "Could not send. Not connected");
            throw new AdtnSocketException("Network not ready yet", new Exception());
        }
    }

    /**
     * Closes the socket.
     */
    @Override
    public void close() {
        service.closeAllConnexion();
    }
}
