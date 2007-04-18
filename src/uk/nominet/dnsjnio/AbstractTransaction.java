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
import org.xbill.DNS.Header;
import org.xbill.DNS.NonblockingResolver;
import org.xbill.DNS.ResolverListener;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Abstract superclass for the Transaction and SinglePortTransaction classes
 */
public abstract class AbstractTransaction implements ConnectionListener, TimerListener {
    protected void disconnect(Connection connection) {
        if (connection != null) {
            connection.disconnect();
            connection.removeListener(this);
            connection = null;
        }
    }

    public int getPort() {
        return 0;
    }

    protected static void sendQuery(Connection connection, Message query) {
        if (connection != null) {
            byte [] out = query.toWire(Message.MAXLENGTH);
            connection.send(out);
        }
    }

    protected abstract void disconnect(QueryData qData);
    protected abstract void returnException(Exception e, QueryData qData);

    /**
     * Called by the Connection when the original query times out
     * When timer completes, end the connection and throw an IOException to the caller.
     */
    public void timedOut(QueryData qData) {
        disconnect(qData);
        returnException(new SocketTimeoutException("Timed out"), qData);
    }

    protected static void returnResponse(ResolverListener listener, ResponseQueue responseQueue, Message message, Object id) {
        Response response = new Response();
        if (listener == null) {
            response.setId(id);
            response.setMessage(message);
            responseQueue.insert(response);
        }
        else {
            // Send the result back to the listener
            // @todo Should probably have a pool of these threads.
            ResponderThread responder = new ResponderThread(listener, id, message);
            responder.start();
        }
    }

    protected void returnException(ResolverListener listener, ResponseQueue responseQueue, Exception e, Object id) {
    // Stop the timer!
    Response response = new Response();
        if (listener == null) {
            response.setException(e);
            response.setId(id);
            response.setException(true);
            responseQueue.insert(response);
        }
        else {
            // Send the exception back to the listener
            ResponderThread responder = new ResponderThread(listener, id, e);
            responder.start();
        }
    }


    public void closed(Connection connection) {}
}
