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

import java.net.SocketTimeoutException;

import org.xbill.DNS.Message;
import org.xbill.DNS.ResolverListener;

/**
 * Abstract superclass for the Transaction and SinglePortTransaction classes
 */
public abstract class AbstractTransaction implements ConnectionListener, TimerListener {
    protected boolean disconnect(Connection connection) {
        if (connection != null) {
        	// If disconnect returns false, then the connection has already been closed,
        	// and the user will be sent the data.
        	// Otherwise, we close.
        	connection.removeListener(this);
            if (connection.disconnect()) {
            	connection = null;
            	return true;
            }
        }
        return false;
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

    protected abstract boolean disconnect(QueryData qData);
    protected abstract void returnException(Exception e, QueryData qData);

    /**
     * Called by the Connection when the original query times out
     * When timer completes, end the connection and throw an IOException to the caller.
     */
    public void timedOut(QueryData qData) {
    	// only return an exception if we actually closed the connection
        if (disconnect(qData)) {
            returnException(new SocketTimeoutException("Timed out"), qData);
        }
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
            // @todo@ Should probably have a pool of these threads.
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
            // @todo@ Should probably have a pool of these threads.
            ResponderThread responder = new ResponderThread(listener, id, e);
            responder.start();
        }
    }


    public void closed(Connection connection) {}
}
