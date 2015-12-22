package de.tu_darmstadt.adtn.sendingpool;

/**
 * An entry of the sending pool.
 */
public class SendingPoolEntry {

    private byte[] packet;
    private byte[] messageID;
    private byte[] usedKey;

    /**
     * Creates a sending pool entry.
     *
     * @param packet    The packet to send.
     * @param messageID The ID of the corresponding message or null if this is a dummy packet.
     */
    public SendingPoolEntry(byte[] packet, byte[] messageID, byte[] usedKey) {
        this.packet = packet;
        this.messageID = messageID;
        this.usedKey = usedKey;
    }

    /**
     * @return The packet to send.
     */
    public byte[] getPacket() {
        return packet;
    }

    /**
     * @return The ID of the corresponding message or null if this is a dummy packet.
     */
    public byte[] getMessageID() {
        return messageID;
    }

    public byte[] getUsedKey(){return usedKey;}
}
