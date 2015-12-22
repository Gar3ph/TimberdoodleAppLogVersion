package de.tu_darmstadt.adtn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;

import javax.crypto.SecretKey;

import bluetoothlibrary.network.BTNetworkService;
import de.tu_darmstadt.adtn.ciphersuite.GroupCipherSuite;
import de.tu_darmstadt.adtn.ciphersuite.IGroupCipher;
import de.tu_darmstadt.adtn.errorlogger.ErrorLoggingSingleton;
import de.tu_darmstadt.adtn.groupkeyshareexpirationmanager.GroupKeyShareExpirationManager;
import de.tu_darmstadt.adtn.groupkeyshareexpirationmanager.IGroupKeyShareExpirationManager;
import de.tu_darmstadt.adtn.groupkeystore.GroupKeyStore;
import de.tu_darmstadt.adtn.groupkeystore.IGroupKeyStore;
import de.tu_darmstadt.adtn.logging.loggers.CreateLogger;
import de.tu_darmstadt.adtn.logging.loggers.ReceiveLogger;
import de.tu_darmstadt.adtn.logging.loggers.SelectLogger;
import de.tu_darmstadt.adtn.logging.loggers.SendLogger;
import de.tu_darmstadt.adtn.messagestore.IMessageStore;
import de.tu_darmstadt.adtn.messagestore.MessageStore;
import de.tu_darmstadt.adtn.packetbuilding.IPacketBuilder;
import de.tu_darmstadt.adtn.packetbuilding.PacketBuilder;
import de.tu_darmstadt.adtn.packetsocket.PacketSocket;
import de.tu_darmstadt.adtn.preferences.IPreferences;
import de.tu_darmstadt.adtn.preferences.Preferences;
import de.tu_darmstadt.adtn.sendingpool.ISendingPool;
import de.tu_darmstadt.adtn.sendingpool.SendingPool;
import de.tu_darmstadt.adtn.ui.NetworkingStatusNotification;
import de.tu_darmstadt.adtn.wifi.IbssNetwork;
import de.tu_darmstadt.adtn.wifi.MacSpoofing;
import de.tu_darmstadt.timberdoodle.R;

/**
 * The aDTN service.
 */
public class Service extends android.app.Service implements IService {

    //region Service binding

    /**
     * A binder to obtain the service object once the service is started.
     */
    public class LocalBinder extends Binder {
        /**
         * @return The service object.
         */
        public Service getService() {
            return Service.this;
        }
    }

    // The binder that gets returned in onBind()
    private final LocalBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    //endregion

    private CreateLogger create = CreateLogger.getInstance();
    private ReceiveLogger receive = ReceiveLogger.getInstance();
    private SelectLogger select = SelectLogger.getInstance();
    private SendLogger send = SendLogger.getInstance();

    private IPreferences preferences;

    // Sending and receiving
    private IMessageStore messageStore;
    private IPacketBuilder packetBuilder;
    private ISocket socket;
    private ISendingPool sendingPool;
    private Thread receiveThread;
    private volatile boolean stopReceiving;

    // Encryption
    private IGroupCipher groupCipher;

    // Key store
    private final static String GROUP_KEY_STORE_FILENAME = "network_group_keys";
    private final Object groupKeyStoreLock = new Object();
    private IGroupKeyStore groupKeyStore;

    // Group key share expiration
    private final static long GROUP_KEY_SHARE_EXPIRATION_INTERVAL = 5 * 60000; // 5 minutes
    private IGroupKeyShareExpirationManager expirationManager;

    // Networking state
    private final Object networkingStartStopLock = new Object();
    private volatile NetworkingStatus networkingStatus;
    private NetworkingStatusNotification statusNotification;

    // For sending message arrival broadcast intents to other application components
    private LocalBroadcastManager broadcastManager;

    // Keeps the ad-hoc network alive
    private IbssNetwork ibssNetwork;

    private final String TAG = "adtnService";
    // Bluetooth service
    private BTNetworkService nService;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BTNetworkService.LocalBinder binder = (BTNetworkService.LocalBinder) service;
            Service.this.nService = (BTNetworkService)binder.getService();
            Log.i(TAG, "Service was connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Service.this.nService = null;
            Log.i(TAG, "Service connection lost/not established");
        }
    };

    /**
     * Binds to the Timberdoodle service.
     *
     * @param serviceConnection The service connection to use in the call to bindService.
     */
    public void bindBTService(ServiceConnection serviceConnection) {
        if (!bindService(new Intent(this, BTNetworkService.class), serviceConnection, Context.BIND_AUTO_CREATE)) {
            throw new RuntimeException("Could not bind to service");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bindBTService(connection);

        // Initialise logger
        ErrorLoggingSingleton.getInstance().setContext(getApplicationContext());

        // Set up networking status
        statusNotification = new NetworkingStatusNotification(this);
        setNetworkingStatus(false, null);

        preferences = new Preferences(this);

        // Initialize group cipher, packet builder and broadcast manager
        packetBuilder = new PacketBuilder(ProtocolConstants.MAX_MESSAGE_SIZE);
        groupCipher = new GroupCipherSuite(packetBuilder.getUnencryptedPacketSize());
        packetBuilder.setCipher(groupCipher);
        broadcastManager = LocalBroadcastManager.getInstance(this);

        /* Initialize message store even without networking enabled, so messages to send will be
         * collected and get sent as soon as networking is enabled.
         */
        messageStore = new MessageStore(this);

        // Create group key share expiration manager
        expirationManager = new GroupKeyShareExpirationManager(this, GROUP_KEY_SHARE_EXPIRATION_INTERVAL);

        byte[] key;
        TelephonyManager tm = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            key = tm.getDeviceId().getBytes();
            create.setUserID(key);
            receive.getInstance().setUserID(key);
            select.getInstance().setUserID(key);
            send.getInstance().setUserID(key);
        }
    }

    @Override
    public void onDestroy() {
        // Do cleanup in reverse order
        Log.i(TAG, "onDestroy in adtnService was invoked");
        stopNetworking(null, false);
        expirationManager.store();
        IGroupKeyStore keyStore = getGroupKeyStore();
        if (keyStore != null) keyStore.save();
        messageStore.close();

        super.onDestroy();
    }

    /**
     * Starts receiving messages and processing the sending pool.
     */
    @Override
    public void startNetworking() {
        Log.i(TAG, "startNetworking was invoked on adtn service");
        synchronized (networkingStartStopLock) {
            // Do nothing if already started
            if (networkingStatus.getStatus() == NetworkingStatus.STATUS_ENABLED) return;

            // Check if group key store is present
            if (getGroupKeyStore() == null) {
                setNetworkingStatus(false, getString(R.string.group_key_store_not_accessible));
                return;
            }

            // Keep running in background
            startService(new Intent(this, Service.class));

            if(preferences.getAutoJoinAdHocNetwork()) {
                // Try to spoof MAC address
                MacSpoofing.trySetRandomMac();
                // Enable ad-hoc auto-connect and set up preference listener
                ibssNetwork = new IbssNetwork(this);
                if (preferences.getAutoJoinAdHocNetwork()) ibssNetwork.start();
                preferences.addOnCommitListenerListener(new de.tu_darmstadt.adtn.genericpreferences.IPreferences.OnCommitListener() {
                    @Override
                    public void onCommit() {
                        if (preferences.getAutoJoinAdHocNetwork()) {
                            ibssNetwork.start();
                        } else {
                            ibssNetwork.stop();
                        }
                    }
                });

                // Create socket, message store and sending pool
                socket = new PacketSocket(this, "wlan0", 0xD948,
                        new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
                        new byte[]{(byte) 0x00, (byte) 0x41, (byte) 0xAC, (byte) 0xC2, (byte) 0x96, (byte) 0xC6},
                        packetBuilder.getEncryptedPacketSize());
            } else if(preferences.getAutoBluetooth()){
                Log.i(TAG, "adtnService tries to start networking in bluetooth service");
                nService.startNetworking();
                Log.i(TAG, "adtnService started networking");
                preferences.addOnCommitListenerListener(new de.tu_darmstadt.adtn.genericpreferences.IPreferences.OnCommitListener() {
                    @Override
                    public void onCommit() {
                        if (preferences.getAutoBluetooth())
                            nService.startNetworking();
                        else
                            nService.stopNetworking();
                    }
                });
                socket = nService.getSocket();
            }
            sendingPool = new SendingPool(preferences, socket, messageStore, packetBuilder,
                    groupKeyStore, new ISendingPool.OnSendingErrorListener() {
                @Override
                public void onSendingError(AdtnSocketException e) {
                    stopNetworking(getString(R.string.sending_error) + e.getLocalizedMessage(), true);
                }
            });

            // Start receiving
            stopReceiving = false;
            receiveThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveMessages();
                }
            });
            receiveThread.start();

            setNetworkingStatus(true, null);
        }
    }

    /**
     * Stops receiving messages and processing the sending pool.
     */
    @Override
    public void stopNetworking() {
        stopNetworking(null, true);
    }

    /**
     * @return The current networking status.
     */
    @Override
    public NetworkingStatus getNetworkingStatus() {
        return networkingStatus;
    }

    /**
     * Sets the current networking status info.
     *
     * @param enabled      true if the service is enabled or false if disabled. Ignored if
     *                     errorMessage is not null.
     * @param errorMessage If not null, the state is set to "error" with the specified message.
     */
    private void setNetworkingStatus(boolean enabled, String errorMessage) {
        if (errorMessage == null) {
            networkingStatus = new NetworkingStatus(enabled);
        } else {
            networkingStatus = new NetworkingStatus(errorMessage);
        }

        statusNotification.setStatus(networkingStatus);
    }

    /**
     * Stops networking.
     *
     * @param errorMessage The error message to set or null. If null, the networking state will be
     *                     set to "disabled". Otherwise it will be set to "error".
     * @param async        If true, the method will not block until networking stopped. Otherwise the
     *                     method blocks until networking is stopped.
     */
    private void stopNetworking(final String errorMessage, boolean async) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (networkingStartStopLock) {
                    // Do nothing if already stopped
                    if (networkingStatus.getStatus() != NetworkingStatus.STATUS_ENABLED) return;

                    // Stop sending and receiving
                    sendingPool.close();
                    stopReceiving = true;
                    socket.close();
                    joinReceiveThread();

                    // Stop ad-hoc auto-connect
                    ibssNetwork.stop();

                    // Stop service if no one binds to it
                    stopSelf();

                    // Disable MAC spoofing
                    MacSpoofing.tryDisable();

                    setNetworkingStatus(false, errorMessage);
                }
            }
        };

        // Run either synchronous or asynchronous
        if (async) {
            new Thread(runnable).start();
        } else {
            runnable.run();
        }
    }

    /**
     * Puts a message in the sending pool so it will be sent when networking is available.
     *
     * @param header  The message header.
     * @param content The message content.
     */
    @Override
    public void sendMessage(byte header, byte[] content) {
        try {
            Log.i(TAG, "A message was authored and passed to the adtn service. The message is: " + new String(content, "UTF-8") );
        } catch (UnsupportedEncodingException e) {

        }
        if (content.length > ProtocolConstants.MAX_MESSAGE_CONTENT_SIZE) {
            throw new RuntimeException("Content size exceeds maximum allowed size");
        }

        // Merge header and content and put them in the message store
        byte[] message = new byte[ProtocolConstants.MESSAGE_HEADER_SIZE + content.length];
        message[0] = header;
        System.arraycopy(content, 0, message, 1, content.length);
        messageStore.addMessage(message);
        CreateLogger.getInstance().log(message);
    }

    /* Thread function to continuously receive messages, put them in the message store and send
     * a broadcast intent to inform about the message arrival */
    private void receiveMessages() {
        byte[] receiveBuffer = new byte[packetBuilder.getEncryptedPacketSize()];

        while (true) {
            // Receive encrypted packet
            try {
                socket.receive(receiveBuffer, 0);
                Thread.sleep(10000);
            } catch (AdtnSocketException|InterruptedException e) {
                // receive() fails if networking is stopping or if an actual error occurred
                if (stopReceiving) {
                    stopNetworking(getString(R.string.receiving_error) + e.getLocalizedMessage(), true);
                }
                break;
            }
            Log.i(TAG, "Message reached adtn service");
            // Try to decrypt. Skip if not possible.
            SecretKey[] keys = new SecretKey[groupKeyStore.getKeys().size()];
            byte[] unpacked = packetBuilder.tryUnpackPacket(receiveBuffer, groupKeyStore.getKeys().toArray(keys));
            if (unpacked == null) continue;

            // Ignore if already received
            if (messageStore.receivedMessage(unpacked)) continue;

            // Notify of message arrival via broadcast intent
            Intent intent = new Intent(ACTION_HANDLE_RECEIVED_MESSAGE);
            intent.putExtra(INTENT_ARG_HEADER, unpacked[0]);
            intent.putExtra(INTENT_ARG_CONTENT, Arrays.copyOfRange(unpacked, 1, unpacked.length));
            broadcastManager.sendBroadcast(intent);
        }
    }

    // Blocks until the receive thread stopped
    private void joinReceiveThread() {
        boolean currentThreadWasInterrupted = false;
        while (true) {
            try {
                receiveThread.join();
                break;
            } catch (InterruptedException e) {
                currentThreadWasInterrupted = true;
            }
        }
        if (currentThreadWasInterrupted) Thread.currentThread().interrupt();
    }

    /**
     * @return The service preferences.
     */
    @Override
    public IPreferences getPreferences() {
        return preferences;
    }

    /**
     * @return The group cipher object.
     */
    @Override
    public IGroupCipher getGroupCipher() {
        return groupCipher;
    }

    /**
     * @return The group key store.
     */
    @Override
    public IGroupKeyStore getGroupKeyStore() {
        synchronized (groupKeyStoreLock) {
            return groupKeyStore;
        }
    }

    /**
     * @return The group key share expiration manager.
     */
    @Override
    public IGroupKeyShareExpirationManager getExpirationManager() {
        return expirationManager;
    }

    /**
     * Tries to open the key store with the given password.
     * @param password The password for the key store.
     *
     * @return true if the password was correct or false otherwise.
     */
    @Override
    public boolean openGroupKeyStore(String password) {
        try {
            synchronized (groupKeyStoreLock) {
                // Do nothing if already loaded
                if (groupKeyStore != null) return true;

                groupKeyStore = new GroupKeyStore(this, groupCipher, GROUP_KEY_STORE_FILENAME, password, false);
                return true;
            }
        } catch (UnrecoverableKeyException e) {
            return false;
        }
    }

    /**
     * Tell the service to reset the key store and use the specified password as its new password.
     * This will delete the old key store and create an empty new one. Only call this to create a
     * key store for the first time or if the user forgot the password.
     *
     * @param password The password for the new key store.
     */
    @Override
    public void createGroupKeyStore(String password) {
        try {
            synchronized (groupKeyStoreLock) {
                // Do nothing if already loaded
                if (groupKeyStore != null) return;

                groupKeyStore = new GroupKeyStore(this, groupCipher, GROUP_KEY_STORE_FILENAME, password, true);
            }
        } catch (UnrecoverableKeyException e) {
            ErrorLoggingSingleton log = ErrorLoggingSingleton.getInstance();
            log.storeError(ErrorLoggingSingleton.getExceptionStackTraceAsFormattedString(e));
            throw new RuntimeException(e);
        }
    }

    /**
     * @return true if the key store exists or false otherwise.
     */
    @Override
    public boolean groupKeyStoreExists() {
        return getGroupKeyStore() != null || getFileStreamPath(GROUP_KEY_STORE_FILENAME).exists();
    }
}
