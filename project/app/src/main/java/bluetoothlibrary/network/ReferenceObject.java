package bluetoothlibrary.network;

import java.io.Serializable;

/**
 * Created by Dev on 13.12.2015.
 */
public class ReferenceObject implements Serializable {


    private BluetoothNetwork networkReference = null;

    public BluetoothNetwork getBluetoothNetwork() {
        return networkReference;
    }

    public void setBluetoothNetwork(BluetoothNetwork network) {
        networkReference = network;
    }
}
