package bluetoothlibrary.network;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import bluetoothlibrary.bluetooth.manager.AlternateBluetoothManager;
import bluetoothlibrary.bus.BondedDevice;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class BTNetworkService extends Service {

    // The binder that gets returned in onBind()
    private final IBinder binder = new LocalBinder();
    private String TAG = "BluetoothNetwork";
    //Manager that handles connection
    private AlternateBluetoothManager mBluetoothManager;
    //Amount of server threads that are allowed
    private int maxClients = 6;
    private List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
    private BluetoothDevice current = null;
    private List<byte[]> messageBuffer = new ArrayList<byte[]>();
    private int failCounterClient = 0;
    private boolean clientToServer = false;

    /**
     * Called by the system when the service is first created.  Do not call this method directly.
     */
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "SERVICE WAS INVOKED");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //EventBus.getDefault().unregister(this);
        stopNetworking();
        Log.d(TAG, "SERVICE WAS DESTROYED");
        Log.d(TAG, "Disconnected all connections");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "ONBIND WAS CALLED");
        return binder;
    }

    /**
     * Called when all clients have disconnected from a particular interface
     * published by the service.  The default implementation does nothing and
     * returns false.
     *
     * @param intent The Intent that was used to bind to this service,
     *               as given to {@link Context#bindService
     *               Context.bindService}.  Note that any extras that were included with
     *               the Intent at that point will <em>not</em> be seen here.
     * @return Return true if you would like to have the service's
     * {@link #onRebind} method later called when new clients bind to it.
     */
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public void startNetworking() {
        if(mBluetoothManager == null) {
            mBluetoothManager = new AlternateBluetoothManager(this, AlternateBluetoothManager.Mode.INSECURE);
            //Log.d(TAG, "Bluetooth manager instantiated");
            checkBluetoothAvailability();
            //Log.d(TAG, "Bluetooth availability check started");
            mBluetoothManager.setNbrClientMax(maxClients);
            setTimeDiscoverable(AlternateBluetoothManager.BLUETOOTH_TIME_DISCOVERY_INFINITE);
            //Log.d(TAG, "Discoverable requested");
            selectDualMode();
            //Log.d(TAG, "Dual mode selected");
            scanAllBluetoothDevice();
        }
    }

    public void stopNetworking() {
        if(mBluetoothManager != null) {
            Log.i(TAG, "Stop networking was called");
            mBluetoothManager.closeAllConnections();
            mBluetoothManager = null;
        }
    }

    /**
     * Release all connections
     */
    public void closeAllConnexion() {
        if (mBluetoothManager != null) {
            Log.i(TAG, "Close all connections was called");
            mBluetoothManager.closeAllConnections();
        }
    }

    public void checkBluetoothAvailability() {
        if (mBluetoothManager != null)
            if (!mBluetoothManager.checkBluetoothAvailability()) {
                onBluetoothNotAvailable();
                Log.i(TAG, "Bluetooth not available");
            }
    }

    public void setTimeDiscoverable(int timeInSec) {
        if (mBluetoothManager != null) {
            mBluetoothManager.setTimeDiscoverable(timeInSec);
            Log.i(TAG, "Duration of being discoverable was set");
        }
    }

    /**
     * Start discovering other devices
     */
    public void startDiscovery() {
        if (mBluetoothManager != null) {
            mBluetoothManager.startDiscovery();
            Log.i(TAG, "startDiscovery() called on Bluetooth manager");
        }
    }

    /**
     *
     */
    public void scanAllBluetoothDevice() {
        if (mBluetoothManager != null) {
            mBluetoothManager.scanAllBluetoothDevice();
            Log.i(TAG, "Start scanning for Bluetooth devices");
        }
    }

    public void selectDualMode() {
        if (mBluetoothManager != null)
            mBluetoothManager.selectDualMode();
    }

    public void createClient(String addressMac) {
        if (mBluetoothManager != null) {
            Log.i(TAG, "I try to become a client to a remote server");
            mBluetoothManager.createClient(addressMac);
        }
    }

    public void sendMessage(byte[] message) throws NetworkNotConnectedException{
        if (mBluetoothManager != null) {
            Log.i(TAG, "Sending message to manager");
            mBluetoothManager.sendMessage(message);
        } else
            Log.i(TAG, "Couldn't send message, manager is null");
    }

    public boolean isConnected() {
        boolean result = false;
        if (mBluetoothManager != null) {
            result = mBluetoothManager.isConnected;
        }
        return result;
    }

    public int myNbrClientMax() {
        return maxClients;
    }

    public void onBluetoothDeviceFound(BluetoothDevice device) {
        Log.d(TAG, "Found a device, created client connection");
        String address = mBluetoothManager.getYourBtMacAddress();
        if(!device.getAddress().equals(address)) {

            createClient(device.getAddress());

        }
    }

    public void onClientConnectionSuccess() {
        failCounterClient = 0;
    }

    public void onClientConnectionFail() {
        if (mBluetoothManager != null) {
            ++failCounterClient;
            if (failCounterClient == 3) {

                mBluetoothManager.resetClient();
                mBluetoothManager.setIsConnected();
            }
        }
        Log.i(TAG, "Client connection fail was invoked");
    }

    public void onBluetoothCommunicator(byte[] messageReceive) {
        messageBuffer.add(messageReceive);
    }

    public void onBluetoothNotAvailable() {
        throw new RuntimeException("Bluetooth not available");
    }

    public void handleClientConnection(BluetoothDevice device) {
        final BluetoothDevice bDevice = device;
        Handler mHandler = new Handler();
        Runnable connectAttempt = new Runnable(){
            @Override
            public void run() {
                if (!clientToServer) {
                    Log.i(TAG, "Runner invokes client connect attempt");
                    onBluetoothDeviceFound(bDevice);
                    clientToServer = true;
                }
            }
        };
        long range = 40000L;
        Random rand = new Random();
        long time = (long)(rand.nextDouble()*range) + 1000;
        Log.i(TAG, "Will attempt to connect to server in " + time + "ms");
        mHandler.postDelayed(connectAttempt, time);
        Log.i(TAG, "Bluetooth device was found");
    }

    public void handleClientConnection(BondedDevice event) {
        //mBluetoothManager.sendMessage("BondedDevice");
    }

    public byte[] receive() {
        if (!messageBuffer.isEmpty()) {
            byte[] result = messageBuffer.get(0);
            messageBuffer.remove(result);
            return result;
        } else return null;
    }

    /**
     * A binder to obtain the service object once the service is started.
     */
    public class LocalBinder extends Binder {
        /**
         * @return The service object.
         */
        public Service getService() {
            Log.d(TAG, "GET SERVICE IN LOCAL BINDER WAS CALLED");
            return BTNetworkService.this;
        }
    }

    public BluetoothSocketAdapter getSocket(){
        return new BluetoothSocketAdapter(this);
    }

    public String getState(){
        if(mBluetoothManager != null)
        return mBluetoothManager.getState();
        else return "Networkng not running";
    }

}
