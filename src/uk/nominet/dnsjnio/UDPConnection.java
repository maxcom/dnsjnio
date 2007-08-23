/*
Copyright 2007 Nominet UK

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License.
 */

package uk.nominet.dnsjnio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

import org.xbill.DNS.Message;

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
