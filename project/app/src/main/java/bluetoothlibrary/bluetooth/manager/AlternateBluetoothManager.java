package bluetoothlibrary.bluetooth.manager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bluetoothlibrary.bluetooth.client.AlternateClient;
import bluetoothlibrary.bluetooth.server.BluetoothServer;
import bluetoothlibrary.bus.BluetoothCommunicator;
import bluetoothlibrary.bus.BondedDevice;
import bluetoothlibrary.bus.ClientConnectionFail;
import bluetoothlibrary.bus.ClientConnectionSuccess;
import bluetoothlibrary.bus.ServerConnectionFail;
import bluetoothlibrary.bus.ServerConnectionSuccess;
import bluetoothlibrary.network.BTNetworkService;
import bluetoothlibrary.network.NetworkNotConnectedException;
import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;

/**
 * Created by Rami Martin.
 * Edited by Tobias-Wolfgang Otto
 */
public class AlternateBluetoothManager extends BroadcastReceiver {


    public static final int REQUEST_DISCOVERABLE_CODE = 114;
    public static final int BLUETOOTH_REQUEST_REFUSED = 0; // NE PAS MODIFIER LA VALEUR
    //Depends on the device and the android version.
    //Most of the time it will actually be 3600 seconds instead of "always on".
    public static final int BLUETOOTH_TIME_DISCOVERY_INFINITE = 0;
    public static final int BLUETOOTH_TIME_DISCOVERY_60_SEC = 60;
    public static final int BLUETOOTH_TIME_DISCOVERY_120_SEC = 120;
    public static final int BLUETOOTH_TIME_DISCOVERY_300_SEC = 300;
    public static final int BLUETOOTH_TIME_DISCOVERY_600_SEC = 600;
    public static final int BLUETOOTH_TIME_DISCOVERY_900_SEC = 900;
    public static final int BLUETOOTH_TIME_DISCOVERY_1200_SEC = 1200;
    public static final int BLUETOOTH_TIME_DISCOVERY_3600_SEC = 3600;
    public static int BLUETOOTH_REQUEST_ACCEPTED;
    private static int BLUETOOTH_NBR_CONNECTIONS_MAX = 7;
    public TypeBluetooth mType;
    public boolean isConnected;
    private Context context;
    private BTNetworkService service;
    private BluetoothAdapter mBluetoothAdapter;
    private String TAG = "Alternate Bluetooth manager";
    private AlternateClient mBluetoothClient;

    private List<BluetoothDevice> devicesFound;
    private ArrayList<String> mAdressListServerWaitingConnection;
    private HashMap<String, BluetoothServer> mServeurWaitingConnectionList;
    private volatile ArrayList<BluetoothServer> mServeurConnectedList;
    private HashMap<String, Thread> mServeurThreadList;
    private int mNbrClientConnection;
    private int mTimeDiscoverable;
    //public boolean thisClientConnectedToServer = false;
    private boolean mBluetoothIsEnableOnStart;
    private String mBluetoothNameSaved;
    private boolean secure;
    public volatile boolean connecting = false;

    public AlternateBluetoothManager(Context context, Mode mode) {
        this.context = context;
        this.service = (BTNetworkService) context;
        secure = mode == Mode.SECURE;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothNameSaved = mBluetoothAdapter.getName();
        mBluetoothIsEnableOnStart = mBluetoothAdapter.isEnabled();
        devicesFound = new ArrayList<BluetoothDevice>();
        mType = TypeBluetooth.None;
        isConnected = false;
        mNbrClientConnection = 0;
        mAdressListServerWaitingConnection = new ArrayList<String>();
        mServeurWaitingConnectionList = new HashMap<String, BluetoothServer>();
        mServeurConnectedList = new ArrayList<BluetoothServer>();
        mServeurThreadList = new HashMap<String, Thread>();
        setTimeDiscoverable(BLUETOOTH_TIME_DISCOVERY_INFINITE);
        EventBus.getDefault().register(this);
    }

    private int getAmountOfFreeSpaces() {
        return (getNbrClientMax() - mNbrClientConnection);
    }

    private String getPlacesAvailableMessage() {
        return getAmountOfFreeSpaces() + " places available " + android.os.Build.MODEL;
    }

    public void selectServerMode() {
        startDiscovery();
        mType = TypeBluetooth.Server;
        setServerBluetoothName();
    }

    private void setServerBluetoothName() {
        mBluetoothAdapter.setName("Server " + getPlacesAvailableMessage());
    }

    /**
     * Returns the connected server list
     *
     * @return
     */
    public List<BluetoothServer> getServerList() {
        return mServeurConnectedList;
    }

    public void selectDualMode() {
        startDiscovery();
        Log.i(TAG, "Discovery was started");
        mType = TypeBluetooth.Dual;
        setDualBluetoothName();
    }

    private void setDualBluetoothName() {
        mBluetoothAdapter.setName("Dual " + getPlacesAvailableMessage());
    }

    public void selectClientMode() {
        startDiscovery();
        mType = TypeBluetooth.Client;
        mBluetoothAdapter.setName("Client " + android.os.Build.MODEL);
    }

    public String getYourBtMacAddress() {
        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.getAddress();
        }
        return null;
    }

    public int getNbrClientMax() {
        return BLUETOOTH_NBR_CONNECTIONS_MAX;
    }

    public void setNbrClientMax(int nbrClientMax) {
        if (nbrClientMax <= BLUETOOTH_NBR_CONNECTIONS_MAX) {
            BLUETOOTH_NBR_CONNECTIONS_MAX = nbrClientMax;
        }
    }

    public boolean isNbrMaxReached() {
        return mNbrClientConnection == getNbrClientMax();
    }

    public void setServerWaitingConnection(String address, BluetoothServer bluetoothServer, Thread threadServer) {
        mAdressListServerWaitingConnection.add(address);
        mServeurWaitingConnectionList.put(address, bluetoothServer);
        mServeurThreadList.put(address, threadServer);
    }

    public void incrementNbrConnection() {
        mNbrClientConnection = mNbrClientConnection + 1;
        setServerBluetoothName();
        if (mNbrClientConnection == getNbrClientMax()) {
            //resetWaitingThreadServer();
        }
        Log.e("", "===> incrementNbrConnection mNbrClientConnection : " + mNbrClientConnection);
    }

    private void resetWaitingThreadServer() {
        for (Map.Entry<String, Thread> bluetoothThreadServerMap : mServeurThreadList.entrySet()) {
            if (mAdressListServerWaitingConnection.contains(bluetoothThreadServerMap.getKey())) {
                Log.e("", "===> resetWaitingThreadServer Thread : " + bluetoothThreadServerMap.getKey());
                bluetoothThreadServerMap.getValue().interrupt();
            }
        }
        for (Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionList.entrySet()) {
            Log.e("", "===> resetWaitingThreadServer BluetoothServer : " + bluetoothServerMap.getKey());
            bluetoothServerMap.getValue().closeConnection();
            //mServeurThreadList.remove(bluetoothServerMap.getKey());
        }
        mAdressListServerWaitingConnection.clear();
        mServeurWaitingConnectionList.clear();
    }

    public void decrementNbrConnection() {
        if (mNbrClientConnection == 0) {
            return;
        }
        mNbrClientConnection = mNbrClientConnection - 1;
        if (mNbrClientConnection == 0) {
            isConnected = false;
        }
        Log.e("", "===> decrementNbrConnection mNbrClientConnection : " + mNbrClientConnection);
        setServerBluetoothName();
    }

    public void setTimeDiscoverable(int timeInSec) {
        mTimeDiscoverable = timeInSec;
        BLUETOOTH_REQUEST_ACCEPTED = mTimeDiscoverable;
    }

    public boolean checkBluetoothAvailability() {
        return mBluetoothAdapter != null;
    }

    public void cancelDiscovery() {
        if (mBluetoothAdapter != null && isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    public boolean isDiscovering() {
        return mBluetoothAdapter.isDiscovering();
    }

    public void startDiscovery() {
        if (mBluetoothAdapter != null)
            if (mBluetoothAdapter.isEnabled() && isDiscovering()) {
                Log.e("", "===> mBluetoothAdapter.isDiscovering()");
            } else {
                Log.e("", "===> startDiscovery");
                //Intent start = new Intent(context, BluetoothNetwork.class);
                //context.startActivity(start);
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, mTimeDiscoverable);
                discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(discoverableIntent);
            }
    }

    public void scanAllBluetoothDevice() {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        context.registerReceiver(this, intentFilter);
        mBluetoothAdapter.startDiscovery();
    }

    public void createClient(String addressMac) {
        if (mType == TypeBluetooth.Client || mType == TypeBluetooth.Dual) {
            IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            context.registerReceiver(this, bondStateIntent);
            if (mBluetoothClient == null) {
                boolean check = false;
                for(BluetoothServer server: mServeurConnectedList)
                    if(server.getClientAddress().equals(addressMac)) check = true;
                if(!check) {
                    Log.i(TAG, "Creating client");
                    mBluetoothClient = new AlternateClient(mBluetoothAdapter, addressMac, secure, this);
                    new Thread(mBluetoothClient).start();
                }
            }
        }
    }

    public void setIsConnected(){
        isConnected = false;
        if(mBluetoothClient != null) isConnected = isConnected || mBluetoothClient.isConnected;
        for(BluetoothServer server: mServeurConnectedList){
            isConnected = isConnected || server.isConnected;
        }
    }

    public synchronized void createServeur(String address) {
        if ((mType == TypeBluetooth.Server || mType == TypeBluetooth.Dual) && !mAdressListServerWaitingConnection.contains(address)) {
            if (mBluetoothClient == null || !mBluetoothClient.getAddress().equalsIgnoreCase(address)) {
                BluetoothServer mBluetoothServer = new BluetoothServer(mBluetoothAdapter, address, secure);
                Thread threadServer = new Thread(mBluetoothServer);
                threadServer.start();
                setServerWaitingConnection(address, mBluetoothServer, threadServer);
                IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                context.registerReceiver(this, bondStateIntent);
                Log.e("", "===> createServeur address : " + address);
            }
        }
    }

    public void onServerConnectionSuccess(String addressClientConnected) {
        connecting = true;
        for (Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionList.entrySet()) {
            if (addressClientConnected.equalsIgnoreCase(bluetoothServerMap.getValue().getClientAddress())) {
                mServeurConnectedList.add(bluetoothServerMap.getValue());
                incrementNbrConnection();
                Log.i("", "===> onServerConnectionSuccess address : " + addressClientConnected);
                connecting = false;
                return;
            }
        }
        connecting = false;
        setIsConnected();
    }

    public void onServerConnectionFailed(String addressClientConnectionFailed) {
        int index = 0;
        for (BluetoothServer bluetoothServer : mServeurConnectedList) {
            if (addressClientConnectionFailed.equalsIgnoreCase(bluetoothServer.getClientAddress())) {
                mServeurConnectedList.get(index).closeConnection();
                mServeurConnectedList.remove(index);
                mServeurWaitingConnectionList.get(addressClientConnectionFailed).closeConnection();
                mServeurWaitingConnectionList.remove(addressClientConnectionFailed);
                mServeurThreadList.get(addressClientConnectionFailed).interrupt();
                mServeurThreadList.remove(addressClientConnectionFailed);
                mAdressListServerWaitingConnection.remove(addressClientConnectionFailed);
                decrementNbrConnection();
                Log.i("", "===> onServerConnectionFailed address : " + addressClientConnectionFailed);
                return;
            }
            index++;
        }
        setIsConnected();
    }

    public void sendMessage(byte[] message) throws NetworkNotConnectedException{
        boolean send = false;
        if (mType != null) {
            if (mServeurConnectedList != null) {
                Log.i(TAG, "Sending message to clients");
                for (int i = 0; i < mServeurConnectedList.size(); i++) {
                        send = true;
                        mServeurConnectedList.get(i).write(message);
                }
            }
            if (mBluetoothClient != null) {
                Log.i(TAG, "Sending message to server");
                send = true;
                mBluetoothClient.write(message);
            }
        }
        if(!send) throw new NetworkNotConnectedException();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        Log.i(TAG, "A device was found, posting it into the ps channel");
        if (intent.getAction().equalsIgnoreCase(BluetoothDevice.ACTION_FOUND)) {
            if ((mType == TypeBluetooth.Client && !isConnected)
                    || (mType == TypeBluetooth.Server && !mAdressListServerWaitingConnection.contains(device.getAddress()))
                    || mType == TypeBluetooth.Dual && (!isConnected || !mAdressListServerWaitingConnection.contains(device.getAddress()))) {
                devicesFound.add(device);
                if(!isNbrMaxReached()){
                    createServeur(device.getAddress());
                }
                //service.handleClientConnection(device);
            }
        }
        if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
            //Log.e("", "===> ACTION_BOND_STATE_CHANGED");
            int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            if (prevBondState == BluetoothDevice.BOND_BONDING) {
                // check for both BONDED and NONE here because in some error cases the bonding fails and we need to fail gracefully.
                if (bondState == BluetoothDevice.BOND_BONDED || bondState == BluetoothDevice.BOND_NONE) {
                    //Log.e("", "===> BluetoothDevice.BOND_BONDED");
                    service.handleClientConnection(new BondedDevice());
                }
            }
        }

        if (intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
            if(mBluetoothClient == null && mType == TypeBluetooth.Dual){
                if(!devicesFound.isEmpty()){
                    if(mBluetoothClient == null)
                        service.handleClientConnection(devicesFound.get(0));
                    scanAllBluetoothDevice();
                }
            }
        }

    }

    public void disconnectClient() {
        //mType = TypeBluetooth.None;
        cancelDiscovery();
        resetClient();
        scanAllBluetoothDevice();
    }

    public void disconnectServer() {
        //mType = TypeBluetooth.None;
        cancelDiscovery();
        resetServer();
        scanAllBluetoothDevice();
    }

    public void resetServer() {
        if (mServeurConnectedList != null) {
            for (int i = 0; i < mServeurConnectedList.size(); i++) {
                mServeurConnectedList.get(i).closeConnection();
            }
        }
        if (mServeurConnectedList != null)
            mServeurConnectedList.clear();
    }

    public void resetClient() {
        if (mBluetoothClient != null) {
            mBluetoothClient.closeConnection();
            mBluetoothClient = null;
        }
    }

    public void closeAllConnections() {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.setName(mBluetoothNameSaved);
            cancelDiscovery();

            //Kill all server connections
            if(mServeurConnectedList != null) {
                for (BluetoothServer server : mServeurConnectedList)
                    server.closeConnection();
                mServeurConnectedList.clear();
                mServeurConnectedList = null;
            }
            resetWaitingThreadServer();
            mAdressListServerWaitingConnection = null;
            mServeurThreadList = null;
            mServeurWaitingConnectionList = null;
            devicesFound.clear();
            devicesFound = null;

            if(mBluetoothClient != null){
                mBluetoothClient.closeConnection();
                mBluetoothClient = null;
            }

            mBluetoothAdapter.disable();

        }
        try {
            context.unregisterReceiver(this);
        } catch (Exception e) {
        }

        mBluetoothAdapter = null;
        EventBus.getDefault().unregister(this);
        if (mType != null) {
            resetServer();
            resetClient();
            Log.i(TAG, "All Bluetooth connections were released (devices might still be paired)");
        }


    }

    @Subscribe
    public void onEventMainThread(BluetoothDevice device) {
        service.handleClientConnection(device);
    }

    @Subscribe
    public void onEventMainThread(ClientConnectionSuccess event) {
        service.onClientConnectionSuccess();
        Log.i(TAG, "I connected to " + mBluetoothClient.getAddress());
    }

    @Subscribe
    public void onEventMainThread(ClientConnectionFail event) {
        Log.i(TAG, "Client connection failed");
        disconnectClient();
    }

    @Subscribe
    public void onEventMainThread(ServerConnectionSuccess event) {
        onServerConnectionSuccess(event.mClientAdressConnected);
        Log.i(TAG, event.mClientAdressConnected + " connected to me");
    }

    @Subscribe
    public void onEventMainThread(ServerConnectionFail event) {
        Log.i(TAG, "Server connection failure. Releasing " + event.mClientAdressConnectionFail);
        onServerConnectionFailed(event.mClientAdressConnectionFail);
    }

    @Subscribe
    public void onEventMainThread(BluetoothCommunicator event) {
        service.onBluetoothCommunicator(event.mBytesReceive);
    }

    @Subscribe
    public void onEventMainThread(BondedDevice event) {
        //mBluetoothManager.sendMessage("BondedDevice");
    }

    /**
     * Mode of operation
     */
    public enum TypeBluetooth {
        Client,
        Server,
        Dual,
        None
    }

    /**
     * Mode of security
     */
    public enum Mode {
        SECURE,
        INSECURE
    }

    public String getState(){
        StringBuffer result = new StringBuffer();
        result.append("Server: ");
        if(mBluetoothClient != null)
            result.append(mBluetoothClient.getAddress());
        else result.append("null");
        result.append("\n").append("Clients: ").append("\n");
        for(BluetoothServer server: mServeurConnectedList)
            result.append(server.getClientAddress()).append("\n");
        return result.toString();
    }
}
