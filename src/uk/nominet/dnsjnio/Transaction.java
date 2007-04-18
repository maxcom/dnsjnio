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

import org.xbill.DNS.*;

import java.net.InetSocketAddress;
import java.io.IOException;

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
    protected InetSocketAddress addr;
    private long endTime;
    private ResponseQueue responseQueue;
    private ResolverListener listener = null;
    protected int udpSize;

    /**
     * Transaction constructor
     * @param addr The resolver to query
     * @param tsig The TSIG for the query
     * @param tcp use TCP if true, UDP otherwise
     * @param ignoreTruncation true if truncated responses are ok
     */
    public Transaction(InetSocketAddress addr, TSIG tsig, boolean tcp, boolean ignoreTruncation) {
        this.tcp = tcp;
        this.ignoreTruncation = ignoreTruncation;
        this.tsig = tsig;
        this.addr = addr;
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
        connection.connect(addr);
    }

    /**
     * Disconnect.
     */
    protected void disconnect(QueryData ignoreMe) {
        disconnect(connection);
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
                System.out.println("Query wrong id! Expected " + query.getHeader().getID() + " but got " + message.getHeader().getID());
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
        // Stop the timer!
        cancelTimer();
        returnResponse(listener, responseQueue, message, id);
    }

    /**
     * Throw an Exception to the listener
     */
    protected void returnException(Exception e, QueryData ignoreMe) {
        // Stop the timer!
        cancelTimer();
        returnException(listener, responseQueue, e, id);
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
