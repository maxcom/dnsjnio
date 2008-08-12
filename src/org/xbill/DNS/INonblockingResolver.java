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

package org.xbill.DNS;

import uk.nominet.dnsjnio.ResponseQueue;

/**
 * Extended Resolver interface for NonblockingResolver.
 * Able to set TCP and timeout on a per-query basis
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
    
//    /**
//     * Set single port mode on or off
//     * @param useSamePort should same port be used for all the queries?
//     */
// THIS METHOD HAS BEEN REMOVED : SEE http://www.us-cert.gov/cas/techalerts/TA08-190B.html
//    public void setSinglePort(boolean useSamePort);
}
