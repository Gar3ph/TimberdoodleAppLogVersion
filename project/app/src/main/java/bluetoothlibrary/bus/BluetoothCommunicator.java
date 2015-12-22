package bluetoothlibrary.bus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothCommunicator {

    public String mMessageReceive;
    public byte[] mBytesReceive;

    public BluetoothCommunicator(String messageReceive) {
        mMessageReceive = messageReceive;
    }
    public BluetoothCommunicator(byte[] bytes){mBytesReceive = bytes;}
}
