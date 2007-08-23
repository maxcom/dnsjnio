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

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ListenerTest extends TestCase {
    final static String SERVER = "localhost";
    final static int PORT = TestServer.PORT;
    final static int TIMEOUT = 10;
    private int idCount = 0;
    private Map results = Collections.synchronizedMap(new HashMap());
    static int threadId = 0;
    Object lock = new Object();
    static TestServer server = TestServer.startServer();

    public void setUp() {
        reset();
//        server = TestServer.startServer();
    }
//    
//    public void tearDown() {
//    	server.stopServer();
//    }
//
    
    public void finalize() {
       server.stopRunning();    	
    }
    
    private void reset() {
        resetResults();
        idCount = 0;
        Timer.reset();
    }

    private void resetResults() {
        results = Collections.synchronizedMap(new HashMap());
    }

    public void testSendAsync() throws Exception {
        NonblockingResolver resolver = new NonblockingResolver(SERVER);
        resolver.setPort(PORT);
        resolver.setTimeout(TIMEOUT);

        Name name = Name.fromString("example.com", Name.root);
        Record question = Record.newRecord(name, Type.A, DClass.ANY);
        Message query = Message.newQuery(question);
        Integer id = new Integer(idCount++);
        ResolverListenerImpl listener = new ResolverListenerImpl(id, results, lock);
        resolver.sendAsync(query, id, listener);
        while (!(results.containsKey(id))) {
            synchronized (lock) {
                lock.wait();
            }
        }
        QueryResult result = (QueryResult) (results.get(id));
        assertTrue("Exception thrown " + result.x, result.x == null);
        assertTrue(result.message.getRcode() == 0);
    }

    public void testTimeoutsAsync() throws Exception {
        // Start a query which will never end
        // wait for the required timeout period
        NonblockingResolver resolver = new NonblockingResolver("localhost");
        int timeout = 1;
        resolver.setTimeout(timeout);
        resolver.setPort(PORT);
        Message query = getQuery("timeout.example.net");
        Integer id = new Integer(idCount++);
        ResolverListenerImpl listener = new ResolverListenerImpl(id, results, lock);
        long startTime = System.currentTimeMillis();
        resolver.sendAsync(query, id, listener);
        while (!(results.containsKey(id))) {
            synchronized (lock) {
                lock.wait();
            }
        }
        long endTime = System.currentTimeMillis();
        QueryResult result = (QueryResult) (results.get(id));
        if (result.received) {
            fail("Response recevied for impossible query!");
        }
        if (result.x == null) {
            fail("No exception thrown when timeout expected!");
        }
        if (!(result.x instanceof SocketTimeoutException)) {
            fail("Exception " + result.x + " thrown instead of SocketTimeoutException!");
        }
        long totalTime = endTime - startTime;
        assertTrue("Timeout too short! " + totalTime, totalTime > timeout * 900);
        assertTrue("Timeout too long! " + totalTime, totalTime < timeout * 1100);
    }

    public void testManySequentialAsynchronousRequests() throws Exception {
        NonblockingResolver resolver = new NonblockingResolver(SERVER);
        doTestManySequentialAsynchronousRequests(resolver);
    }

    public void testManyAsynchronousRequests() throws Exception {
        NonblockingResolver resolver = new NonblockingResolver(SERVER);
        doTestManyAsynchronousRequests(resolver, 500, PORT);
    }

    private void doTestManyAsynchronousRequests(NonblockingResolver resolver, int numRequests, int port) throws TextParseException, InterruptedException {
        int bad = 0;
        int headerIdCount = 0;
//        String name = "example.com";
        String name = RemoteServerTest.REAL_QUERY_NAME;
        resolver.setTimeout(TIMEOUT);
        resolver.setPort(port);
        int startId = idCount;
        for (int i = 0; i < numRequests; i++) {
            Message query = getQuery(name);
            int headerId = headerIdCount++;
            query.getHeader().setID(headerId);
            Integer id = new Integer(idCount++);
            ResolverListenerImpl listener = new ResolverListenerImpl(id, results, lock);
//            System.out.println("Sending id = " + id + ", header ID = " + query.getHeader().getID());
            resolver.sendAsync(query, id, listener);
        }
        while (results.size() < numRequests) {
            synchronized (lock) {
                lock.wait();
            }
//            System.out.println(results.size() + " back");
        }
        for (int i = startId; i < startId + numRequests; i++) {
            QueryResult result = (QueryResult) (results.get(new Integer(i)));
            if (result.received) {
//                System.out.println("Result " + result.id + " received OK");
            } else {
//                System.out.println("Result " + result.id + " threw Exception " + result.x);
                bad++;
            }
            assertTrue(result.received || result.x != null);
        }
        // Check %age of timeouts/good responses.
        assertTrue("Too many exceptions! (" + bad + " of " + numRequests + ")", bad < (numRequests * 0.05));
    }

    public void testManyAsynchronousClients() throws Exception {
        // test many NonblockingResolvers using asynchronous sends
        int numClients = 100;
        int startId = idCount;
        int bad = 0;

        for (int i = 0; i < numClients; i++) {
            Thread task = new Thread() {
                public void run() {
                    try {
                        NonblockingResolver resolver = new NonblockingResolver(SERVER);
                        resolver.setPort(PORT);
                        resolver.setTimeout(TIMEOUT);
                        Object id = new Integer(idCount++);
                        Message query = getQuery("example" + ((Integer) (id)).intValue() + ".com");
                        ResolverListenerImpl listener = new ResolverListenerImpl(id, results, lock);
                        resolver.sendAsync(query, id, listener);
                    } catch (Exception e) {}
                }
            };
            task.start();
        }
        while (results.size() < numClients) {
            synchronized (lock) {
                lock.wait();
            }
//            System.out.println("Got back " + results.size());
        }
        for (int i = startId; i < startId + numClients; i++) {
            QueryResult result = (QueryResult) (results.get(new Integer(i)));
            if (result.received) {
//                System.out.println("Result " + result.id + " received OK");
            } else {
//                System.out.println("Result " + result.id + " threw Exception " + result.x);
                bad++;
            }
            assertTrue(result.received || result.x != null);
        }
        // Check %age of timeouts/good responses.
        assertTrue("Too many exceptions! (" + bad + " of " + numClients + ")", bad < (numClients * 0.05));
    }

    private void doTestManySequentialAsynchronousRequests(NonblockingResolver resolver) throws TextParseException, InterruptedException {
        int numRequests = 50;
        int bad = 0;
        String name = "example.com";
        resolver.setTimeout(TIMEOUT);
        resolver.setPort(PORT);
        for (int i = 0; i < numRequests; i++) {
            resetResults();
            Message query = getQuery(name + idCount);
            Integer id = new Integer(idCount++);
            ResolverListenerImpl listener = new ResolverListenerImpl(id, results, lock);
            resolver.sendAsync(query, id, listener);
            while (results.size() < 1) {
                synchronized (lock) {
                    lock.wait();
                }
            }

            QueryResult result = (QueryResult) (results.get(id));
            assertTrue(result != null);
//            System.out.println("Got back " + i + ", " + ((result).timedout ? "timed out" : "OK"));
            if (result.x != null) {
                bad++;
            }
            results.remove(id);
        }
        assertTrue("Too many exceptions! (" + bad + " of " + numRequests + ")", bad < (numRequests * 0.05));
    }

//    public void testManySequentialAsynchronousRequestsTCP() throws Exception {
//        NonblockingResolver resolver = new NonblockingResolver(SERVER);
//        resolver.setTCP(true);
//        doTestManySequentialAsynchronousRequests(resolver);
//    }

    public void testManyAsynchronousRequestsTCP() throws Exception {
        // Our blocking test server is unhappy about handling 500 concurrent requests...
//        NonblockingResolver resolver = new NonblockingResolver(RemoteServerTest.REAL_SERVER);
        NonblockingResolver resolver = new NonblockingResolver(SERVER);
        resolver.setTCP(true);
        doTestManyAsynchronousRequests(resolver, 50, PORT); // 53);
    }

    private Message getQuery(String nameString) throws TextParseException {
        Name name = Name.fromString(nameString, Name.root);
        Record question = Record.newRecord(name, Type.A, DClass.ANY);
        return Message.newQuery(question);
    }

    public class SynchronousQueryThread extends Thread {
        int timeout;
        String nameString;
        Object id;
        Message ret;
        boolean useSimpleResolver = false;
        boolean useLocalHost = false;

        SynchronousQueryThread(String name, int timeout, Object id, boolean useSimple, boolean useLocalHost) {
            this.timeout = timeout;
            this.nameString = name;
            this.id = id;
            this.useSimpleResolver = useSimple;
            this.useLocalHost = useLocalHost;
        }

        public void run() {
            Resolver resolver = null;
            Message query = null;
            try {
                if (useSimpleResolver) {
                    resolver = new SimpleResolver(SERVER);
                } else {
                    if (!useLocalHost) {
                        resolver = new NonblockingResolver(SERVER);
                    } else {
                        resolver = new NonblockingResolver("localhost");
                    }
                }
                resolver.setTimeout(timeout);
                Name name = Name.fromString(nameString, Name.root);
                Record question = Record.newRecord(name, Type.A, DClass.ANY);
                query = Message.newQuery(question);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            try {
                ret = resolver.send(query);
            }
            catch (Exception e) {
                QueryResult result = new QueryResult(false, e, id);
                results.put(id, result);
                return;
            }
            QueryResult result = new QueryResult(true, ret, id);
            results.put(id, result);
        }
    }

    public class ResolverListenerImpl implements ResolverListener {
        Object id;
        Map ownResults;
        Object listenerLock;

        public ResolverListenerImpl(Object id, Map results, Object lock) {
            this.id = id;
            this.ownResults = results;
            this.listenerLock = lock;
        }

        public void receiveMessage(Object id, Message message) {
            if (!id.equals(this.id)) {
                System.out.println(message);
                fail("Response for wrong id! orig = " + this.id + ", returned = " + id + " query for " + message.getQuestion().getName());
            }
            QueryResult result = new QueryResult(true, message, id);
            ownResults.put(id, result);
            synchronized (listenerLock) {
                listenerLock.notify();
            }
        }

        public void handleException(Object id, Exception exception) {
            if (!id.equals(this.id)) {
                fail("Response for wrong id! orig = " + this.id + ", returned = " + id);
            }
            QueryResult result = new QueryResult(false, exception, id);
            ownResults.put(id, result);
            synchronized (listenerLock) {
                listenerLock.notify();
            }
        }
    }

    private class QueryResult {
        public boolean received = false;
        public Exception x = null;
        public boolean timedout = false;
        public Object id;
        public Message message = null;

        public String toString() {
            return "id=" + id + ", received=" + (received ? "true" : "false") + ", timedout = " + (received ? "true" : "false") +
                    ", message=" + message + ", x=" + x;
        }

        public QueryResult(boolean received, Object x, Object id) {
            if (received) {
                set(received, (Message) x, null, id, false);
            } else {
                set(received, null, (Exception) x, id, false);
            }
        }

        public void set(boolean received, Message message, Exception x, Object id, boolean timedout) {
            this.received = received;
            this.x = x;
            this.timedout = timedout;
            this.id = id;
            this.message = message;
            if (!this.timedout) {
                if (x instanceof SocketTimeoutException) {
                    this.timedout = true;
                }
            }
//            System.out.println("id=" + id + ", received=" + (received ? "true" + " name=" + message.getQuestion().getName() : "false" + ", x=" + this.x));
        }
    }

    public void testMultipleThreadedClientsManyMessages() throws Exception {
        // Start a few threaded clients, with each one alternating between one of
        // 2 NonblockingResolvers, each pointing at the same TestServer.
        int numThreads = 10;
        int totalQueries = 100;
        int outstandingQueries = 25;
        NonblockingResolver res1 = new NonblockingResolver(SERVER);
        res1.setPort(PORT);
        res1.setTimeout(TIMEOUT);
        NonblockingResolver res2 = new NonblockingResolver(SERVER);
        res2.setPort(PORT);
        res2.setTimeout(TIMEOUT);
        Thread[] threads = new Thread[numThreads];
        Map[] resultss = new Map[numThreads];
        for (int i = 0; i < numThreads; i++) {
            resultss[i] = Collections.synchronizedMap(new HashMap());
            threads[i] = new AsynchronousQueryThread (totalQueries, outstandingQueries, res1, res2, resultss[i]);
            threads[i].start();
        }
        for (int i = 0; i < numThreads; i++) {
            threads[i].join();
        }
        // Now make sure that the results were OK
        for (int i = 0; i < numThreads; i++) {
            int good = 0;
            for (int j = 0; j < resultss[i].size(); j++) {
                QueryResult result = (QueryResult)(resultss[i].get(new Integer(j)));
                if (result.x == null) {
                    good++;
                }
            }
            if (good < (resultss[i].size() * 0.95)) {
                assertTrue("Too many failures for " + i + ", good = " + good + ", bad = " + (resultss[i].size() - good), false);
            }
        }
    }

    public class AsynchronousQueryThread extends Thread {
        // This class should run X outstanding queries, for a total of Y queries.
        int numQueriesTotal;
        int maxOutstandingQueries;
        NonblockingResolver res1, res2;
        Map threadResults;
        int threadIdCount = 0;
        Object threadLock = new Object();
        int id = 0;
        public AsynchronousQueryThread(int numQueriesTotal, int maxOutstandingQueries, NonblockingResolver res1, NonblockingResolver res2, Map results) {
            this.numQueriesTotal = numQueriesTotal;
            this.maxOutstandingQueries = maxOutstandingQueries;
            this.res1 = res1;
            this.res2 = res2;
            this.threadResults = results;
            this.id = threadId++;
        }

        public void run() {
            for (int i =0; i < numQueriesTotal; i++) {
                Message query=null;
                try {
                    query = getQuery("example" + "_" + id + "_" + threadIdCount);
                } catch (Exception e) {}
                NonblockingResolver res = res1;
                if ((i+id) % 2 == 0) {
                    res = res2;
                }
                Integer id = new Integer(threadIdCount++);
                ResolverListenerImpl listener = new ResolverListenerImpl(id, threadResults, threadLock);
                res.sendAsync(query, id, listener);
                while (threadResults.size() <= i) {
                    synchronized (threadLock) {
                        try {
                            threadLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }
    }
}
