package org.xbill.DNS;

import uk.nominet.dnsjnio.*;

import java.net.UnknownHostException;
import java.util.*;

import java.io.*;

/**
 * The contents of this file are subject to the Mozilla Public Licence Version
 * 1.1 (the "Licence"); you may not use this file except in compliance with the
 * Licence. You may obtain a copy of the Licence at http://www.mozilla.org/MPL
 * Software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the Licence of
 * the specific language governing rights and limitations under the Licence. The
 * Original Code is dnsjnio. The Initial Developer of the Original Code is
 * Nominet UK (www.nominet.org.uk). Portions created by Nominet UK are Copyright
 * (c) Nominet UK 2006. All rights reserved.
 */
public class ExtendedNonblockingResolver {

	private class NotifyingResponseQueue extends ResponseQueue {
		Thread threadToBeNotified;

		public void setNotifyThread(Thread toNotify) {
			threadToBeNotified = toNotify;
		}

		public synchronized void insert(Response response) {
			list.addLast(response);
			notifyAll();
//			threadToBeNotified.notify();
			threadToBeNotified.interrupt();
		}
	}

	private class RequestQueue extends NotifyingResponseQueue { // @todo@ Need
																// to
		// template this!
		/**
		 * This method is called internally to add a new QueryRequest to the
		 * queue.
		 * 
		 * @param request
		 *            the new QueryRequest
		 */
		public synchronized void insert(QueryRequest request) {
			list.addLast(request);
			notifyAll();
//			threadToBeNotified.notify();
			threadToBeNotified.interrupt();
		}

		public synchronized Response getItem() {
			throw new RuntimeException("Not implemented!"); // @todo Fix this rubbish!
		}

		public synchronized QueryRequest getRequest() {
			while (isEmpty()) {
				try {
					waitingThreads++;
					wait();
				} catch (InterruptedException e) {
					Thread.interrupted();
				}
				waitingThreads--;
			}
			return (QueryRequest) (list.removeFirst());
		}
	}

	private class QueryRequest {
		protected ExtendedNonblockingResolver eres;

		protected ResponseQueue responseQueue;

		protected Object responseId;

		protected Message query;

		public QueryRequest(ExtendedNonblockingResolver eres,
				ResponseQueue responseQueue, Object responseId, Message query) {
			this.eres = eres;
			this.responseQueue = responseQueue;
			this.responseId = responseId;
			this.query = query;
		}
	}

	private class ResolutionThread extends Thread {
		// So ENBR will start up a new query thread on startup.
		// Thread will loop forever, listening for responses and sending new
		// queries.
		// When async client request comes in, ENBR sticks on queue and
		// forgets about it.
		// - client will handle queued responses
		// When sync client request comes in, ENBR calls async and waits for
		// completion.

		// inputQueue contains QueryRequests
		RequestQueue inputQueue;

		List notifyingResponseQueues = new LinkedList();

		public ResolutionThread(RequestQueue inputQueue) {
			this.inputQueue = inputQueue;
			inputQueue.setNotifyThread(this);
		}

		public void run() {
			while (true) {
				// Need to run in a main loop, watching out for either a
				// new item in the inputQueue, or a new response from a server
				// Need to use wait.
				// Then, responseQueue and requestQueue should notify us when
				// something ready
				System.out.println("Waiting for event");
				try {
					synchronized (this) {
					wait();
					}
				} catch (InterruptedException e) {
					Thread.interrupted();
				}
				System.out.println("Got an event");
				// Was it the requestQueue or the responseQueue?
				if (!inputQueue.isEmpty()) {
					System.out.println("Was a request");
					// Get a new request
					QueryRequest request = inputQueue.getRequest();
					notifyingResponseQueues.add(request.responseQueue);
					startNewRequest(request);
				} else {
					// Check response queues
					boolean gotResponse = false;
					for (Iterator it = notifyingResponseQueues.iterator(); it
							.hasNext();) {
						NotifyingResponseQueue queue = (NotifyingResponseQueue) (it
								.next());
						if (!queue.isEmpty()) {
							System.out.println("Was a response");
							gotResponse = true;
							// Get a new response
							Response response = queue.getItem();
							// @todo@ Do something with the response!
							processResponse(response);
						}
						if (!gotResponse) {
							throw new RuntimeException(
									"Stray event in ResolutionThread!");
						}
					}
				}
				System.out.println("ResolutionThread waiting on request");
				QueryRequest request = inputQueue.getRequest();
				System.out.println("ResolutionThread got request");
				queryLoop(request.eres, request.query, request.responseId,
						request.responseQueue);
			}
		}

		private void startNewRequest(QueryRequest request) {
			// @todo Send the first request
			throw new RuntimeException("Need to make first request");
		}

		private void processResponse(Response response) {
			// @todo@ !!!
			throw new RuntimeException("Implement processResponse!");
		}

		private void queryLoop(ExtendedNonblockingResolver eres, Message query,
				Object clientId, ResponseQueue clientQueue) {
			// The first server is queried for the name, and the response
			// awaited.
			// If the response is good, then it is returned to the caller. If it
			// times out, then the next resolver is tried at the same time as
			// retrying
			// the first. If there is a transport problem, then the first
			// resolver is not
			// retried at all, but the action moves on to the next resolver
			HashMap sent = new HashMap();
			int outstanding = 0;
			boolean done = false;
			int currentIndex = 0;
			NonblockingResolver[] resolvers;
			NonblockingResolver currentResolver = null;
			int retries;
			NotifyingResponseQueue queryQueue = new NotifyingResponseQueue();
			resolvers = getResolvers(eres);
			// Object[] inprogress = new Object[resolvers.length];
			retries = eres.retries;
			while (!done) {
				if (currentIndex < resolvers.length) {
					// Send a query on the next resolver
					System.out.println("Sending to new resolver "
							+ currentIndex);
					currentResolver = resolvers[currentIndex++];
					currentResolver.sendAsync(query, currentResolver,
							queryQueue);
					sent.put(currentResolver, new Integer(1));
					outstanding++;
					System.out.println("oustanding = " + outstanding);
				} else {
					System.out.println("No more resolvers to try");
				}
				if (outstanding == 0) {
					sendExceptionToClient(clientId, clientQueue);
					return;
				}
				boolean doneInnerLoop = false;
				while (!doneInnerLoop) {
					Response response = queryQueue.getItem();
					System.out.println("Got response " + response
							+ ", is Exception == " + response.isException());
					outstanding--;
					System.out.println("oustanding = " + outstanding);
					if (response.isException()) {
						// Exception - what do we do now?
						// If it is a timeout then we should retry up to retries
						// times
						if (response.getException() instanceof InterruptedIOException) {
							NonblockingResolver res = (NonblockingResolver) (response
									.getId());
							System.out.println("Got an exception from " + res);
							if (((Integer) (sent.get(res))).intValue() < retries) {
								System.out.println("Sending again to " + res);
								res.sendAsync(query, res, queryQueue);
								outstanding++;
								System.out.println("oustanding = "
										+ outstanding);
								Integer i = (Integer) (sent.get(res));
								i = new Integer(i.intValue() + 1);
								sent.remove(res);
								sent.put(res, i);
							} else {
								System.out.println("Not sending again to "
										+ res + ", as " + retries
										+ " retries exceeded");
							}
						} else {
							System.out.println("Got other exception ("
									+ response.getException()
									+ ") - ignoring that server");
						}
						if (outstanding == 0) {
							sendExceptionToClient(clientId, clientQueue);
							return;
						}
					} else {
						// Got a response! Now what do we do with it?
						System.out.println("Got good response");
						if (Options.check("verbose"))
							System.err.println("ExtendedResolver: "
									+ "received message");
						response.setId(clientId);
						response.setException(false);
						clientQueue.insert(response);
						return;
					}
					if (response.getId() == (currentResolver)) {
						// Response from the latest resolver - let's try the
						// next one
						// (if there is one)
						System.out.println("Got response for currentResolver ("
								+ currentResolver
								+ ") - trying next resolver for first time");
						if (currentIndex < resolvers.length) {
							doneInnerLoop = true;
						}
					}
				}
			}
		}

		private void sendExceptionToClient(Object clientId,
				ResponseQueue clientQueue) {
			// Uh oh! Run out of nameservers to query
			// Best throw TimeoutException
			System.out.println("Sending back exception to client");
			Response replyToClient = new Response();
			replyToClient.setException(new InterruptedIOException());
			replyToClient.setException(true);
			replyToClient.setId(clientId);
			clientQueue.insert(replyToClient);
		}

		private NonblockingResolver[] getResolvers(
				ExtendedNonblockingResolver eres) {
			List l = eres.resolvers;
			NonblockingResolver[] resolvers = (NonblockingResolver[]) l
					.toArray(new NonblockingResolver[l.size()]);
			if (eres.loadBalance) {
				int nresolvers = resolvers.length;
				/*
				 * Note: this is not synchronized, since the worst thing that
				 * can happen is a random ordering, which is ok.
				 */
				int start = eres.lbStart++ % nresolvers;
				if (eres.lbStart > nresolvers)
					eres.lbStart %= nresolvers;
				if (start > 0) {
					NonblockingResolver[] shuffle = new NonblockingResolver[nresolvers];
					for (int i = 0; i < nresolvers; i++) {
						int pos = (i + start) % nresolvers;
						shuffle[i] = resolvers[pos];
					}
					resolvers = shuffle;
				}
			}
			return resolvers;
		}
	}

	private static final int quantum = 5;

	private List resolvers;

	private boolean loadBalance = false;

	private int lbStart = 0;

	private int retries = 3;

	static int idCount = 0;

	private RequestQueue requestQueue;

	private ResolutionThread resolutionThread;

	private void init() {
		requestQueue = new RequestQueue();
		resolvers = new ArrayList();
		resolutionThread = new ResolutionThread(requestQueue);
		resolutionThread.start();
	}

	/**
	 * Creates a new Extended Resolver. The default ResolverConfig is used to
	 * determine the servers for which NonblockingResolver contexts should be
	 * initialized.
	 * 
	 * @see NonblockingResolver
	 * @see ResolverConfig
	 * @exception UnknownHostException
	 *                Failure occured initializing NonblockingResolvers
	 */
	public ExtendedNonblockingResolver() throws UnknownHostException {
		init();
		String[] servers = ResolverConfig.getCurrentConfig().servers();
		if (servers != null) {
			for (int i = 0; i < servers.length; i++) {
				Resolver r = new NonblockingResolver(servers[i]);
				r.setTimeout(quantum);
				resolvers.add(r);
			}
		} else
			resolvers.add(new NonblockingResolver());
	}

	/**
	 * Creates a new Extended Resolver
	 * 
	 * @param res
	 *            An array of pre-initialized Resolvers is provided.
	 * @see NonblockingResolver
	 * @exception UnknownHostException
	 *                Failure occured initializing NonblockingResolvers
	 */
	public ExtendedNonblockingResolver(Resolver[] res)
			throws UnknownHostException {
		init();
		for (int i = 0; i < res.length; i++)
			resolvers.add(res[i]);
	}

	/**
	 * Sends a message and waits for a response. Multiple servers are queried,
	 * and queries are sent multiple times until either a successful response is
	 * received, or it is clear that there is no successful response.
	 * 
	 * @param query
	 *            The query to send.
	 * @return The response.
	 * @throws IOException
	 *             An error occurred while sending or receiving.
	 */
	public Message send(Message query) throws IOException {
		ResponseQueue queue = new ResponseQueue();
		sendAsync(query, queue);
		Response response = queue.getItem();
		if (response.isException()) {
			throw new IOException();
		} else {
			return response.getMessage();
		}
	}

	/**
	 * Asynchronously sends a message to multiple servers, potentially multiple
	 * times, registering a listener to receive a callback on success or
	 * exception. Multiple asynchronous lookups can be performed in parallel.
	 * Since the callback may be invoked before the function returns, external
	 * synchronization is necessary.
	 * 
	 * @param query
	 *            The query to send
	 * @param queue
	 *            The ResponseQueue to hold the response
	 * @return An identifier, which is also a parameter in the callback
	 */
	public Object sendAsync(final Message query, final ResponseQueue queue) {
		Object id = new Integer(idCount++);
		sendAsync(query, id, queue);
		return id;
	}

	public void sendAsync(final Message query, final Object id,
			final ResponseQueue responseQueue) {
		QueryRequest request = new QueryRequest(this, responseQueue, id, query);
		requestQueue.insert(request);
	}

	/** Returns the nth resolver used by this ExtendedResolver */
	public Resolver getResolver(int n) {
		if (n < resolvers.size())
			return (Resolver) resolvers.get(n);
		return null;
	}

	/** Returns all resolvers used by this ExtendedResolver */
	public Resolver[] getResolvers() {
		return (Resolver[]) resolvers.toArray(new Resolver[resolvers.size()]);
	}

	/** Adds a new resolver to be used by this ExtendedResolver */
	public void addResolver(Resolver r) {
		resolvers.add(r);
	}

	/** Deletes a resolver used by this ExtendedResolver */
	public void deleteResolver(Resolver r) {
		resolvers.remove(r);
	}

	/**
	 * Sets whether the servers should be load balanced.
	 * 
	 * @param flag
	 *            If true, servers will be tried in round-robin order. If false,
	 *            servers will always be queried in the same order.
	 */
	public void setLoadBalance(boolean flag) {
		loadBalance = flag;
	}

	/** Sets the number of retries sent to each server per query */
	public void setRetries(int retries) {
		this.retries = retries;
	}
}
