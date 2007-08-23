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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class handles the timeouts for the Transaction objects.
 * A new thread is started to handle all the timeouts for all the
 * Transaction objects.
 * A separate list is kept for single port transactions, and normal transactions.
 */
public class Timer {
    // There has to be a better way than this!
    private final static Map timeouts = Collections.synchronizedMap(new HashMap());
    private final static Map singlePortTimeouts = Collections.synchronizedMap(new HashMap());
    private static boolean threadStarted = false;
    private final static Thread timerThread = new Thread("DnsTimer") {
        public void run() {
            while (true) {
                try {
                    sleep(100); // Poll 10 times a second
                }
                catch (InterruptedException e) {
                    return;
                }
                long currentTime = System.currentTimeMillis();
                List removeList = new LinkedList();
                // Now go through and call any timeouts
                synchronized (timeouts) {
                    Set keys = timeouts.keySet();
                    for (Iterator it = keys.iterator(); it.hasNext();) {
                        Transaction t = (Transaction)(it.next());
                        long endTime = ((Long)(timeouts.get(t))).longValue();
                        if (endTime <= currentTime) {
                            removeList.add(t);
                        }
                    }
                    for (Iterator it = removeList.iterator(); it.hasNext();) {
                        TimerListener t = (TimerListener)it.next();
                        t.timedOut(null);
                        timeouts.remove(t);
                    }
                }
                removeList.clear();
                synchronized (singlePortTimeouts) {
                    Set keys = singlePortTimeouts.keySet();
                    for (Iterator it = keys.iterator(); it.hasNext();) {
                        ListenerAndData l = (ListenerAndData)(it.next());
                        long endTime = ((Long)(singlePortTimeouts.get(l))).longValue();
                        if (endTime <= currentTime) {
                            removeList.add(l);
                        }
                    }
                    for (Iterator it = removeList.iterator(); it.hasNext();) {
                        ListenerAndData l = (ListenerAndData)it.next();
                        l.getListener().timedOut(l.getqData());
                        singlePortTimeouts.remove(l);
                    }
                }
            }
        }
    };

    /**
     * Add a timeout callback for the specified Transaction.
     * @param timeout the absolute timeout time in milliseconds.
     * @param t the Transaction to be called back.
     */
    public final static void addTimeout(long timeout, TimerListener t, QueryData qData) {
        ListenerAndData l = new ListenerAndData();
        l.setListener(t);
        l.setqData(qData);
        checkTimerStarted();
        synchronized (singlePortTimeouts) {
            singlePortTimeouts.put(l, new Long(timeout));
        }
    }

    private static synchronized void checkTimerStarted() {
        if (!threadStarted) {
            threadStarted = true;
            timerThread.setDaemon(true);
            timerThread.start();
        }
    }

    public final static void addTimeout(long timeout, TimerListener t) {
        checkTimerStarted();
        synchronized (timeouts) {
            timeouts.put(t, new Long(timeout));
        }
    }

    /**
     * Cancel the timeout callback for the specified Transaction
     * @param t
     */
    public final static void cancelTimeout(TimerListener t, QueryData qData) {
        // Need to search through list to find entry for qData.getId().
        ListenerAndData toRemove = null;
        Set set = null;
        synchronized (singlePortTimeouts) {
            set = singlePortTimeouts.keySet();
            for (Iterator it = set.iterator(); it.hasNext();) {
                ListenerAndData x = (ListenerAndData)it.next();
                if (x.getListener() == t) {
                    if (x.getqData().getId().equals(qData.getId())) {
                        if (x.getqData().getQuery().getHeader().getID()==(qData.getQuery().getHeader().getID())) {
                            toRemove = x;
                            break;
                        }
                    }
                }
            }
            singlePortTimeouts.remove(toRemove);
        }
    }

    public final static void cancelTimeout(TimerListener t) {
        synchronized (timeouts) {
            timeouts.remove(t);
        }
    }

    /**
     * Cancel all timeout callbacks.
     */
    public final static void reset() {
        synchronized (timeouts) {
            timeouts.clear();
        }
        synchronized (singlePortTimeouts) {
            singlePortTimeouts.clear();
        }
    }
}
