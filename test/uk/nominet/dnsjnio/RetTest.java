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

import junit.framework.TestCase;
import org.xbill.DNS.*;

import java.util.List;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

/**
 * Test the Listener implementation of NonblockingResolver.
 * Have one thread cycling through several resolvers.
 * Each resolver may have several outstanding queries lodged
 * with it.
 */
public class RetTest extends TestCase {
    final static int NUM_RESOLVERS = 5;
    ResolverWrap[] resolvers = new ResolverWrap[NUM_RESOLVERS];
    final static int MAX_QUERIES = 100;
    final static int TOTAL_QUERIES = 10000; // 5  million queries have run successfully
    int queriesSoFar = 0;
    final static String SERVER = "localhost";
    final static int PORT = TestServer.PORT;
    final static int TIMEOUT = 10;
    public static int threadIdCount = 0;
    final static Random random = new Random();
    Object lock = new Object();
    public RetTest(String arg0) {
        super(arg0);
        TestServer.startServer();
        threadIdCount = 0;
        Timer.reset();
    }

    private void configureResolvers() throws Exception {
        for (int i = 0; i < NUM_RESOLVERS; i++) {
            resolvers[i] = new ResolverWrap();
            resolvers[i].configure();
        }
    }

    public void testSingleThreadManyResolvers() throws Exception {
        configureResolvers();
        while (queriesSoFar < TOTAL_QUERIES) {
            for (int i = 0; i < NUM_RESOLVERS; i++) {
                // Check if any resolvers have got an open query slot
                if (resolvers[i].getQueriesInProgress() < MAX_QUERIES) {
                    // Send another one!
                    resolvers[i].sendNextQuery();
                    queriesSoFar++;
                }
                Thread.yield();
            }
            Thread.sleep(1);

        }
        // Wait for queries to complete
        for (int i = 0; i < NUM_RESOLVERS; i++) {
            while (resolvers[i].getQueriesInProgress() > 0) {
                Thread.sleep(1);
            }
        }

        // Check the results!
        for (int i = 0; i < NUM_RESOLVERS; i++) {
            System.out.println("Resolver " + i + ", good = " + resolvers[i].numOk + ", bad = " + resolvers[i].numBad + " of " + resolvers[i].numSent);
        }
        for (int i = 0; i < NUM_RESOLVERS; i++) {
            assertTrue ((resolvers[i].numOk + resolvers[i].numBad) == resolvers[i].numSent);
            assertTrue ((resolvers[i].numBad) < resolvers[i].numSent * 0.05);
        }
    }


    public class ResolverWrap implements ResolverListener {
        Resolver resolver;
        int queriesInProgress = 0;
        public int numSent = 0;
        public int numOk = 0;
        public int numBad = 0;
        int threadId;
        Object lock = new Object();
        List ids = new LinkedList();

        public ResolverWrap() {
            threadId = threadIdCount++;
        }
        public void configure() throws Exception {
//            resolver = new NonblockingResolver(SERVER);
            resolver = new NonblockingResolver(SERVER);
            resolver.setPort(PORT);
            resolver.setTimeout(TIMEOUT);
        }

        public int getQueriesInProgress() {
            synchronized (lock) {
                return queriesInProgress;
            }
        }

        public int getNumSent() {
            synchronized (lock) {
                return numSent;
            }
        }

        public Resolver getResolver() {
            return resolver;
        }

        public void sendNextQuery() throws Exception {
            // Send another query
            synchronized (lock) {
                String name = "example_" + threadId + "_" + numSent;
                Message query = getQuery(name);
                OPTRecord opt = new OPTRecord(65535, (byte)0,
                        (byte)0, ExtendedFlags.DO);
                if (opt != null)
                    query.addRecord(opt, Section.ADDITIONAL);
                query.getHeader().setFlag(Flags.RD);
//                System.out.println("Querying " + threadId + " for " + name);
                synchronized (lock) {
                    Object id = resolver.sendAsync(query, this);
                    ids.add(id);
                    queriesInProgress++;
                    numSent++;
                }
            }
        }

        private Message getQuery(String nameString) throws TextParseException {
            Name name = Name.fromString(nameString, Name.root);
            Record question = Record.newRecord(name, Type.A, DClass.ANY);
            return Message.newQuery(question);
        }

        public void receiveMessage(Object id, Message message) {
            checkId(id);
            pause();
            synchronized (lock) {
                queriesInProgress--;
                numOk++;
            }
            assertTrue(message.getQuestion().getName().toString().startsWith("example_"+threadId+"_"));
//            System.out.println("Got result for " + threadId + " for " + message.getQuestion().getName());
        }

        public void handleException(Object id, Exception exception) {
            checkId(id);
            pause();
            synchronized (lock) {
                queriesInProgress--;
                numBad++;
            }
//            System.out.println("Got exception for " + threadId);
//            exception.printStackTrace();
        }

        private void pause() {
            int delay = random.nextInt(2000);
            try {
                Thread.sleep(delay);
            } catch (Exception e) {}
        }

        private void checkId(Object id) {
        	synchronized (lock) {
                assertTrue("Bad id!", ids.contains(id));
                ids.remove(id);
        	}
        }
    }
}
