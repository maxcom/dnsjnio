package org.xbill.DNS;

import uk.nominet.dnsjnio.ResponseQueue;

/**
 * Extended Resolver interface for NonblockingResolver.
 * Able to set TCP and timeout on a per-query basis

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
public interface INonblockingResolver extends Resolver {
    /**
     * An old-style call, allowing the user to specify the query ID.
     */
    public void sendAsync(Message message, Object id, ResolverListener resolverListener);
    /**
     * Asynchronously sends a message to a single nameserver, registering a
     *  ResponseQueue to buffer responses on success or exception.  Multiple 
     *  asynchronous lookups can be performed in parallel.
     * @param query The query to send
     * @param responseQueue the queue for the responses
     * @return An identifier, which is also a data member of the Response
     */
    public Object
            sendAsync(final Message query, final ResponseQueue responseQueue);
    /**
     * Asynchronously sends a message to a single nameserver, registering a
     *  ResponseQueue to buffer responses on success or exception.  Multiple 
     *  asynchronous lookups can be performed in parallel.
     * @param query The query to send
     * @param id An identifier, which is also a data member of the Response
     * @param responseQueue the queue for the responses
     */
    public void
            sendAsync(final Message query, Object id, final ResponseQueue responseQueue);
    public void
            sendAsync(final Message query, Object id, int timeout, boolean useTCP, final ResponseQueue responseQueue);
    /**
     * Set single port mode on or off
     * @param useSamePort should same port be used for all the queries?
     */
    public void setSinglePort(boolean useSamePort);
}
