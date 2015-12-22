package bluetoothlibrary.bluetooth.manager;

import android.app.Activity;
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

import bluetoothlibrary.bluetooth.client.BluetoothClient;
import bluetoothlibrary.bluetooth.server.BluetoothServer;
import bluetoothlibrary.bus.BondedDevice;
import de.greenrobot.event.EventBus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothManager extends BroadcastReceiver {

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
    private Activity mActivity;
    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothClient mBluetoothClient;

    private ArrayList<String> mAdressListServerWaitingConnection;
    private HashMap<String, BluetoothServer> mServeurWaitingConnectionList;
    private ArrayList<BluetoothServer> mServeurConnectedList;
    private HashMap<String, Thread> mServeurThreadList;
    private int mNbrClientConnection;
    private int mTimeDiscoverable;
    //public boolean thisClientConnectedToServer = false;
    private boolean mBluetoothIsEnableOnStart;
    private String mBluetoothNameSaved;
    private boolean secure;
    public BluetoothManager(Activity activity, Mode mode) {
        mActivity = activity;
        secure = mode == Mode.SECURE;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothNameSaved = mBluetoothAdapter.getName();
        mBluetoothIsEnableOnStart = mBluetoothAdapter.isEnabled();
        mType = TypeBluetooth.None;
        isConnected = false;
        mNbrClientConnection = 0;
        mAdressListServerWaitingConnection = new ArrayList<String>();
        mServeurWaitingConnectionList = new HashMap<String, BluetoothServer>();
        mServeurConnectedList = new ArrayList<BluetoothServer>();
        mServeurThreadList = new HashMap<String, Thread>();
        setTimeDiscoverable(BLUETOOTH_TIME_DISCOVERY_INFINITE);
    }

    public boolean isConnectedToServer() {
        return mBluetoothClient != null;
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
        if (mBluetoothAdapter == null) {
            return false;
        } else {
            return true;
        }
    }

    public void cancelDiscovery() {
        if (isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    public boolean isDiscovering() {
        return mBluetoothAdapter.isDiscovering();
    }

    public void startDiscovery() {
        if (mBluetoothAdapter == null) {
            return;
        } else {
            if (mBluetoothAdapter.isEnabled() && isDiscovering()) {
                Log.e("", "===> mBluetoothAdapter.isDiscovering()");
                return;
            } else {
                Log.e("", "===> startDiscovery");
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, mTimeDiscoverable);
                mActivity.startActivity/*ForResult*/(discoverableIntent/*, REQUEST_DISCOVERABLE_CODE*/);
            }
        }
    }

    public void scanAllBluetoothDevice() {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mActivity.registerReceiver(this, intentFilter);
        mBluetoothAdapter.startDiscovery();
    }

    public void createClient(String addressMac) {
        if (mType == TypeBluetooth.Client || mType == TypeBluetooth.Dual) {
            IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            mActivity.registerReceiver(this, bondStateIntent);
            if (mBluetoothClient == null) {
                mBluetoothClient = new BluetoothClient(mBluetoothAdapter, addressMac, secure);
                new Thread(mBluetoothClient).start();
            }
        }
    }

    public void createServeur(String address) {
        if ((mType == TypeBluetooth.Server || mType == TypeBluetooth.Dual) && !mAdressListServerWaitingConnection.contains(address)) {
            if (mBluetoothClient == null || !mBluetoothClient.getAddress().equalsIgnoreCase(address)) {
                BluetoothServer mBluetoothServer = new BluetoothServer(mBluetoothAdapter, address, secure);
                Thread threadServer = new Thread(mBluetoothServer);
                threadServer.start();
                setServerWaitingConnection(address, mBluetoothServer, threadServer);
                IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                mActivity.registerReceiver(this, bondStateIntent);
                Log.e("", "===> createServeur address : " + address);
            }
        }
    }

    public void onServerConnectionSuccess(String addressClientConnected) {
        for (Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionList.entrySet()) {
            if (addressClientConnected.equals(bluetoothServerMap.getValue().getClientAddress())) {
                mServeurConnectedList.add(bluetoothServerMap.getValue());
                incrementNbrConnection();
                Log.e("", "===> onServerConnectionSuccess address : " + addressClientConnected);
                return;
            }
        }
    }

    public void onServerConnectionFailed(String addressClientConnectionFailed) {
        int index = 0;
        for (BluetoothServer bluetoothServer : mServeurConnectedList) {
            if (addressClientConnectionFailed.equals(bluetoothServer.getClientAddress())) {
                mServeurConnectedList.get(index).closeConnection();
                mServeurConnectedList.remove(index);
                mServeurWaitingConnectionList.get(addressClientConnectionFailed).closeConnection();
                mServeurWaitingConnectionList.remove(addressClientConnectionFailed);
                mServeurThreadList.get(addressClientConnectionFailed).interrupt();
                mServeurThreadList.remove(addressClientConnectionFailed);
                mAdressListServerWaitingConnection.remove(addressClientConnectionFailed);
                decrementNbrConnection();
                Log.e("", "===> onServerConnectionFailed address : " + addressClientConnectionFailed);
                return;
            }
            index++;
        }
    }

    public void sendMessage(String message) {
        if (mType != null && isConnected) {
            if (mServeurConnectedList != null) {
                for (int i = 0; i < mServeurConnectedList.size(); i++) {
                    //mServeurConnectedList.get(i).write(message);
                }
            }
            if (mBluetoothClient != null) {
                mBluetoothClient.write(message);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
            if ((mType == TypeBluetooth.Client && !isConnected)
                    || (mType == TypeBluetooth.Server && !mAdressListServerWaitingConnection.contains(device.getAddress()))
                    || mType == TypeBluetooth.Dual && (!isConnected || !mAdressListServerWaitingConnection.contains(device.getAddress()))) {

                EventBus.getDefault().post(device);
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
                    EventBus.getDefault().post(new BondedDevice());
                }
            }
        }

    }

    public void disconnectClient() {
        //mType = TypeBluetooth.None;
        cancelDiscovery();
        resetClient();
    }

    public void disconnectServer() {
        //mType = TypeBluetooth.None;
        cancelDiscovery();
        resetServer();
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
            mBluetoothClient.closeConnexion();
            mBluetoothClient = null;
        }
    }

    public void closeAllConnections() {
        mBluetoothAdapter.setName(mBluetoothNameSaved);

        try {
            mActivity.unregisterReceiver(this);
        } catch (Exception e) {
        }

        cancelDiscovery();

        if (!mBluetoothIsEnableOnStart) {
            mBluetoothAdapter.disable();
        }

        mBluetoothAdapter = null;

        if (mType != null) {
            resetServer();
            resetClient();
        }


    }

    /**
     * Mode of operation
     */
    public enum TypeBluetooth {
        Client,
        Server,
        Dual,
        None;
    }

    /**
     * Mode of security
     */
    public enum Mode {
        SECURE,
        INSECURE
    }
}
