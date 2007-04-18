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

import java.util.LinkedList;

/**
 * This class implements a simple queue.
 * It blocks threads wishing to remove an object from the queue
 * until an object is available.
 */
public class ResponseQueue extends LinkedList
{
	private int waitingThreads = 0;
                                                                                        
    /**
     * This method is called internally to add a new Response to the queue.
     * @param response the new Response
     */
    public synchronized void insert(Response response)
	{
		addLast(response);
		notify();
	}

	public synchronized Response remove()
	{
		if ( isEmpty() ) {
			try	{ waitingThreads++; wait();}
			catch (InterruptedException e)	{Thread.interrupted();}
			waitingThreads--;
		}
		return (Response)removeFirst();
	}

	public boolean isEmpty() {
		return 	(size() - waitingThreads <= 0);
	}
}
