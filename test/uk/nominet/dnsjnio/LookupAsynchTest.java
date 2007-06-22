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

import java.net.UnknownHostException;

import junit.framework.TestCase;
import org.xbill.DNS.*;

/**
 * 
 * Test that we can lookup on a real server. But also test that things work fine
 * when it all times out (using test server)
 */
public class LookupAsynchTest extends TestCase {
	public final static String REAL_SERVER = "ns0.validation-test-servers.nominet.org.uk.";

	final static String TEST_SERVER = "localhost";

	final static int PORT = TestServer.PORT;

	final static int NUM_SERVERS = 3;

	final static int TIMEOUT = 2;

	public LookupAsynchTest(String arg0) {
		super(arg0);
	}

	public void testNormalLookup() throws Exception {
		// assumes you're online!
		Lookup lookup = new Lookup("example.com", Type.A);
		lookup.run();
		int result = lookup.getResult();
		if (result != Lookup.SUCCESSFUL)
			System.out.print(" " + lookup.getErrorString());
		else {
			Record[] answers = lookup.getAnswers();
			for (int i = 0; i < answers.length; i++)
				System.out.println(answers[i]);
		}
		assertTrue("Should have got a good response", lookup.getResult() == Lookup.SUCCESSFUL);
	}

	boolean ran = false;

	public void testLookupAsynch() throws Exception {
		// assumes you're online!
		LookupAsynch.refreshDefault();
		LookupAsynch la = new LookupAsynch("example.com", Type.A);
		ran = false;
		Runnable completionTask = new Runnable() {
			public void run() {
				ran = true;
			}
		};
		long timeBefore = System.currentTimeMillis();
		la.runAsynch(completionTask);
		long timeoutVal = 1000 * 2;
		while (!ran && System.currentTimeMillis() < (timeBefore + timeoutVal))
			Thread.sleep(100);
		assertTrue(ran);
		assertTrue("Should have got a good response", la.getResult() == LookupAsynch.SUCCESSFUL);
	}

	public void testLookupAsynchNX() throws Exception {
		// assumes you're online!
		// Start a query which will never end
		// wait for the required timeout period
		LookupAsynch.refreshDefault();
		LookupAsynch la = new LookupAsynch(
				"thisnamedoesnotexist495jlhwo4rutyedjkhfq3.com", Type.A);
		ran = false;
		Runnable completionTask = new Runnable() {
			public void run() {
				ran = true;
			}
		};
		long timeBefore = System.currentTimeMillis();
		la.runAsynch(completionTask);

		long timeoutVal = 1000 * 10;
		while (!ran && System.currentTimeMillis() < (timeBefore + timeoutVal))
			Thread.sleep(100);
		assertTrue(ran);

		int result = la.getResult();
		assertTrue("Shouldn't have found a made-up name", result == LookupAsynch.HOST_NOT_FOUND);
	}

	public void testTestServerTests() throws Exception {
		// These tests are done against our test server
		configureTestResolver();

		doTestLookupAsynchTimeout();
		doTestMultipleLookups();
	}

	private void configureTestResolver() throws UnknownHostException {
		// Start up a load of resolvers on localhost (running on different
		// ports)
		TestServer[] servers = new TestServer[NUM_SERVERS];

		NonblockingResolver[] resolvers = new NonblockingResolver[NUM_SERVERS];
		for (int i = 0; i < NUM_SERVERS; i++) {
			servers[i] = TestServer.startServer(PORT + 1 + i, NUM_LOOKUPS + 1,
					1);
			NonblockingResolver res = new NonblockingResolver(TEST_SERVER);
			res.setTimeout(TIMEOUT);
			res.setPort(PORT + 1 + i);
			resolvers[i] = res;
		}
		ExtendedNonblockingResolver eres = ExtendedNonblockingResolver
				.newInstance(resolvers);
		eres.setRetries(1);
		LookupAsynch.setDefaultResolver(eres);
	}

	private void doTestLookupAsynchTimeout() throws Exception {
		// Start a query which will never end
		// wait for the required timeout period
		LookupAsynch la = new LookupAsynch("timeout.example.net", Type.A);
		ran = false;
		Runnable completionTask = new Runnable() {
			public void run() {
				ran = true;
			}
		};
		long timeBefore = System.currentTimeMillis();
		la.runAsynch(completionTask);

		long timeoutVal = 1000 * 10;
		while (!ran && System.currentTimeMillis() < (timeBefore + timeoutVal))
			Thread.sleep(100);
		assertTrue(ran);
		int result = la.getResult();
		assertTrue("Should have got a timeout", result == LookupAsynch.TRY_AGAIN);
	}

	final static int NUM_LOOKUPS = 100;

	final static int NUM_TIMEOUTS = 20;

	boolean[] ranArray = new boolean[NUM_LOOKUPS];

	int count = 0;

	public void doTestMultipleLookups() throws Exception {
		// We'd like to test good replies, NXDOMAINS, and timeouts at once
		ran = false;
		LookupAsynch[] las = new LookupAsynch[NUM_LOOKUPS];
		Runnable completionTask = new Runnable() {
			public void run() {
				synchronized (ranArray) {
					ranArray[count++] = true;
				}
			}
		};
		long timeBefore = System.currentTimeMillis();
		for (int i = 0; i < NUM_TIMEOUTS; i++) {
			LookupAsynch la = new LookupAsynch("timeout.example.net", Type.A);
			la.runAsynch(completionTask);
			las[i] = la;
		}
		for (int i = NUM_TIMEOUTS; i < NUM_LOOKUPS; i++) {
			LookupAsynch la = new LookupAsynch("example" + i + ".net", Type.A);
			la.runAsynch(completionTask);
			las[i] = la;
		}

		long timeoutVal = 1000 * 15;
		ran = false;
		while (!ran && System.currentTimeMillis() < (timeBefore + timeoutVal)) {
			Thread.sleep(100);
			ran = true;
			synchronized (ranArray) {
				for (int i = 0; i < NUM_LOOKUPS; i++) {
					if (ranArray[i] != true) {
						ran = false;
					}
				}
			}
		}
		assertTrue("Didn't all complete!", ran);

		for (int i = 0; i < NUM_TIMEOUTS; i++) {
			int result = las[i].getResult();
			assertTrue("Didn't get all the timeouts we wanted", result == LookupAsynch.TRY_AGAIN);
		}
		for (int i = NUM_TIMEOUTS; i < NUM_LOOKUPS; i++) {
			int result = las[i].getResult();
			assertTrue( "Didn't get all the successful responses we wanted", result == LookupAsynch.SUCCESSFUL);
		}
	}
}
