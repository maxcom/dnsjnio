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

import junit.framework.TestCase;

/**
 * Exercise the ExtendedNonblockingResolver a little
 */
public class ExtendedResolverTest extends TestCase {
    final static String SERVER = "localhost";
    final static int PORT = TestServer.PORT;
//    final static int PORT = 53;
    final static int TIMEOUT = 10;
    final static int NUM_SERVERS = 10;
    
    private void startServers(int numServers) {
    	for (int i = 0; i < numServers; i++) {
//            TestServer.startServer(PORT + i, 10, 1);    		
    	}
    }

    public void testExtendedResolver() {
    	// Start up a load of resolvers on localhost (running on different ports)
    	startServers(NUM_SERVERS);
    	// Start up an ExtendedNonblockingResolver with all of these as instances.
    	// Run some tests on these servers where :
    	//    a) All servers return response (with random time delays)
    	//    b) All servers time out or throw other exception
    	//    c) Some servers return response, others throw exceptions
        // @todo!!
    }
}
