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

import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.NonblockingResolver;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import junit.framework.TestCase;
import java.net.*;

public class PortTest extends TestCase {
	final static String SERVER = "localhost";

	final static int PORT = TestServer.PORT;

	final static int LOCAL_PORT = 5678;

	// final static int PORT = 53;
	final static int TIMEOUT = 10;

	int idCount = 0;

	ResponseQueue queue = new ResponseQueue();

	TestServer server = TestServer.startServer();

	boolean singlePort; // do a single port test?

	boolean useTcp;

	public void setUp() {
		reset();
	}

	public void finalize() {
		server.stopRunning();
	}

	private void reset() {
		idCount = 0;
		Timer.reset();
	}

	public void testSamePort() throws Exception {
		singlePort = true;
		runTheTest();
	}

	public void testDifferentPort() throws Exception {
		singlePort = false;
		runTheTest();
	}

	private void runTheTest() throws Exception {
		// Try it on udp
		useTcp = false;
		doTestManyAsynchronousClients();
		// Try it on tcp
		useTcp = true;
		doTestManyAsynchronousClients();
	}

	private void doTestManyAsynchronousClients() throws Exception {
		// test many NonblockingResolvers using asynchronous sends
		int numClients = 100;
		int bad = 0;
		NonblockingResolver resolver = new NonblockingResolver(SERVER);
		// if (singlePort) {
		resolver.setLocalAddress(new InetSocketAddress(LOCAL_PORT));
		// }
		resolver.setRemotePort(PORT);
		resolver.setTimeout(TIMEOUT);
		resolver.setSinglePort(singlePort);
		for (int i = 0; i < numClients; i++) {
			Object id = new Integer(idCount++);
			Message query = getQuery("example" + ((Integer) (id)).intValue()
					+ ".net");
			resolver.sendAsync(query, id, queue);
		}

		int[] ports = new int[numClients];
		for (int i = 0; i < numClients; i++) {
			Response response = queue.getItem();
			if (!response.isException()) {
				// Check response to see which port query went in on
				int port = getPortFromResponse(response.getMessage());
				ports[i] = port;
//				System.out.println("Result " + response.getId()
//						+ " received OK on port " + port);
			} else {
//				System.out.println("Result " + response.getId()
//						+ " threw Exception " + response.getException());
			}
			assertTrue(!response.isException()
					|| response.getException() != null);
			if (response.isException()) {
				bad++;
			}
		}
		if (singlePort) {
			// Check same port has been used
			int port0 = ports[0];
			for (int i = 0; i < (numClients - bad); i++) {
				assertTrue("Single port system used multiple ports!",
						ports[i] == port0);
				assertTrue("Correct port used (" + LOCAL_PORT + ")",
						ports[i] == LOCAL_PORT);
			}
		} else {
			// @todo@ Check different port has been used
			for (int i = 0; i < (numClients - bad); i++) {
				int port = ports[i];
				// Now go through remainder of array and check no port the same
				for (int j = i + 1; j < (numClients - bad); j++) {
					assertTrue("Multi port system used same port (" + port
							+ ")!", port != ports[j]);
				}
			}
		}
		assertTrue("Too many exceptions! (" + bad + " of " + numClients + ")",
				bad < (numClients * 0.05));
	}

	public static int getPortFromResponse(Message m) {
		for (int i = 0; i < 4; i++) {
			try { // Can do something with the counts field here, instead of
				// cycling through all of these
				Record[] records = m.getSectionArray(i);
				if (records != null) {
					for (int j = 0; j < records.length; j++) {
						if ((records[j]).getClass().equals(TXTRecord.class)) {
							return Integer.valueOf(
									((String) (((TXTRecord) (records[j]))
											.getStrings().get(0)))).intValue();
						}
					}
				}
			} catch (IndexOutOfBoundsException e) {
				// carry on!
			}
		}
		return -999;
	}

	private Message getQuery(String nameString) throws TextParseException {
		Name name = Name.fromString(nameString, Name.root);
		Record question = Record.newRecord(name, Type.A, DClass.ANY);
		return Message.newQuery(question);
	}
}
