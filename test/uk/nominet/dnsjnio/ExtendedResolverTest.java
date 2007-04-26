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
import org.xbill.DNS.*;

import java.net.*;

/**
 * Exercise the ExtendedNonblockingResolver a little
 */
public class ExtendedResolverTest extends TestCase {
	final static String SERVER = "localhost";

	final static int PORT = TestServer.PORT;

	final static int TIMEOUT = 10;

	final static int NUM_SERVERS = 10;

	TestServer[] servers;

	NonblockingResolver[] resolvers;

	ExtendedNonblockingResolver eres;

	static int headerIdCount = 0;

	private void startServers(int numServers) throws Exception {
		// Start up a load of resolvers on localhost (running on different
		// ports)
		stopServers();
		servers = new TestServer[numServers];
		resolvers = new NonblockingResolver[NUM_SERVERS];
		for (int i = 0; i < numServers; i++) {
			servers[i] = TestServer.startServer(PORT + 1 + i, 10, 1);
			NonblockingResolver res = new NonblockingResolver(SERVER);
			res.setPort(PORT + 1 + i);
			resolvers[i] = res;
		}
		// Start up an ExtendedNonblockingResolver with all of these as
		// instances.
		for (int i = 0; i < NUM_SERVERS; i++) {
		}
		eres = new ExtendedNonblockingResolver(resolvers);
	}

	private void stopServers() {
		if (servers != null) {
			for (int i = 0; i < servers.length; i++) {
				servers[i].stopRunning();
			}
		}
		TestServer.stopServer();
	}

	public void testExtendedNonblockingResolver() throws Exception {
		startServers(NUM_SERVERS);

		// Run some tests on these servers where :
		// a) All servers return response (with random time delays)
		runAllGoodTest();
		// b) All servers time out or throw other exception
		runAllBadTest();
		// c) Some servers return response, others throw exceptions
		runSomeGoodTest();
		stopServers();
	}

	public void runAllGoodTest() throws Exception {
		// Set all servers to return response (with random time delays)
		// Then send the query and make sure it comes back OK.
		runMultipleQueries(true);
	}

	public void runAllBadTest() throws Exception {
		// b) All servers time out or throw other exception
		// Set all servers to fail (with random time delays)
		// Then send the query and make sure it throws an exception.
		runMultipleQueries(false);
	}

	public void runSomeGoodTest() throws Exception {
		// c) Some servers return response, others throw exceptions
		// Set some servers to fail and some to return (with random time delays)
		// Then send the query and make sure it returns.
		// @todo!! How can we implement this?
		runMultipleQueries(true);
	}

	private void runMultipleQueries(boolean expectedOk) throws Exception {
		// @todo@ Send a load of concurrent queries to the ENBR
		Name name = Name.fromString("example.net", Name.root);
		if (!expectedOk) {
			// Change the name to get the server to time out
			name = Name.fromString("timeout.example.net", Name.root);
		}
		Record question = Record.newRecord(name, Type.A, DClass.ANY);
		Message query = Message.newQuery(question);
		int numRequests = 100;
		int bad = 0;

		ResponseQueue queue = new ResponseQueue();
		for (int i = 0; i < numRequests; i++) {
			int headerId = headerIdCount;
			headerId = headerIdCount++;
			query.getHeader().setID(headerId);
			System.out.println("Sending Query");
			eres.sendAsync(query, queue);
		}
		for (int i = 0; i < numRequests; i++) {
			System.out.println("Waiting on next item");
			Response response = queue.getItem();
			if (!response.isException()) {
				System.out.println(i + ", Result " + response.getId()
						+ " received OK");
			} else {
				System.out.println(i + ", Result " + response.getId()
						+ "threw Exception " + response.getException());
			}
			assertTrue(!response.isException()
					|| response.getException() != null);
			if (response.isException()) {
				bad++;
			}
		}
		if (expectedOk) {
			assertTrue(bad == numRequests);
		} else
			assertTrue(bad == 0);
	}

	// public class ENBRTestVersion extends ExtendedNonblockingResolver {
	// public ENBRTestVersion() throws UnknownHostException {
	// super();
	// }
	// public
	// ENBRTestVersion(Resolver [] res) throws UnknownHostException {
	// super (res);
	// }
	//		
	// }
}
