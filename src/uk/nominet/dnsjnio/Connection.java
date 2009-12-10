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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.LinkedList;

import uk.nominet.dnsjnio.NonblockingResolver;

/**
 * The superclass for the TCP and UDP connections.
 * This class models a socket, and is called by the client, and the
 * DnsController nio control loop.
 */
public abstract class Connection {
    protected final static int SINGLE_PORT_BUFFER_SIZE = 64 * 1024;
    protected final static int BUFFER_SIZE = 4 * 1024;
    protected byte[] recvBytes;
    protected ConnectionListener listener;

    protected SelectionKey sk;
    protected LinkedList sendQ = new LinkedList();

    protected ByteBuffer sendBuffer=null;
    protected int recvCount = 0;
    protected boolean writeReady = false;

    protected InetSocketAddress remoteAddress;
    protected InetSocketAddress localAddress;
    private int state = State.CLOSED;

    byte[] bytes;
    protected ByteBuffer inBuf;

    Connection(ConnectionListener listener, int bufferSize) {
        this.listener = listener;
        recvBytes = new byte[bufferSize];
        bytes = new byte[bufferSize];
        inBuf = ByteBuffer.wrap(bytes);
    }

    Connection(ConnectionListener listener) {
        this(listener, BUFFER_SIZE);
    }

    public void removeListener(ConnectionListener newListener) {
        if (listener == newListener) {
            listener = null;
        }
    }

    protected void fireDataAvailable(byte[] data) {
        if (listener != null) {
            listener.dataAvailable(data, this);
        }
    }

    private void fireStateChanged() {
        if (listener != null) {
            if (getState() == State.OPENED) {
                listener.readyToSend(this);
            }
            if (getState() == State.CLOSED) {
                listener.closed(this);
            }
        }
    }

    public String getRemoteName() {
        return remoteAddress.getHostName();
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public void connect(InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
        if(getState() == State.CLOSED) {
            setRemoteAddress(remoteAddress);
            setLocalAddress(localAddress);
            setState(State.OPENING);
            if (! DnsController.isSelectThread()) {
                DnsController.invoke(new Runnable() {
                    public void run() {
                        connect();
                    }
                });
            } else {
                connect();
            }
        }
    }

    public boolean disconnect() {
        if (! DnsController.isSelectThread()) {
            DnsController.invoke(new Runnable() {
                public void run() {
                    close();
                }
            } );
        } else {
            return close();
        }
        return true;
    }

    /**
     * queue up some bytes to send and try to send it out
     */
    public void send(final byte[] out) {
        if (! DnsController.isSelectThread()) {
            DnsController.invoke(new Runnable() {
                public void run() {
                    send(out);
                }
            });
        } else {
            sendQ.add(out);
            writeQueued();
        }
    }

    /**
     * process a write ready selection
     */
    public void doWrite() {
        // Deselect OP_WRITE, but don't enable OP_READ until the end of the write
    	if (sk.isValid()) {
        sk.interestOps(0);
//        sk.interestOps(SelectionKey.OP_READ);
        writeReady = true;				// write is ready
        if(sendBuffer != null)write(sendBuffer);	// may have a partial write
        writeQueued();					// write out rest of queue
    	}
    	else closeComplete();
    }

    /**
     * attempt to send all queued data
     */
    protected void writeQueued() {
        while(writeReady && sendQ.size() > 0)	// now process the queue
        {
            byte[] msg = (byte[])sendQ.remove(0);
            write(msg);	// write the bytes
        }
    }


    /**
     * send some bytes
     */
    private void write(byte[] out) {
        byte[] data = decorateData(out);
        ByteBuffer buf = ByteBuffer.wrap(data);
        write(buf);
    }

    // Default to do nothing
    protected byte[] decorateData(byte[] in) {
        return in;
    }

    protected void commonEndWrite(ByteBuffer data) {
        if(data.hasRemaining()) {
            writeReady = false;
            sk.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);

            sendBuffer = data;		// save the partial buffer
            writeReady = false;
        } else {
            sk.interestOps(SelectionKey.OP_READ);
            sendBuffer = null;
            writeReady = true;
        }
    }
    
    protected void setLocalAddress(InetSocketAddress newLocalAddr) {
    	localAddress = newLocalAddr;
    }

    protected void setRemoteAddress(InetSocketAddress newRemoteAddr) {
        remoteAddress = newRemoteAddr;
    }

    protected void setRemoteAddress(String address) {
        int n = address.indexOf(':');
        String pt = null;
        String host;
        int port= NonblockingResolver.DEFAULT_PORT;
        if(n == 0) {
            host = "127.0.0.1";
            pt = address.substring(1);
        }
        else if(n < 0) {
            host = address;
        } else {
            host = address.substring(0,n);
            pt = address.substring(n+1);
        }
        if (pt != null) {
            try {
                port = Integer.parseInt(pt);
            } catch (NumberFormatException e) {
                port = -1;
            }
        }
        setRemoteAddress(new InetSocketAddress(host, port));
    }

    protected void closeComplete() {
        sk.cancel();
        sk.attach(null);
        try {
            closeChannel();
            if (sk.isValid()) {
                sk.interestOps(0);
                sk.selector().wakeup();
            }
        } catch(Exception ce) {
            ce.printStackTrace();
        }
        setState(State.CLOSED);
        inBuf = null;
        recvBytes = null;
        recvCount = 0;
    }

    protected void clearRecvBytes(int j) {
        if (recvBytes != null) {
            if (j == recvCount) {
                recvCount = 0;
            }
            else {
                byte[] temp = new byte[recvCount - j];
                System.arraycopy(recvBytes, j, temp, 0, recvCount - j);
                recvCount -= j; // Drop front of buffer
                System.arraycopy(temp, 0, recvBytes, 0, recvCount);
                temp=null;
            }
        }
    }

    protected void readFromChannel(ByteChannel sc) {
        int len = 0;
        ByteBuffer inputBuffer = inBuf; // save a local reference just in case it gets nulled out.

        // TODO: probably need some type of synchronization on the input
        // buffer because it gets nulled out in closeComplete due to
        // timeout and then can potentially cause a crash here.
        if(sc.isOpen() && state != State.CLOSED && inputBuffer != null)
        {
            try {
                len = sc.read(inputBuffer);
            } catch(IOException e) {
                len=-1;
            }
            
            if(len >= 0)
            {
                addToBuffer(bytes, len);
                inBuf.clear();
            }
            else if(len < 0) {
                closeComplete();
            }
        }
    }

    /**
     * This method simply buffers the input.
     * The send to user will be triggered when the end of input is reached.
     */
    protected void addToBuffer(byte[] buf, int len) {
    	byte[] receivedBytes = recvBytes; // save a local reference just in case it gets nulled out.

    	// TODO: probably need some type of synchronization on the received
    	// bytes buffer. It gets nulled out in closeComplete due to timeout
    	// and then can potentially cause a crash here.
        if(buf != null && state != State.CLOSED && receivedBytes != null)
        {
            if (recvCount + len > receivedBytes.length) {
                // Grow the buffer, we already kept a reference to the old
            	// buffer so we don't need to make a temporary copy of it.
                recvBytes = null;
                recvBytes = new byte[recvCount + len];
                System.arraycopy(receivedBytes, 0, recvBytes, 0, recvCount);
                receivedBytes = recvBytes; // reset the reference
            }
            System.arraycopy(buf, 0, receivedBytes, recvCount, len);
            recvCount += len;
        }
    }

    protected void sendToUser(byte[] packet) {
        if (listener != null) {
            if (NonblockingResolver.isDataComplete(packet)) {
                fireDataAvailable(packet);	// to user
            }
        }
    }

    protected abstract boolean close();
    protected abstract void connect();
    protected abstract void write(ByteBuffer buf);
    protected abstract void closeChannel() throws IOException;
    public abstract void doRead();
    public abstract void doConnect();

    protected void setState(int newState) {
        if (state != newState) {
            state = newState;
            fireStateChanged();
        }
    }

    public int getState() {
        return state;
    }

    public class State {
        public final static int CLOSED = 0;
        public final static int OPENING = 1;
        public final static int OPENED = 2;
        public final static int CLOSING = 3;
    }
}
