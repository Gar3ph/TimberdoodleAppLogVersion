package de.tu_darmstadt.adtn.sendingpool;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

import javax.crypto.SecretKey;

import de.tu_darmstadt.adtn.AdtnSocketException;
import de.tu_darmstadt.adtn.ISocket;
import de.tu_darmstadt.adtn.groupkeystore.IGroupKeyStore;
import de.tu_darmstadt.adtn.logging.loggers.SelectLogger;
import de.tu_darmstadt.adtn.logging.loggers.SendLogger;
import de.tu_darmstadt.adtn.messagestore.IMessageStore;
import de.tu_darmstadt.adtn.messagestore.Message;
import de.tu_darmstadt.adtn.packetbuilding.IPacketBuilder;
import de.tu_darmstadt.adtn.preferences.IPreferences;

/**
 * Wraps messages from the message store in packets and stores them.
 * The packets are then broadcasted to network in batches.
 */
public class SendingPool implements ISendingPool {

    private volatile int sendInterval;
    private volatile int refillThreshold;
    private volatile int batchSize;
    private LinkedList<SendingPoolEntry> entries = new LinkedList<>();
    private Thread thread;
    private Random random = new Random();
    private OnSendingErrorListener onSendingErrorListener;
    private IPreferences preferences;
    private IPreferences.OnCommitListener preferencesListener = new de.tu_darmstadt.adtn.genericpreferences.Preferences.OnCommitListener() {
        @Override
        public void onCommit() {
            loadPreferences();
        }
    };
    private ISocket socket;
    private IMessageStore messageStore;
    private IPacketBuilder packetBuilder;
    private IGroupKeyStore groupKeyStore;

    /**
     * Creates the sending pool object.
     *
     * @param preferences   A preferences object to configure the sending pool.
     * @param socket        The socket to use for sending the packets.
     * @param messageStore  The message store to fetch the messages from.
     * @param packetBuilder The packet builder to create packets for a message.
     * @param groupKeyStore The key store containing the keys to encrypt the packets.
     */
    public SendingPool(IPreferences preferences, ISocket socket, IMessageStore messageStore,
                       IPacketBuilder packetBuilder, IGroupKeyStore groupKeyStore,
                       OnSendingErrorListener onSendingErrorListener) {
        // Store references
        this.preferences = preferences;
        this.socket = socket;
        this.messageStore = messageStore;
        this.packetBuilder = packetBuilder;
        this.groupKeyStore = groupKeyStore;
        this.onSendingErrorListener = onSendingErrorListener;

        // Register preferences listener and load current preferences
        preferences.addOnCommitListenerListener(preferencesListener);
        loadPreferences();

        // Start worker thread
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        long millis = System.currentTimeMillis();
                        refill(); // Fetch messages from store
                        //if (!sendBatch()) break; // Send the messages
                        sendBatch();
                        // Wait between sending of two batches
                        long wait = sendInterval * 1000 - (System.currentTimeMillis() - millis);
                        if (wait > 0) {
                            Thread.sleep(wait);
                        }
                    } catch (InterruptedException e) {
                        break; // Cancel if interrupted by shutdown()
                    }
                }
            }
        });
        thread.start();
    }

    private void loadPreferences() {
        sendInterval = preferences.getSendingPoolSendInterval();
        refillThreshold = preferences.getSendingPoolRefillThreshold();
        batchSize = preferences.getSendingPoolBatchSize();
    }

    /**
     * Cancels message processing. Note that this could block if message store or network are
     * blocking.
     */
    @Override
    public void close() {
        thread.interrupt();

        // Wait until worker thread stopped. Postpone any interruptions of current thread.
        boolean currentThreadWasInterrupted = false;
        while (true) {
            try {
                thread.join();
                break;
            } catch (InterruptedException e) {
                currentThreadWasInterrupted = true;
            }
        }
        if (currentThreadWasInterrupted) Thread.currentThread().interrupt();

        // Do not leak the preferences listener object
        preferences.removeOnCommitListener(preferencesListener);
    }

    // Wraps messages from the message store in packets and adds them to the sending pool
    private void refill() {
        Log.i("SendingPool", "Refill was invoked");
        // No need to refill?
        //if (entries.size() >= refillThreshold) return;
        if (entries.size() >= 1) return;
        // Calculate how many messages are needed to reach threshold
        Collection<SecretKey> keys = groupKeyStore.getKeys();
        SecretKey[] keyArray = new SecretKey[keys.size()];
        keys.toArray(keyArray);
        int numKeys = keys.size();
        if (numKeys == 0) return; // Cannot create any packets without keys
        //int numMessages = (refillThreshold - entries.size() + numKeys - 1) / numKeys;
        int numMessages = 1;
        int i = 0;
        // Wrap messages in packets so they are ready to send and store them in pool
        for (Message message : messageStore.getNextMessagesToSend(numMessages)) {
            i = 0;
            SelectLogger.getInstance().log(message.getContent());
            for (byte[] packet : packetBuilder.createPackets(message.getContent(), keyArray)) {
                entries.add(new SendingPoolEntry(packet, message.getID(), keyArray[i].getEncoded()));
                i++;
                try {
                    Log.i("SendingPool", "Got " + new String(message.getContent(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {

                }
            }
        }
    }

    /* Sends a batch containing packets for the messages that are currently in the pool.
     * Random packets will be interspersed until batch size is reached.
     * Returns true on success or false if sending failed. */
    private boolean sendBatch() {
        SendingPoolEntry[] batch = new SendingPoolEntry[batchSize];
        //SendingPoolEntry[] batch = new SendingPoolEntry[1];
        // Move as much pool entries to batch as possible
        int i;
        for (i = 0; i < batch.length && !entries.isEmpty(); ++i) {
            batch[i] = entries.remove(random.nextInt(entries.size()));
        }
        int j = 0;
        while(j < batch.length && batch[j] != null)
            j++;
        i = j;

        // Fill batch with random data packets if there are no more entries in pool
        if (i < batch.length) {
            while (i < batch.length) {
                batch[i] = new SendingPoolEntry(packetBuilder.createRandomPacket(), null, null);
                //batch[i] = new SendingPoolEntry(new byte[ProtocolConstants.MAX_PACKET_SIZE], null, 0);
                ++i;
            }

            // Shuffle batch so random data packets do not necessarily appear at the end
            Collections.shuffle(Arrays.asList(batch));
        }

        // Finally send the packets stored in batch
        for (SendingPoolEntry entry : batch) {
            // Try to send the packet
            try {
                socket.send(entry.getPacket(), 0);
                if(entry.getUsedKey() != null)
                    SendLogger.getInstance().log(entry.getMessageID(), entry.getUsedKey());
            } catch (AdtnSocketException e) {
                if(!e.getMessage().equals("Network not ready yet"))
                    onSendingErrorListener.onSendingError(e);
                return false;
            }

            // Update statistics for message if this is not a dummy packet
            if (entry.getMessageID() != null) messageStore.sentMessage(entry.getMessageID());
        }

        return true;
    }
}
