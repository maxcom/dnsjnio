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
import org.xbill.DNS.ResolverListener;
import org.xbill.DNS.TSIG;

public class QueryData {
    Connection connection;
    Message query;
    Object id;
    boolean responded = false;
    TSIG tsig;
    boolean tcp;
    boolean ignoreTruncation;
    private long endTime;
    private ResponseQueue responseQueue;
    private ResolverListener listener = null;
    protected int udpSize;
    private boolean sent = false;

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Message getQuery() {
        return query;
    }

    public void setQuery(Message query) {
        this.query = query;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public boolean isResponded() {
        return responded;
    }

    public void setResponded(boolean responded) {
        this.responded = responded;
    }

    public TSIG getTsig() {
        return tsig;
    }

    public void setTsig(TSIG tsig) {
        this.tsig = tsig;
    }

    public boolean isTcp() {
        return tcp;
    }

    public void setTcp(boolean tcp) {
        this.tcp = tcp;
    }

    public boolean isIgnoreTruncation() {
        return ignoreTruncation;
    }

    public void setIgnoreTruncation(boolean ignoreTruncation) {
        this.ignoreTruncation = ignoreTruncation;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public ResponseQueue getResponseQueue() {
        return responseQueue;
    }

    public void setResponseQueue(ResponseQueue responseQueue) {
        this.responseQueue = responseQueue;
    }

    public ResolverListener getListener() {
        return listener;
    }

    public void setListener(ResolverListener listener) {
        this.listener = listener;
    }

    public int getUdpSize() {
        return udpSize;
    }

    public void setUdpSize(int udpSize) {
        this.udpSize = udpSize;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }
}
