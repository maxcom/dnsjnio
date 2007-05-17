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
                selectLoop();
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
                selector.select();
            } catch(Exception e) {}

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
