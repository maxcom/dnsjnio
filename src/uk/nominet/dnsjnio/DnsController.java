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

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This class controls the I/O using the java.nio package.
 * A select thread is created, which runs the select loop
 * forever.
 * A queue of invocations is kept for the thread, and an
 * outgoing queue is also instantiated.
 * One DnsController services all resolvers
 */
public class DnsController {
    private static DnsController INSTANCE = new DnsController();
    private static List invocations;
    private static Selector selector;
    private static Thread selectThread;

    private DnsController() {
        initialise();
    }

    public static DnsController getInstance() {
        return INSTANCE;
    }

    public static Selector getSelector() {
        return selector;
    }

    private static void initialise() {
        invocations = new LinkedList();
        try {
            selector = Selector.open();
        } catch(IOException ie) {
            // log error?
            System.out.println("Error - can't open selector\r\n" + ie);
        }
        selectThread = new Thread("DnsSelect") {
            public void run() {
            	while (true) {
	            	try {
	            		selectLoop();
	            	} catch (Throwable t) {
                        System.out.println("Caught exception in DnsSelect thread\r\n" + t);
	            	}
            	}
            }
        };
        selectThread.setDaemon(true);
        selectThread.start();
    }

    private static void selectLoop() {
        Runnable task;
        while (true) {
            do {
                task = null;
                synchronized (invocations) {
                    if (invocations.size() > 0) {
                        task = (Runnable)(invocations.get(0));
                        invocations.remove(0);
                        task.run();
                    }
                }
            } while(task != null);

            try {
            	// We Could get rid of timer thread by calling selector.select(timeout) here.
            	// We'd need to keep a list of all the expected absolute timeouts and wait for the next one
            	// That could get fairly busy if there's a lot of outstanding requests,
            	// and the select loop is not the right place to waste time.
            	// I just don't know whether a separate polling thread (Timer) is the right answer!
                selector.select();
            } catch(Exception e) {
                System.out.println("Exception caught in select loop\r\n" + e);
            }

            // process any selected keys
            Set selectedKeys = selector.selectedKeys();
            Iterator it = selectedKeys.iterator();
            while(it.hasNext()) {
                SelectionKey key = (SelectionKey)(it.next());
                Connection conn = (Connection)key.attachment();
                int kro = key.readyOps();
                if((kro & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                    conn.doRead();
                }
                if((kro & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    conn.doWrite();
                }
                if((kro & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
                    conn.doConnect();
                }
                it.remove();
            }
        }
    }

    public static void invoke(Runnable task) {
        synchronized (invocations) {
            invocations.add(invocations.size(), task);
        }
        selector.wakeup();
    }

    public static boolean isSelectThread() {
        return Thread.currentThread() == selectThread;
    }
}
