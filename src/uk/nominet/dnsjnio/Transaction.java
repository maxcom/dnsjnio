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

import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.ResolverListener;
import org.xbill.DNS.TSIG;

/**
 * This class models a DNS query transaction. It contains methods
 * to open a port, send the query, and handle the message and any
 * errors. It also closes the port once the transaction is complete.
 * It is used by the NonblockingResolver to send DNS queries.
 * It instantiates a Connection (either UDP or TCP), and uses that
 * to make the query.
 * It parses the reply and, if the response is truncated over UDP,
 * will trigger a TCP retry with truncation not set.
 */

public class Transaction extends AbstractTransaction {
    Connection connection;
    Message query;
    Object id;
    boolean responded = false;
    TSIG tsig;
    boolean tcp;
    boolean ignoreTruncation;
    protected InetSocketAddress remoteAddr;
    protected InetSocketAddress localAddr;
    private long endTime;
    private ResponseQueue responseQueue;
    private ResolverListener listener = null;
    protected int udpSize;
    private boolean answered = false;
    private final Object lock = new Object();

    /**
     * Transaction constructor
     * @param remoteAddr The resolver to query
     * @param tsig The TSIG for the query
     * @param tcp use TCP if true, UDP otherwise
     * @param ignoreTruncation true if truncated responses are ok
     */
    public Transaction(InetSocketAddress remoteAddr, InetSocketAddress localAddr, TSIG tsig, boolean tcp, boolean ignoreTruncation) {
        this.tcp = tcp;
        this.ignoreTruncation = ignoreTruncation;
        this.tsig = tsig;
        this.remoteAddr = remoteAddr;
        this.localAddr = localAddr;
    }

    /**
     * Send a query. This kicks off the whole process.
     * @param query
     * @param id
     * @param responseQueue
     * @param endTime
     */
    public void sendQuery(Message query, Object id, ResponseQueue responseQueue, long endTime) {
        this.responseQueue = responseQueue;
        this.id = id;
        this.query = query;
        this.endTime = endTime;
        startTimer();
        startConnect();
    }

    /**
     * Send a query using a ResolverListener. This kicks off the whole process.
     * @param query
     * @param id
     * @param listener
     * @param endTime
     */
    public void sendQuery(Message query, Object id, ResolverListener listener, long endTime) {
        this.listener = listener;
        this.id = id;
        this.query = query;
        this.endTime = endTime;
        startTimer();
        startConnect();
    }

    /**
     * ResponseQueue a callback at the timeout time
     */
    private void startTimer() {
        Timer.addTimeout(endTime, this);
    }

    /**
     * Instantiate a new Connection, and start the connect process.
     */
    protected void startConnect() {
        if (tcp) {
            connection = new TCPConnection(this);
        }
        else {
            connection = new UDPConnection(this, udpSize);
        }
        connection.connect(remoteAddr, localAddr);
    }

    /**
     * Disconnect.
     */
    protected boolean disconnect(QueryData ignoreMe) {
        return disconnect(connection);
    }

    /**
     * Called by the Connection to say that we are readyToSend.
     * We can now send the data.
     */
    public void readyToSend(Connection ignoreMe) {
        sendQuery(connection, query);
    }

    /**
     * A packet is available. Decode it and act accordingly.
     * If the packet is truncated over UDP, and ignoreTruncation
     * is false, then a tcp query is run to return the whole response.
     * @param data
     */
    public void dataAvailable(byte[] data, Connection ignoreMe) {
        // Now get the data, and send it back to the listener.
        try {
            disconnect(ignoreMe);
            Message message = NonblockingResolver.parseMessage(data);
            NonblockingResolver.verifyTSIG(query, message, data, tsig);
            // Now check that we got the whole message, if we're asked to do so
            if (!tcp && !ignoreTruncation &&
                    message.getHeader().getFlag(Flags.TC))
            {
                // Redo the query, but use tcp this time.
                tcp = true;
                // Now start again with a TCP connection
                startConnect();
                return;
            }
            if (query.getHeader().getID() != message.getHeader().getID()) {
//                System.out.println("Query wrong id! Expected " + query.getHeader().getID() + " but got " + message.getHeader().getID());
                return;
            }
            returnResponse(message);
        }
        catch (IOException e) {
            returnException(e, null);
        }
    }

    /**
     * Return the response to the listener
     * @param message the response
     */
    private void returnResponse(Message message) {
    	boolean needToRespond = false;
    	synchronized (lock) {
    	if (!answered) {
    		answered = true;
    		needToRespond = true;
    	}
    	}
    	if (needToRespond) {
            // Stop the timer!
            cancelTimer();
            returnResponse(listener, responseQueue, message, id);    		
    	}
    }

    /**
     * Throw an Exception to the listener
     */
    protected void returnException(Exception e, QueryData ignoreMe) {
    	boolean needToRespond = false;
    	synchronized (lock) {
    	if (!answered) {
    		answered = true;
    		needToRespond = true;
    	}
    	}
    	if (needToRespond) {
            // Stop the timer!
            cancelTimer();
            returnException(listener, responseQueue, e, id);
    	}
    }

    /**
     * Cancel the timeout callback.
     */
    private void cancelTimer() {
        Timer.cancelTimeout(this);
        responded = true;
    }

    public void setUdpSize(int udpSize) {
        this.udpSize = udpSize;
    }
}
