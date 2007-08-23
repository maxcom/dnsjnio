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

import java.util.LinkedList;

/**
 * This class implements a simple queue.
 * It blocks threads wishing to remove an object from the queue
 * until an object is available.
 */
public class ResponseQueue
{
	protected LinkedList list = new LinkedList();
	protected  int waitingThreads = 0;
                                                                                        
    /**
     * This method is called internally to add a new Response to the queue.
     * @param response the new Response
     */
    public synchronized void insert(Response response)
	{
		list.addLast(response);
		notify();
	}

	public synchronized Response getItem()
	{
		while ( isEmpty() ) {
			try	{ waitingThreads++; wait();}
			catch (InterruptedException e)	{
				Thread.currentThread().interrupt();
				//Thread.interrupted();
				}
			waitingThreads--;
		}
		return (Response)(list.removeFirst());
	}

	public boolean isEmpty() {
		return 	(list.size() - waitingThreads <= 0);
	}
}
