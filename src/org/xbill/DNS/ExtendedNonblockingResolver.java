package org.xbill.DNS;

import uk.nominet.dnsjnio.*;

import java.net.UnknownHostException;
import java.util.*;

import java.io.*;
import java.net.*;

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

	private static class Resolution {
		NonblockingResolver[] resolvers;

		HashMap sent;

		Object[] inprogress;

		int retries;

		int outstanding;

		boolean done;

		Message query;

		Message response;

		Throwable thrown;

		Object clientId;

		ResponseQueue clientQueue;

		public Resolution(ExtendedNonblockingResolver eres, Message query) {
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
			sent = new HashMap();
			inprogress = new Object[resolvers.length];
			retries = eres.retries;
			this.query = query;
		}

		/* Start a synchronous resolution */
		public Message start() throws IOException {
			ResponseQueue queue = new ResponseQueue();
			startAsync(queue, this);
			Response response = queue.getItem();
			if (response.isException()) {
				throw new IOException();
			} else {
				return response.getMessage();
			}
		}

		public void startAsync(ResponseQueue clientQueue) {
			startAsync(clientQueue, this);
		}

		/* Start an asynchronous resolution */
		public void startAsync(ResponseQueue clientQueue, Object clientId) {
			this.clientId = clientId;
			this.clientQueue = clientQueue;
			Thread queryThread = new Thread("ExtendedResolverQueryThread") {
				public void run() {
					queryLoop();
				}
			};
			queryThread.start();
		}

		private void queryLoop() {
			// The first server is queried for the name, and the response
			// awaited.
			// If the response is good, then it is returned to the caller. If it
			// times out, then the next resolver is tried at the same time as
			// retrying
			// the first. If there is a transport problem, then the first
			// resolver is not
			// retried at all, but the action moves on to the next resolver
			boolean done = false;
			int currentIndex = 0;
			ResponseQueue queryQueue = new ResponseQueue();
			while (!done) {
				// Send a query on the next resolver
				System.out.println("Sending to new resolver " + currentIndex);
				NonblockingResolver currentResolver = resolvers[currentIndex++];
				currentResolver.sendAsync(query, currentResolver, queryQueue);
				sent.put(currentResolver, new Integer(1));
				outstanding++;
				boolean doneInnerLoop = false;
				while (!doneInnerLoop) {
					Response response = queryQueue.getItem();
					System.out.println("Got response " + response);
					outstanding--;
					NonblockingResolver r = (NonblockingResolver) (response
							.getId());
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
								Integer i = (Integer) (sent.get(res));
								i = new Integer(i.intValue() + 1);
								sent.remove(res);
								sent.put(res, i);
							}
						}
						if (outstanding == 0) {
							// Uh oh! Run out of nameservers to query
							// Best throw TimeoutException
							Response replyToClient = new Response();
							replyToClient
									.setException(new InterruptedIOException());
							synchronized (clientQueue) {
								clientQueue.insert(replyToClient);
							}
							return;
						}
					} else {
						// Got a response! Now what do we do with it?
						System.out.println("Got good response");
						if (Options.check("verbose"))
							System.err.println("ExtendedResolver: "
									+ "received message");
						response.setId(clientId);
						synchronized (clientQueue) {
							clientQueue.insert(response);
						}
						return;
					}
					if (response.getId() == (currentResolver)) {
						// Response from the latest resolver - let's try the
						// next one
						// (if there is one)
						if (currentIndex < resolvers.length) {
							doneInnerLoop = true;
						}
					}
				}
			}
		}

	}

	private static final int quantum = 5;

	private List resolvers;

	private boolean loadBalance = false;

	private int lbStart = 0;

	private int retries = 3;

	static int idCount = 0;

	private void init() {
		resolvers = new ArrayList();
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
		Resolution res = new Resolution(this, query);
		return res.start();
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
	 * @param listener
	 *            The object containing the callbacks.
	 * @return An identifier, which is also a parameter in the callback
	 */
	// public Object
	// sendAsync(final Message query, final ResolverListener listener) {
	public Object sendAsync(final Message query, final ResponseQueue queue) {
		Object id = new Integer(idCount++);
		sendAsync(query, id, queue);
		return id;
	}

	public Object sendAsync(final Message query, final Object id,
			final ResponseQueue queue) {
		Resolution res = new Resolution(this, query);
		res.startAsync(queue, id);
		return res;
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
