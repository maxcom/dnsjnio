package uk.nominet.dnsjnio;

import junit.framework.TestCase;
import org.xbill.DNS.*;

public class RaceTest extends TestCase {
	final static String SERVER = "localhost";
//	final static String SERVER  = RemoteServerTest.REAL_SERVER;
	final static int PORT = TestServer.PORT;
//	final static int PORT = 53;

	public void testForRaceCondition() throws Exception {
		TestServer.startServer();
		Thread thread = startThreads();
		thread.join();
	}

	private Thread startThreads() {
		Thread thread = null;
		for (int i = 0; i < 10; i++) {
			thread = new Racethread(false); // ((i == 0) ? true : false));
			thread.start();
		}
		return thread;
	}

	public class Racethread extends Thread {
		INonblockingResolver res = null;
		boolean doPrint = false;

		Racethread(boolean doPrint) {
			this.doPrint = doPrint;
		}

		public void run() {
			try {
				res = new NonblockingResolver(SERVER);
			} catch (Exception e) {
			}
			res.setPort(PORT);
			boolean foundRace = false;
			int timeout = 500;
			ResponseQueue q = new ResponseQueue();
//			String name = "example.net";
	        String name = RemoteServerTest.REAL_QUERY_NAME;
			Message query = null;
			try {
				query = getQuery(name);
			} catch (Exception e) {
			}
			boolean lastWasTimeout = false;
			Object lastId = new Integer(-1);
//			while (!foundRace) {
				while (!foundRace && timeout < 2500) {
					res.setTimeout((timeout + 500) / 1000, timeout % 1000);
					timeout = timeout + 25;
					Object id = res.sendAsync(query, q);
//					System.out.println("Sent query id : " + id + " for timeout of : " + new Integer(timeout-25));
					java.util.Timer timer = new java.util.Timer();
					timer.schedule(new RaceTimerTask(id), timeout + 10000);
					Object testId = new Integer(-2);
					Response response = null;
					while (testId != id) {
						response = q.getItem();
						testId = response.getId();
						if (id != response.getId()) {
							System.out.println("ERROR : " + id
									+ " expected, was : " + response.getId()
									+ ". Last ID was " + lastId + ", type " + (response.isException() ? " timeout" : " good response") + 
									", last was " + (lastWasTimeout  ? "timeout" : "good response"));
							System.out.println("[Transaction IDs : expected - " + query.getHeader().getID() +
									   ", received - " + response.getMessage().getHeader().getID());
						}
					}
//					System.out.println("Got id : " + id);
					timer.cancel();
//					while (!(q.isEmpty())) {
//						System.out
//								.println("\nERROR  - Queue not empty! Removing "
//										+ q.getItem().getId());
//					}
					// assertEquals(id, response.getId());
					// assertFalse("\n\nLAST ID RECEIVED TWICE!!!\n\n",
					// lastId == response.getId());
					lastId = id;
					lastWasTimeout = response.isException();
					if (doPrint) {
						System.out.print("Timeout : " + timeout + " was "
								+ (response.isException() ? "" : "not ")
								+ "an exception\n");
					}
					System.gc();
				}
				timeout = 40;
//			}
		}

		private Message getQuery(String nameString) throws TextParseException {
			Name name = Name.fromString(nameString, Name.root);
			Record question = Record.newRecord(name, Type.A, DClass.ANY);
			return Message.newQuery(question);
		}

	}
	
	public class RaceTimerTask extends java.util.TimerTask {
		private Object id;
		public RaceTimerTask(Object inid) {
			id = inid;
		}
		public void run() {
			System.out.println("\n\nERROR ERROR ERROR - QUERY " + id + " NEVER ANSWERED!!!\n\n");
		}
	}
}
