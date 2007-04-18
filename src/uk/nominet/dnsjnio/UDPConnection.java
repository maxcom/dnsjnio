/*
 The contents of this file are subject to the Mozilla
 Public Licence Version 1.1 (the "Licence"); you may
 not use this file except in compliance with the
 Licence. You may obtain a copy of the Licence at
 http://www.mozilla.org/MPL
 Software distributed under the Licence is distributed
 on an "AS IS" basis,  WITHOUT WARRANTY OF ANY KIND,
 either express or implied. See the Licence of the
 specific language governing rights and limitations
 under the Licence.
 The Original Code is dnsjnio.
 The Initial Developer of the Original Code is
 Nominet UK (www.nominet.org.uk). Portions created by
 Nominet UK are Copyright (c) Nominet UK 2006.
 All rights reserved.
*/
package uk.nominet.dnsjnio;

import org.xbill.DNS.Message;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

/**
 * This class implements the UDP specific methods for the
 * Connection superclass.
 */
public class UDPConnection extends Connection {

    public UDPConnection(ConnectionListener listener, int udpSize) {
        super(listener, udpSize);
    }

    protected void connect() {
        try {
            DatagramChannel sch = DatagramChannel.open();
            sch.configureBlocking(false);
            InetSocketAddress remote = new InetSocketAddress(getHost(), getPort());
            sk = sch.register(DnsController.getSelector(),0);
            sch.connect(remote);
            attach(sk);
        } catch(Exception e) {
            e.printStackTrace();
            close();
        }
    }


    /**
     * attach key and channel and set connection interest in selection key
     */
    public void attach(SelectionKey sk) {
        this.sk = sk;
        sk.attach(this);
        DatagramChannel sch = (DatagramChannel) sk.channel();
        if(sch.isConnected()) {
            sk.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            setState(State.OPENED);
        }
    }

    /**
     * process a connect complete selection
     */
    public void doConnect()	{
        // Nothing to do for UDP
    }

    /**
     * process a read ready selection
     */
    public void doRead() {
        DatagramChannel sc = (DatagramChannel)sk.channel();
        try {
            readFromChannel(sc);
        }
        catch (NullPointerException e) {
            return;
        }

        while (recvCount > 0) {
            if (recvBytes != null) {
                byte[] packet = new byte[recvCount];
                System.arraycopy(recvBytes, 0, packet, 0, recvCount);
                try {
                    Message m = new Message(packet);
                    if (m.toWire().length < recvCount) {
                        packet = new byte[m.toWire().length];
                        System.arraycopy(recvBytes, 0, packet, 0, m.toWire().length);
                    }
                    sendToUser(packet); // try to send to user
                    // Now clear the buffer
                    clearRecvBytes(packet.length);
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    /**
     * write out a byte buffer
     */
    protected void write(ByteBuffer data) {
        DatagramChannel sc = (DatagramChannel)sk.channel();
        if(sc.isOpen()) {
            if(data.hasRemaining()) {
                try {
                    sc.write(data);
                } catch(IOException e) {
                    closeComplete();
                }
            }
            commonEndWrite(data);
        }
    }

    /**
     * close the connection and its socket
     */
    protected void close() {
        if((getState() != State.CLOSED) && (getState() != State.CLOSING)) {
            DatagramChannel sc = (DatagramChannel)sk.channel();
            if(sc.isOpen()) {
                if(getState() == State.OPENED) {
                    sk.interestOps(0);
                    setState(State.CLOSING);
                    try {
                        sc.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        // log error
                    }
                }
                closeComplete();
            }
        }
    }

    protected void closeChannel() throws IOException {
        DatagramChannel sc = (DatagramChannel)sk.channel();
        if(sc != null && sc.isOpen()) {
            sc.close();
        }
    }
}
