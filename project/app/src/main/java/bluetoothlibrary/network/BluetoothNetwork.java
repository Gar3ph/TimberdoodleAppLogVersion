package bluetoothlibrary.network;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import bluetoothlibrary.bluetooth.manager.BluetoothManager;
import bluetoothlibrary.bus.BluetoothCommunicator;
import bluetoothlibrary.bus.BondedDevice;
import bluetoothlibrary.bus.ClientConnectionFail;
import bluetoothlibrary.bus.ClientConnectionSuccess;
import bluetoothlibrary.bus.ServerConnectionFail;
import bluetoothlibrary.bus.ServerConnectionSuccess;
import de.greenrobot.event.EventBus;

public class BluetoothNetwork extends Activity {

    public static BluetoothNetwork reference = null;

    private String TAG = "BluetoothNetwork";
    //Manager that handles connection
    private BluetoothManager mBluetoothManager;
    //Amount of server threads that are allowed
    private int maxClients = 6;
    private List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
    private BluetoothDevice current = null;
    private List<String> messageBuffer = new ArrayList<String>();
    private int failCounterClient = 0;

    /**
     * Instantiate the the bluetooth manager and check if bluetooth is available on this device
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        moveTaskToBack(true);
        mBluetoothManager = new BluetoothManager(this, BluetoothManager.Mode.SECURE);
        Log.d(TAG, "Bluetooth manager instantiated");
        checkBluetoothAvailability();
        Log.d(TAG, "Bluetooth availability check started");
    }

    /**
     * Register this activity on the event bus and set the parameters for the Bluetooth manager
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
        reference = this;
        Log.d(TAG, "Network reference set");
        mBluetoothManager.setNbrClientMax(maxClients);
        setTimeDiscoverable(BluetoothManager.BLUETOOTH_TIME_DISCOVERY_INFINITE);
        Log.d(TAG, "Discoverable requested");
        selectDualMode();
        Log.d(TAG, "Dual mode selected");
    }

    /**
     * Release all connections and exit the network in a clean way
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        reference = null;
        EventBus.getDefault().unregister(this);
        closeAllConnexion();
        Log.d(TAG, "onDestroy was called");
        Log.d(TAG, "Disconnected all connections");
    }

    /**
     * Called if android returns the answer to the bluetooth request
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothManager.REQUEST_DISCOVERABLE_CODE) {
            if (resultCode == BluetoothManager.BLUETOOTH_REQUEST_ACCEPTED) {
                onBluetoothStartDiscovery();
                Log.d(TAG, "Discovery started");
            }
        }
    }

    /**
     * Release all connections
     */
    public void closeAllConnexion() {
        mBluetoothManager.closeAllConnections();
    }

    public void checkBluetoothAvailability() {
        if (!mBluetoothManager.checkBluetoothAvailability())
            onBluetoothNotAvailable();
    }

    public void setTimeDiscoverable(int timeInSec) {
        mBluetoothManager.setTimeDiscoverable(timeInSec);
    }

    /**
     * Start discovering other devices
     */
    public void startDiscovery() {
        mBluetoothManager.startDiscovery();
    }

    /**
     *
     */
    public void scanAllBluetoothDevice() {
        mBluetoothManager.scanAllBluetoothDevice();
    }

    public void disconnectClient() {
        mBluetoothManager.disconnectClient();
    }

    public void disconnectServer() {
        mBluetoothManager.disconnectServer();
    }

    public void createServeur(String address) {
        mBluetoothManager.createServeur(address);
    }

    public void selectDualMode() {
        mBluetoothManager.selectDualMode();
    }

    public void createClient(String addressMac) {
        mBluetoothManager.createClient(addressMac);
    }

    public void sendMessage(String message) {
        mBluetoothManager.sendMessage(message);
    }

    public boolean isConnected() {
        return mBluetoothManager.isConnected;
    }

    public int myNbrClientMax() {
        return maxClients;
    }

    ;

    public void onBluetoothDeviceFound(BluetoothDevice device) {
        Log.d(TAG, "Found a device, created client connection");
        createClient(device.getAddress());
    }

    ;

    public void onClientConnectionSuccess() {
        failCounterClient = 0;
    }

    ;

    public void onClientConnectionFail() {
        ++failCounterClient;
        if (failCounterClient == 3) {
            mBluetoothManager.resetClient();
        }

    }

    ;

    public void onServeurConnectionSuccess() {

    }

    ;

    public void onServeurConnectionFail() {

    }

    ;

    public void onBluetoothStartDiscovery() {
        scanAllBluetoothDevice();
        startDiscovery();
    }

    ;

    public void onBluetoothCommunicator(String messageReceive) {
        messageBuffer.add(messageReceive);
    }

    ;

    public void onBluetoothNotAvailable() {
        throw new RuntimeException("Bluetooth not available");
    }

    ;

    public void onEventMainThread(BluetoothDevice device) {
        if (!mBluetoothManager.isConnectedToServer())
            onBluetoothDeviceFound(device);
        if (!mBluetoothManager.isNbrMaxReached())
            createServeur(device.getAddress());
    }

    public void onEventMainThread(ClientConnectionSuccess event) {
        Log.d(TAG, "Client connected");
        mBluetoothManager.isConnected = true;
    }

    public void onEventMainThread(ClientConnectionFail event) {
        mBluetoothManager.isConnected = false;
    }

    public void onEventMainThread(ServerConnectionSuccess event) {

        mBluetoothManager.onServerConnectionSuccess(event.mClientAdressConnected);
    }

    public void onEventMainThread(ServerConnectionFail event) {
        mBluetoothManager.onServerConnectionFailed(event.mClientAdressConnectionFail);
    }

    public void onEventMainThread(BluetoothCommunicator event) {
        onBluetoothCommunicator(event.mMessageReceive);
    }

    public void onEventMainThread(BondedDevice event) {
        //mBluetoothManager.sendMessage("BondedDevice");
    }

    public void receive(byte[] buffer, int offSet) {

    }

    public String receive() {
        if (!messageBuffer.isEmpty()) {
            String result = messageBuffer.get(0);
            messageBuffer.remove(result);
            return result;
        } else return null;
    }

}
