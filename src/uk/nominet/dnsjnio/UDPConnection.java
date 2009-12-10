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
            
            // Pick up SocketException here, and keep rebinding to different random port
            boolean connectedOk = false;
            while (!connectedOk) {
              try {
        	    sch.socket().bind(localAddress);
        	    connectedOk = true;
              } catch (java.net.SocketException e) {
            	// Failure may be caused by picking a port number that was
            	// already in use. Pick another random port and try again.
            	// Note that the socket channel is now invalid, we need to
            	// close it and open a fresh one.
            	localAddress = uk.nominet.dnsjnio.NonblockingResolver.getNewInetSocketAddressWithRandomPort(localAddress.getAddress());
            	sch.close();
            	sch = DatagramChannel.open();
            	sch.configureBlocking(false);
              }
            }
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
    	
    	// Make sure that the IP we're receiving from is the IP we sent to!
    	// This is done by the DatagramChannel, which only receives datagrams
    	// from the peer it is connected with.
    	
        DatagramChannel sc = (DatagramChannel)sk.channel();
        try {
            readFromChannel(sc);
        }
        catch (NullPointerException e) {
            return;
        }

        // It's possible that we received more than one DNS packet.
        // Let's split them out, and send each to the client.
        while (recvCount > 0) {
            if (recvBytes != null) {
                byte[] packet = new byte[recvCount];
                System.arraycopy(recvBytes, 0, packet, 0, recvCount);
                try {
                	// Get the first packet in the buffer
                    Message m = new Message(packet);
                    if (m.numBytes() < recvCount) {
                        packet = new byte[m.numBytes()];
                        System.arraycopy(recvBytes, 0, packet, 0, m.numBytes());
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
    protected boolean close() {
    	boolean didClose = false;
        if((getState() != State.CLOSED) && (getState() != State.CLOSING)) {
        	// Fix for 20080801 bug reported by :
        	// Allan O'Driscoll for sporadic NullPointerException - thanks, Allan!
        	if (sk != null) {
                DatagramChannel sc = (DatagramChannel)sk.channel();
                if(sc != null && sc.isOpen()) {
                    didClose = true;
                    if(getState() == State.OPENED) {
                        sk.interestOps(0);
                        setState(State.CLOSING);
                        try {
                            sc.close();
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        //     log error
                        }
                    }
                    closeComplete();
                }
        	}
        }
        return didClose;
//        return true;
    }

    protected void closeChannel() throws IOException {
        DatagramChannel sc = (DatagramChannel)sk.channel();
        if(sc != null && sc.isOpen()) {
            sc.close();
        }
    }
}
