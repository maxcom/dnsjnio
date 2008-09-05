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

    protected boolean close() {
    	boolean didClose = false;
        if(getState() != State.CLOSED) {
        	// Fix for 20080801 bug reported by :
        	// Allan O'Driscoll for sporadic NullPointerException - thanks, Allan!
        	if (sk != null) {
        		SocketChannel sc = (SocketChannel)sk.channel();
        		if(sc != null && sc.isOpen()) {
        			didClose = true;
        			if(getState() == State.OPENED) {
        				sk.interestOps(0);
        				setState(State.CLOSING);
        				Socket sock = sc.socket();
        				try {
        					sock.shutdownOutput();
        				} catch(IOException se) {
        					se.printStackTrace();
                        // 	log error
        				}
        			}
        			closeComplete();
        		}
        	}
        }
        return didClose;
//        return true;
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
