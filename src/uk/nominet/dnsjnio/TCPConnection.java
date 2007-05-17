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

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * This class implements TCP-specific methods for
 * the Connection superclass. 
 */
public class TCPConnection extends Connection {
    boolean packetInProgress = false;
    public TCPConnection(ConnectionListener listener) {
        super(listener);
    }

    public TCPConnection(ConnectionListener listener, int buffSize) {
        super (listener, buffSize);
    }

    protected void close() {
        if(getState() != State.CLOSED) {
            SocketChannel sc = (SocketChannel)sk.channel();
            if(sc.isOpen()) {
                if(getState() == State.OPENED) {
                    sk.interestOps(0);
                    setState(State.CLOSING);
                    Socket sock = sc.socket();
                    try {
                        sock.shutdownOutput();
                    } catch(IOException se) {
                        se.printStackTrace();
                        // log error
                    }
                }
                closeComplete();
            }
        }
    }

    protected void connect() {
        try {
            SocketChannel sch = SocketChannel.open();
            sch.configureBlocking(false);
        	sch.socket().bind(localAddress);
            sk = sch.register(DnsController.getSelector(),0);
            sch.connect(remoteAddress);
            attach(sk);
        } catch(Exception e) {
            e.printStackTrace();
            close();
        }
    }

    /**
     * process a connect complete selection
     */
    public void doConnect()	{
        SocketChannel sc = (SocketChannel)sk.channel();
        try {
            sc.finishConnect();
            sk.interestOps(SelectionKey.OP_WRITE);
            setState(State.OPENED);
        } catch(IOException e) {
            e.printStackTrace();
            closeComplete();
        }
    }

    public void doRead() {
        SocketChannel sc = (SocketChannel)sk.channel();
        // Read the next set of bytes
        readFromChannel(sc);
        // while we have more than 2 bytes :
        while (recvCount > 2) {
            //      Work out the length of the next packet to expect.
            if (recvBytes == null) {
                break;
            }
            int lengthNextPacket = ((recvBytes[0] & 0xFF) << 8) + (recvBytes[1] & 0xFF);
            //      If we have that many bytes, then split the packet off, and clear the front of the recvBuffer
            if (recvCount >= (lengthNextPacket+2)) {
                // Split off the packet, send it to the user, and clear the front of the buffer
                byte[] packet = new byte[lengthNextPacket];
                System.arraycopy(recvBytes, 2, packet, 0, lengthNextPacket);
                sendToUser(packet);
                // Now clear the buffer
                clearRecvBytes(lengthNextPacket + 2);
            }
            else {
                break;
            }
        }
    }

    protected void write(ByteBuffer data) {
        SocketChannel sc = (SocketChannel)sk.channel();
        if(sc.isOpen()) {
            if(data.hasRemaining()) {
                try {
                    sc.write(data);
                } catch(IOException e) {
                    e.printStackTrace();
                    closeComplete();
                }
            }
            commonEndWrite(data);
        }
    }

    // Add on the length bytes
    protected byte[] decorateData(byte[] bytes) {
        byte[] ret = new byte[bytes.length + 2];
        System.arraycopy(bytes, 0, ret, 2, bytes.length);
        ret[0] = (byte)(bytes.length >>> 8);
        ret[1] = (byte)(bytes.length & 0xFF);
        return ret;
    }

    protected void closeChannel() throws IOException {
        SocketChannel sc = (SocketChannel)sk.channel();
        if(sc != null && sc.isOpen()) {
            sc.close();
        }
    }

    /**
     * attach key and channel and set connection interest in selection key
     */
    public void attach(SelectionKey sk) {
        this.sk = sk;
        sk.attach(this);
        SocketChannel sch = (SocketChannel)sk.channel();
        if(sch.isConnected()) {
            sk.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            setState(State.OPENED);
        } else if(sch.isConnectionPending()) {
            sk.interestOps(SelectionKey.OP_CONNECT);
            setState(State.OPENING);
        }
    }
}
