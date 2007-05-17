package org.xbill.DNS;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.nominet.dnsjnio.Response;
import uk.nominet.dnsjnio.ResponseQueue;

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
	private class QueryRequest {
		protected ResponseQueue responseQueue;

		protected Object responseId;

		protected Message query;

		public QueryRequest(ResponseQueue responseQueue, Object responseId,
				Message query) {
			this.responseQueue = responseQueue;
			this.responseId = responseId;
			this.query = query;
		}

		// Each client request needs its own set of these
		protected HashMap sent = new HashMap();

		protected int outstanding = 0;

		protected int currentIndex = 0;

		protected NonblockingResolver currentResolver = null;
	}

	private class ResolutionThread extends Thread {
		NonblockingResolver[] resolvers;

		ExtendedNonblockingResolver eres;

		public ResolutionThread(ExtendedNonblockingResolver eres) {
			setName("EnbrResolutionThread");
			List l = eres.resolvers;
			resolvers = (NonblockingResolver[]) l
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
		}

		private Map clientRequests = new HashMap();

		ResponseQueue queryQueue = new ResponseQueue();

		private void startNewRequest(final Message query, final Object id,
				final ResponseQueue responseQueue) {
			// Send the first request
			// This is done in the client thread by making an asynchronous
			// request
			// using the queryQueue as the response queue.
			// @todo@ Do we need to check the ID to make sure it is not
			// currently in use?
			// Send a query on the next resolver
			QueryRequest request = new QueryRequest(responseQueue, id, query);
//			String name = request.query.getQuestion().getName().toString();
//			System.out.println("Sending first request for " + name
//					+ " to new resolver " + request.currentIndex);
			sendQueryToNextResolver(request);

			clientRequests.put(request.responseId, request);
		}

		private void processResponse(Response response, QueryRequest request) {
			// Stick the response in the client queue
			if (clientRequests.containsKey(request.responseId)) {
				// First take the client request out the list
				clientRequests.remove(request.responseId);

				response.setId(request.responseId);
				response.setException(false);
				// Now queue the response for the client.
				request.responseQueue.insert(response);
			} else {
				// No longer have the request so must have responded. Ignore it.
				System.out.println("ERROR - ProcessResponse ignoring good response due to no known request");
			}
		}

		// private void queryLoop(Message query, Object clientId, ResponseQueue
		// clientQueue) {
		public void run() {
			// The first server is queried for the name, and the response
			// awaited.
			// If the response is good, then it is returned to the caller. If it
			// times out, then the next resolver is tried at the same time as
			// retrying
			// the first. If there is a transport problem, then the first
			// resolver is not
			// retried at all, but the action moves on to the next resolver
			// Object[] inprogress = new Object[resolvers.length];
			while (true) {
				// We have a load of client requests currently on the go.
				// each client request may have multiple queries outstanding on
				// multiple resolvers.
				// however, ALL dns queries use the same responseQueue
				// so we just wait on the next response and do the appropriate
				// thing
				Response nextResponse = queryQueue.getItem();
				// Now we need to match it up to a request
				QueryRequest request = ((QueryId)(nextResponse.getId())).request;
				request.outstanding--;

				// Then do the appropriate thing depending on the response
				if (nextResponse.isException()) {
					// deal with exception
					// If timeout then we should retry up to retries times
					if (nextResponse.getException() instanceof InterruptedIOException) {
						dealWithTimeout(nextResponse, request);
					} else {
//						System.out.println("Got other exception ("
//								+ nextResponse.getException()
//								+ ") - ignoring that server");
					}
					if (request.outstanding == 0) {
						sendExceptionToClient(request);
						continue;
					}
				} else {
					// deal with good response
					processResponse(nextResponse, request);
					continue;
				}
				if (((QueryId)(nextResponse.getId())).resolver == (request.currentResolver)) {
					// Response from the latest resolver - let's try the
					// next one
					// (if there is one)
//					System.out.println("Got response for currentResolver ("
//							+ request.currentResolver
//							+ ") - trying next resolver for first time");
					queryNextResolver(request);
				}
				if (request.outstanding == 0) {
					sendExceptionToClient(request);
					continue;
				}
			}
		}

		private void dealWithTimeout(Response nextResponse, QueryRequest request) {
			NonblockingResolver res = ((QueryId)(nextResponse.getId())).resolver;
//			System.out.println("Got an exception from " + res);
			if (((Integer) (request.sent.get(res))).intValue() < retries) {
//				System.out.println("Sending again to " + res);
				QueryId id = new QueryId(request, res);
				res.sendAsync(request.query, id, queryQueue);
				request.outstanding++;
//				System.out.println("oustanding = " + request.outstanding);
				Integer i = (Integer) (request.sent.get(res));
				i = new Integer(i.intValue() + 1);
				request.sent.remove(res);
				request.sent.put(res, i);
			} else {
//				System.out.println("Not sending again to " + res + ", as "
//						+ retries + " retries exceeded");
			}
		}

		private void queryNextResolver(QueryRequest request) {
			if (request.currentIndex < resolvers.length) {
				// Send a query on the next resolver
//				System.out.println("Sending to new resolver "
//						+ request.currentIndex);
				sendQueryToNextResolver(request);
			} else {
//				System.out.println("No more resolvers to try");
			}
		}
		
		private class QueryId {
			protected QueryRequest request;
			protected NonblockingResolver resolver;
			public QueryId (QueryRequest request, NonblockingResolver resolver) {
				this.request = request;
				this.resolver = resolver;
			}
		}

		private void sendQueryToNextResolver(QueryRequest request) {
			request.currentResolver = resolvers[request.currentIndex++];
			QueryId id = new QueryId (request, request.currentResolver);
			request.currentResolver.sendAsync(request.query,
					id, queryQueue);
			request.sent.put(request.currentResolver, new Integer(1));
			request.outstanding++;
//			System.out.println("oustanding = " + request.outstanding);
		}

		private void sendExceptionToClient(QueryRequest request) {
			// Uh oh! Run out of nameservers to query
			// Best throw TimeoutException
//			System.out.println("Sending back exception to client");

			// First take the client request out the list
			clientRequests.remove(request.responseId);

			Response replyToClient = new Response();
			replyToClient.setException(new InterruptedIOException());
			replyToClient.setException(true);
			replyToClient.setId(request.responseId);
			request.responseQueue.insert(replyToClient);
		}

	}

	private static final int quantum = 5;

	private List resolvers;

	private boolean loadBalance = false;

	private int lbStart = 0;

	private int retries = 3;

	static int idCount = 0;

	private ResolutionThread resolutionThread;

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
		resolvers = new ArrayList();
		String[] servers = ResolverConfig.getCurrentConfig().servers();
		if (servers != null) {
			for (int i = 0; i < servers.length; i++) {
				Resolver r = new NonblockingResolver(servers[i]);
				r.setTimeout(quantum);
				resolvers.add(r);
			}
		} else
			resolvers.add(new NonblockingResolver());
		startResolutionThread();
	}

	private void startResolutionThread() {
		resolutionThread = new ResolutionThread(this);
		resolutionThread.start();
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
	public static ExtendedNonblockingResolver newInstance(Resolver[] res) throws UnknownHostException {
		// Don't allow the this reference to escape during construction
		// (a thread is created and started in the constructor)
		ExtendedNonblockingResolver enbr = new ExtendedNonblockingResolver(res);
		return enbr;		
	}
	
	private ExtendedNonblockingResolver(Resolver[] res)
			throws UnknownHostException {
		resolvers = new ArrayList();
		for (int i = 0; i < res.length; i++)
			resolvers.add(res[i]);
		startResolutionThread();
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
	 * times, registering a queue to receive a response on success or
	 * exception. Multiple asynchronous lookups can be performed in parallel.
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
		resolutionThread.startNewRequest(query, id, responseQueue);
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
