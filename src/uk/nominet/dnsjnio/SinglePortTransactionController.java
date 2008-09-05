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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.NonblockingResolver;
import org.xbill.DNS.ResolverListener;

/**
 * This class provides communication over a single port.
 * Each Resolver will run on a different port, which has one TCP and one UDP connection to a server.
 * When a query comes in, we need to check status of current Connection
 * Either reuse it or reopen it.
 * If a query needs to sent with a header ID which is currently in use on this port, then a new standard Transaction object is used on a new port.
 * When a query ends (response or timeout) then the numQueries should be decremented and the Connection closed if numQueries == 0.
 */
public class SinglePortTransactionController extends AbstractTransaction {
    // Keep list of outstanding queries (connection, responseQueue, listener, id)
    // When a packet comes in, get the id, and check all outstanding queries for that id.
    private Map tcpQueryDataMap = new HashMap();
    private Map udpQueryDataMap = new HashMap();
    private TCPConnection tcpConnection;
    private UDPConnection udpConnection;
    protected InetSocketAddress remoteAddress;
    protected InetSocketAddress localAddress;

    public boolean headerIdNotInUse(int id) {
        // Search the queryDataList for this ID.
        synchronized(tcpQueryDataMap) {
            if (tcpQueryDataMap.keySet().contains(new Integer(id))) {
                return false;
            }
        }
        synchronized(udpQueryDataMap) {
            if (udpQueryDataMap.keySet().contains(new Integer(id))) {
                return false;
            }
        }
        return true;
    }

    static int udpOpenedCount = 0;
    static int udpOpeningCount = 0;
    /**
     * Instantiate a new Connection, and start the connect process.
     */
    protected void startConnect(QueryData qData) {
        startTimer(qData);
        if (qData.isTcp()) {
            synchronized(tcpQueryDataMap) {
                tcpQueryDataMap.put(new Integer(qData.getQuery().getHeader().getID()), qData);
            }
            if (tcpConnection != null) {
                // Deal with current state of tcpConnection
                // Is the connection open? If so, send the query now
                // If connection opening, then queue query now
                // If connection closing, then reopen, and send query. Do we need to worry about queues?
                // Add this query to the list for the connection
                qData.setConnection(tcpConnection);
                // Connection may not be ready just yet!
                // Connection could still be opening - if so, then wait until open. Simply stick query in queue, and be done
                if (tcpConnection.getState() == Connection.State.OPENING) {
                    return;
                }
                if (tcpConnection.getState() == Connection.State.OPENED) {
                    readyToSend(tcpConnection);
                    return;
                }
                else if (tcpConnection.getState() == Connection.State.CLOSING) {
                    // The query is in the queue. When the socket is closed, it will reconnect
                    return;
                }
                else if (tcpConnection.getState() == Connection.State.CLOSED) {
                    // Reopen the connection
                    getNewTcpConnection(qData);
                }
            }
            else {
                // Fire up a new tcpConnection
                getNewTcpConnection(qData);
            }
        }
        else {
            synchronized(udpQueryDataMap) {
                udpQueryDataMap.put(new Integer(qData.getQuery().getHeader().getID()), qData);
            }
            if (udpConnection != null && !(udpConnection.getState() == Connection.State.CLOSED)) {
                // Use this connection
                // Add this query to the list for the connection
                qData.setConnection(udpConnection);
                // Connection may not be ready just yet!
                // Connection could still be opening - if so, then wait until open. Simply stick query in queue, and be done
                if (udpConnection.getState() == Connection.State.OPENING) {
                    return;
                }
                if (udpConnection.getState() == Connection.State.OPENED) {
                    readyToSend(udpConnection);
                    return;
                }
                else if (udpConnection.getState() == Connection.State.CLOSING) {
                    // The query is in the queue. When the socket is closed, it will reconnect
                    return;
                }
            }
            else {
                getNewUdpConnection(qData);
            }
        }
        qData.getConnection().connect(remoteAddress, localAddress);
    }

    private void getNewTcpConnection(QueryData qData) {
        tcpConnection = new TCPConnection(this, Connection.SINGLE_PORT_BUFFER_SIZE);
        qData.setConnection(tcpConnection);
    }

    private void getNewUdpConnection(QueryData qData) {
        udpConnection = new SinglePortUDPConnection(this, localAddress.getPort());
        qData.setConnection(udpConnection);
    }

    public void setRemoteAddress(InetSocketAddress addr) {
        this.remoteAddress = addr;
    }

    public void setLocalAddress(InetSocketAddress addr) {
        this.localAddress = addr;
    }

    public SinglePortTransactionController(InetSocketAddress remoteAddr, InetSocketAddress localAddr) {
        this.remoteAddress = remoteAddr;
        this.localAddress = localAddr;
        synchronized (tcpQueryDataMap) {
            tcpQueryDataMap = new HashMap();
        }
        synchronized (udpQueryDataMap) {
            udpQueryDataMap = new HashMap();
        }
    }

    /**
     * Send a query. This kicks off the whole process.
     * @param qData
     * @param id
     * @param responseQueue
     * @param endTime
     */
    public void sendQuery(QueryData qData, Object id, ResponseQueue responseQueue, long endTime) {
        qData.setResponseQueue(responseQueue);
        qData.setId(id);
        qData.setEndTime(endTime);
        startConnect(qData);
    }

    /**
     * Send a query using a ResolverListener. This kicks off the whole process.
     * @param qData
     * @param id
     * @param listener
     * @param endTime
     */
    public void sendQuery(QueryData qData, Object id, ResolverListener listener, long endTime) {
        qData.setListener(listener);
        qData.setId(id);
        qData.setEndTime(endTime);
        startConnect(qData);
    }

    /**
     * ResponseQueue a callback at the timeout time
     */
    private void startTimer(QueryData qData) {
        Timer.addTimeout(qData.getEndTime(), this, qData);
    }

    /**
     * Disconnect.
     */
    protected boolean disconnect(QueryData qData) {
        // We only want to disconnect if there are no outstanding queries on that connection
        // Remove this query from the list
        Map queryMap = getQueryDataMap(qData.getConnection());
        boolean disconnect = false;
        synchronized(queryMap) {
            queryMap.remove(new Integer(qData.getQuery().getHeader().getID()));
            if (queryMap.size() == 0) {
                disconnect = true;
            }
        }
        if (disconnect) {
            disconnect(qData.getConnection());
        }
        return true;
    }

    /**
     * Called to say that we are readyToSend.
     * We can now send the data.
     */
    public void readyToSend(Connection connection) {
        QueryData qData = null;
        do {
            qData = getNextQueryData(connection);
            if (qData != null) {
                qData.setSent(true);
                sendQuery(connection, qData.getQuery());
            }
        }
        while (qData != null);
    }

    private QueryData getNextQueryData(Connection c) {
        Map queryMap = getQueryDataMap(c);
        synchronized (queryMap) {
            // @todo Can we optimise this?
            for (Iterator it = queryMap.values().iterator(); it.hasNext();) {
                QueryData qData = (QueryData)(it.next());
                if (!(qData.isSent()) && (qData.getConnection() == c)) {
                    return qData;
                }
            }
        }
        return null;
    }

    private Map getQueryDataMap(Connection c) {
        Map queryMap = udpQueryDataMap;
        if ((tcpConnection != null) && (c.equals(tcpConnection))) {
            queryMap = tcpQueryDataMap;
        }
        return queryMap;
    }

    /**
     * The Connection has been closed
     * @param connection
     */
    public void closed(Connection connection) {
        // See if any queries are still outstanding for that Connection.
        Map queryMap = getQueryDataMap(connection);
        boolean reconnect = false;
        synchronized (queryMap) {
            if (queryMap.size()!=0) {
                reconnect = true;
            }
        }
        if (reconnect) {
            // reconnect - the queue will be sent then.
            connection.connect(remoteAddress, localAddress);
        }
    }

    /**
     * A packet is available. Decode it and act accordingly.
     * If the packet is truncated over UDP, and ignoreTruncation
     * is false, then a tcp query is run to return the whole response.
     * @param data
     */
    public void dataAvailable(byte[] data, Connection connection) {
        // Now get the data, and send it back to the listener.
        // Match up the returned qData with the QueryDataList
        try {
            Message message = NonblockingResolver.parseMessage(data);

            QueryData qData = null;

            // Search the list for this connection
            Map queryMap = getQueryDataMap(connection);
            synchronized(queryMap) {
                qData = (QueryData)(queryMap.get(new Integer(message.getHeader().getID())));
            }
            if (qData == null) {
                    return; // @todo !!!
            }
            disconnect(qData);

            NonblockingResolver.verifyTSIG(qData.getQuery(), message, data, qData.getTsig());
            // Now check that we got the whole message, if we're asked to do so
            if (!qData.isTcp() && !qData.isIgnoreTruncation() &&
                    message.getHeader().getFlag(Flags.TC))
            {
                cancelTimer(qData);
                // Redo the query, but use tcp this time.
                qData.setTcp(true);
                // Now start again with a TCP connection
                startConnect(qData);
                return;
            }
//            System.out.println("Returning id = " + qData.getId() + ", header id " + qData.getQuery().getHeader().getID());
            returnResponse(message, qData);
        }
        catch (IOException e) {
            return; // Ignore it. Query will time out eventually.
        }
    }

    /**
     * Return the response to the listener
     * @param message the response
     */
    private void returnResponse(Message message, QueryData qData) {
    	if (!qData.isAnswered()) {
    		qData.setAnswered(true);
            // Stop the timer!
            cancelTimer(qData);
            returnResponse(qData.getListener(), qData.getResponseQueue(), message, qData.getId());
    	}
    }

    /**
     * Throw an Exception to the listener
     */
    protected void returnException(Exception e, QueryData qData) {
    	if (!qData.isAnswered()) {
    		qData.setAnswered(true);
            // Stop the timer!
            cancelTimer(qData);
//          System.out.println("Exception for " +qData.getQuery().getHeader().getID());
            returnException(qData.getListener(), qData.getResponseQueue(), e, qData.getId());
    	}
    }

    /**
     * Cancel the timeout callback
     * Also removes the QueryData from the list
     */
    private void cancelTimer(QueryData qData) {
        Timer.cancelTimeout(this, qData);
        qData.setResponded (true);
        Map queryMap = getQueryDataMap(qData.getConnection());
        synchronized(queryMap) {
            queryMap.remove(new Integer(qData.getQuery().getHeader().getID()));
        }
    }

}
