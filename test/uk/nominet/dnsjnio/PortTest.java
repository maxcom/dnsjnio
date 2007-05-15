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

public class PortTest extends TestCase {
	final static String SERVER = "localhost";

	final static int PORT = TestServer.PORT;

	// final static int PORT = 53;
	final static int TIMEOUT = 10;

	int idCount = 0;
	ResponseQueue queue = new ResponseQueue();

	TestServer server = TestServer.startServer();

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
		doTestManyAsynchronousClients(true);
	}
	
	public void testDifferentPort() throws Exception {
		doTestManyAsynchronousClients(false);
	}
	
	boolean singlePort;

	private void doTestManyAsynchronousClients(boolean samePort) throws Exception {
		// test many NonblockingResolvers using asynchronous sends
		int numClients = 100;
		int bad = 0;
		singlePort = samePort;
		for (int i = 0; i < numClients; i++) {
			Thread task = new Thread() {
				public void run() {
					try {
						NonblockingResolver resolver = new NonblockingResolver(
								SERVER);
						resolver.setPort(PORT);
						resolver.setTimeout(TIMEOUT);
						resolver.setSinglePort(singlePort);
						Object id = new Integer(idCount++);
						Message query = getQuery("example"
								+ ((Integer) (id)).intValue() + ".net");
						resolver.sendAsync(query, id, queue);
					} catch (Exception e) {
					}
				}
			};
			task.start();
		}

		int[] ports = new int[numClients]; 
		for (int i = 0; i < numClients; i++) {
			Response response = queue.getItem();
			if (!response.isException()) {
				// System.out.println("Result " + response.getId() + " received
				// OK");
				// Check response to see which port query went in on
				int port = getPortFromResponse(response.getMessage());
				ports[i] = port;
			} else {
				// System.out.println("Result " + response.getId() + " threw
				// Exception " + response.getException());
			}
			assertTrue(!response.isException()
					|| response.getException() != null);
			if (response.isException()) {
				bad++;
			}
		}
		if (samePort) {
			// Check same port has been used
			int port0 = ports[0];
			for (int i = 0; i < (numClients - bad); i++) {
				assertTrue("Single port system used multiple ports!", ports[i] == port0);
			}
		}
		else {
			// @todo@ Check different port has been used
			for (int i = 0; i < (numClients - bad); i++) {
				int port = ports[i];
				// Now go through remainder of array and check no port the same
				for (int j = i; j < (numClients - bad); j++) {
					assertTrue("Multi port system used same port (" + port + ")!", port != ports[j]);
				}
			}
		}
		assertTrue("Too many exceptions! (" + bad + " of " + numClients + ")",
				bad < (numClients * 0.05));
	}
	
	private int getPortFromResponse(Message m) {
        for (int i = 0; i < 4; i++) {
            try { // Can do something with the counts field here, instead of cycling through all of these
                Record[] records = m.getSectionArray(i);
                if (records != null) {
                    for (int j = 0; j < records.length; j++) {
                        if ((records[j]).getClass().equals(TXTRecord.class)) {
                            return Integer.valueOf(((String)(((TXTRecord)(records[j])).getStrings().get(0)))).intValue();
                        }
                    }
                }
            }
            catch (IndexOutOfBoundsException e) {
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
