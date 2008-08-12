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

package org.xbill.DNS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import uk.nominet.dnsjnio.QueryData;
import uk.nominet.dnsjnio.Response;
import uk.nominet.dnsjnio.ResponseQueue;
import uk.nominet.dnsjnio.SinglePortTransactionController;
import uk.nominet.dnsjnio.Transaction;

/**
 * A nonblocking implementation of Resolver. Multiple concurrent sendAsync
 * queries can be run without increasing the number of threads.
 * 
 * @todo AXFR?
 * 
 */
public class NonblockingResolver implements INonblockingResolver {

	/** The default port to send queries to */
	public static final int DEFAULT_PORT = 53;

	private InetSocketAddress remoteAddress = new InetSocketAddress(
			DEFAULT_PORT);

	private boolean useTCP = false, ignoreTruncation;

	// private byte EDNSlevel = -1;
	private TSIG tsig;

	private int timeoutValue = 10 * 1000;

	/** The default EDNS payload size */
	public static final int DEFAULT_EDNS_PAYLOADSIZE = 1280;

	private static final short DEFAULT_UDPSIZE = 512;

	private OPTRecord queryOPT;

	private static String defaultResolver = "localhost";

	// Use short as id because the id header is limited to 16 bit
	// From RFC1035 4.1.1. Header section format :
	// 
	// ID A 16 bit identifier assigned by the program that
	// generates any kind of query. This identifier is copied
	// the corresponding reply and can be used by the requester
	// to match up replies to outstanding queries.
	//
	private static short uniqueID = 0;

	private SinglePortTransactionController transactionController;

	final private boolean useSinglePort = false;

	private InetSocketAddress localAddress = new InetSocketAddress(0); // use
																		// random
																		// port
																		// by
																		// default

	/**
	 * Creates a SimpleResolver that will query the specified host
	 * 
	 * @exception UnknownHostException
	 *                Failure occurred while finding the host
	 */
	public NonblockingResolver(String hostname) throws UnknownHostException {
		if (hostname == null) {
			hostname = ResolverConfig.getCurrentConfig().server();
			if (hostname == null)
				hostname = defaultResolver;
		}
		InetAddress addr;
		if (hostname.equals("0"))
			addr = InetAddress.getLocalHost();
		else
			addr = InetAddress.getByName(hostname);
		remoteAddress = new InetSocketAddress(addr, DEFAULT_PORT);
		// @todo@ Provide overloaded constructors to take local adress/port?
		transactionController = new SinglePortTransactionController(
				remoteAddress, localAddress);
	}

	/**
	 * Creates a SimpleResolver. The host to query is either found by using
	 * ResolverConfig, or the default host is used.
	 * 
	 * @see ResolverConfig
	 * @exception UnknownHostException
	 *                Failure occurred while finding the host
	 */
	public NonblockingResolver() throws UnknownHostException {
		this(null);
	}

	InetSocketAddress getRemoteAddress() {
		return remoteAddress;
	}

	/** Sets the default host (initially localhost) to query */
	public static void setDefaultResolver(String hostname) {
		defaultResolver = hostname;
	}

	/**
	 * Sets the address of the server to communicate with.
	 * 
	 * @param remoteAddress
	 *            The address of the DNS server
	 */
	public void setRemoteAddress(InetSocketAddress addr) {
		remoteAddress = addr;
		transactionController.setRemoteAddress(remoteAddress);
	}

	/**
	 * Sets the address of the server to communicate with (on the default DNS
	 * port)
	 * 
	 * @param remoteAddress
	 *            The address of the DNS server
	 */
	public void setRemoteAddress(InetAddress addr) {
		remoteAddress = new InetSocketAddress(addr, remoteAddress.getPort());
		transactionController.setRemoteAddress(remoteAddress);
	}

	/**
	 * Sets the server port to communicate on.
	 * 
	 * @param port
	 *            The server DNS port
	 */
	public void setRemotePort(int port) {
		remoteAddress = new InetSocketAddress(remoteAddress.getAddress(), port);
		transactionController.setRemoteAddress(remoteAddress);
	}

	/**
	 * Sets the local address to bind to when sending messages. If useSinglePort
	 * is false then random ports will be used.
	 * 
	 * @param addr
	 *            The local address to send messages from.
	 */
	public void setLocalAddress(InetSocketAddress addr) {
		localAddress = addr;
		transactionController.setLocalAddress(localAddress);
	}

	/**
	 * Sets the local address to bind to when sending messages. A random port
	 * will be used.
	 * 
	 * @param addr
	 *            The local address to send messages from.
	 */
	public void setLocalAddress(InetAddress addr) {
		localAddress = new InetSocketAddress(addr, 0);
		transactionController.setLocalAddress(localAddress);
	}

	/**
	 * Sets the local port to bind to when sending messages. A random port will
	 * be used if useSinglePort is false.
	 * 
	 * @param port
	 *            The local port to send messages from.
	 */
	public void setLocalPort(int port) {
		localAddress = new InetSocketAddress(localAddress.getHostName(), port);
		transactionController.setLocalAddress(localAddress);
	}

	/**
	 * Sets the server DNS port
	 * 
	 * @param port
	 *            the server port
	 */
	public void setPort(int port) {
		setRemotePort(port);
	}

	/**
	 * Get the address we're sending queries from
	 * 
	 * @return the local address
	 */
	public InetSocketAddress getLocalAddress() {
		return localAddress;
	}

	public void setTCP(boolean flag) {
		this.useTCP = flag;
	}

	public boolean isTCP() {
		return useTCP;
	}

	public void setIgnoreTruncation(boolean flag) {
		this.ignoreTruncation = flag;
	}

	// THESE METHODS HAVE BEEN REMOVED : SEE http://www.us-cert.gov/cas/techalerts/TA08-190B.html
//	/**
//	 * Set single port mode on or off
//	 * 
//	 * @param useSamePort
//	 *            should same port be used for all the queries?
//	 */
//	public void setSinglePort(boolean useSamePort) {
//		this.useSinglePort = useSamePort;
//	}
//
//	/**
//	 * In single port mode?
//	 * 
//	 * @return true if a single port should be used for all queries
//	 */
//	public boolean isSinglePort() {
//		return useSinglePort;
//	}

	public void setEDNS(int level, int payloadSize, int flags, List options) {
		if (level != 0 && level != -1)
			throw new IllegalArgumentException("invalid EDNS level - "
					+ "must be 0 or -1");
		if (payloadSize == 0)
			payloadSize = DEFAULT_EDNS_PAYLOADSIZE;
		queryOPT = new OPTRecord(payloadSize, 0, level, flags, options);
	}

	public void setEDNS(int level) {
		setEDNS(level, 0, 0, null);
	}

	private void applyEDNS(Message query) {
		if (queryOPT == null || query.getOPT() != null)
			return;
		query.addRecord(queryOPT, Section.ADDITIONAL);
	}

	public void setTSIGKey(TSIG key) {
		tsig = key;
	}

	public void setTSIGKey(Name name, byte[] key) {
		tsig = new TSIG(name, key);
	}

	public void setTSIGKey(String name, String key) {
		tsig = new TSIG(name, key);
	}

	protected TSIG getTSIGKey() {
		return tsig;
	}

	public void setTimeout(int secs) {
		setTimeout(secs, 0);
	}

	public void setTimeout(int secs, int millisecs) {
		timeoutValue = (secs * 1000) + millisecs;
	}

	int getTimeout() {
		return timeoutValue / 1000;
	}

	private int maxUDPSize(Message query) {
		OPTRecord opt = query.getOPT();
		if (opt == null)
			return DEFAULT_UDPSIZE;
		else
			return opt.getPayloadSize();
	}

	/**
	 * Sends a message to a single server and waits for a response. No checking
	 * is done to ensure that the response is associated with the query (other
	 * than checking that the DNS packet IDs are equal)
	 * 
	 * @param query
	 *            The query to send.
	 * @return The response.
	 * @throws IOException
	 *             An error occurred while sending or receiving.
	 */
	public Message send(Message query) throws IOException {

		ResponseQueue queue = new ResponseQueue();
		Object id = sendAsync(query, queue);
		Response response = queue.getItem();
		if (response.getId() != id) {
			throw new IllegalStateException("Wrong id (" + response.getId()
					+ ", should be " + id + ") returned from sendAsync()!");
		}
		if (response.isException()) {
			if (response.getException() instanceof SocketTimeoutException) {
				throw new SocketTimeoutException();
			} else if (response.getException() instanceof IOException) {
				throw (IOException) (response.getException());
			} else {
				throw new IllegalStateException("Unexpected exception!\r\n"
						+ response.getException().toString());
			}
		}
		return response.getMessage();
	}

	/**
	 * Old-style interface
	 * 
	 * @param message
	 *            message to send
	 * @param resolverListener
	 *            object to call back
	 * @return id of the query
	 */
	public Object sendAsync(Message message, ResolverListener resolverListener) {
		// If this method is called, then the Transaction should fire up a new
		// thread, and use it to
		// call the client back.
		// If not this method, then the Transaction should use the standard
		// behaviour of inserting
		// the response in to the client-supplied ResponseQueue.
		final Object id;
		synchronized (this) {
			id = new Integer(uniqueID++);
		}
		sendAsync(message, id, resolverListener);
		return id;
	}

	/**
	 * Old-style interface
	 * 
	 * @param message
	 *            message to send
	 * @param resolverListener
	 *            object to call back
	 */
	public void sendAsync(Message message, Object id,
			ResolverListener resolverListener) {
		sendAsync(message, id, getTimeout(), useTCP, null, false,
				resolverListener);
	}

	/**
	 * Asynchronously sends a message to a single nameserver, registering a
	 * ResponseQueue to buffer responses on success or exception. Multiple
	 * asynchronous lookups can be performed in parallel.
	 * 
	 * @param query
	 *            The query to send
	 * @param responseQueue
	 *            the queue for the responses
	 * @return An identifier, which is also a data member of the Response
	 */
	public Object sendAsync(final Message query,
			final ResponseQueue responseQueue) {
		final Object id;
		synchronized (this) {
			id = new Integer(uniqueID++);
		}
		sendAsync(query, id, responseQueue);
		return id;
	}

	/**
	 * Add the query to the queue for the NonblockingResolverEngine
	 * 
	 * @param query
	 *            The query to send
	 * @param id
	 *            The object to be used as the id in the callback
	 * @param responseQueue
	 *            The queue for the responses
	 */
	public void sendAsync(final Message query, Object id,
			final ResponseQueue responseQueue) {
		sendAsync(query, id, getTimeout(), useTCP, responseQueue);
	}

	public void sendAsync(final Message inQuery, Object id, int inQueryTimeout,
			boolean queryUseTCP, final ResponseQueue responseQueue) {
		sendAsync(inQuery, id, inQueryTimeout, queryUseTCP, responseQueue,
				true, null);
	}

	private void sendAsync(final Message inQuery, Object id,
			int inQueryTimeout, boolean queryUseTCP,
			final ResponseQueue responseQueue, boolean useResponseQueue,
			ResolverListener listener) {
		if (!useResponseQueue && (listener == null)) {
			throw new IllegalArgumentException(
					"No ResolverListener supplied for callback when useResponsequeue = true!");
		}

		if (Options.check("verbose"))
			System.err.println("Sending to " + remoteAddress.getAddress()
					+ ", from " + remoteAddress.getAddress());

		if (inQuery.getHeader().getOpcode() == Opcode.QUERY) {
			Record question = inQuery.getQuestion();
			if (question != null && question.getType() == Type.AXFR) {
				throw new UnsupportedOperationException(
						"AXFR not implemented in NonblockingResolver");
				// return sendAXFR(query); // @TODO@ SORT OUT TRANSFER!
			}
		}

		int queryTimeout = 1000 * inQueryTimeout;
		Message query = (Message) inQuery.clone();
		applyEDNS(query);
		if (tsig != null)
			tsig.apply(query, null);

		byte[] out = query.toWire(Message.MAXLENGTH);
		int udpSize = maxUDPSize(query);
		boolean tcp = false;
		long endTime = System.currentTimeMillis() + queryTimeout;

		if (queryUseTCP || out.length > udpSize) {
			tcp = true;
		}

		// Send the query to the nioEngine.

		// If !useResponseQueue, then the Transaction should fire up a new
		// thread, and use it to
		// call the client back.
		// If useResponseQueue, then the Transaction should use the standard
		// behaviour of inserting
		// the response in to the client-supplied ResponseQueue.

		// Use SinglePortTransactionController if possible, otherwise get new
		// Transaction.
		if (useSinglePort
				&& transactionController.headerIdNotInUse(query.getHeader()
						.getID())) {
			QueryData qData = new QueryData();
			qData.setTcp(tcp);
			qData.setIgnoreTruncation(ignoreTruncation);
			qData.setTsig(tsig);
			qData.setQuery(query);
			if (!tcp) {
				qData.setUdpSize(udpSize);
			}
			if (useResponseQueue) {
				transactionController.sendQuery(qData, id, responseQueue,
						endTime);
			} else {
				// Start up the Transaction with a ResolverListener
				transactionController.sendQuery(qData, id, listener, endTime);
			}
		} else {
			// InetSocketAddress sa = new InetSocketAddress(remoteAddress,
			// port);
			// Use random port
			InetSocketAddress localAddr = new InetSocketAddress(localAddress
					.getAddress(), 0);
			Transaction transaction = new Transaction(remoteAddress, localAddr,
					tsig, tcp, ignoreTruncation);
			if (!tcp) {
				transaction.setUdpSize(udpSize);
			}
			if (useResponseQueue) {
				transaction.sendQuery(query, id, responseQueue, endTime);
			} else {
				// Start up the Transaction with a ResolverListener
				transaction.sendQuery(query, id, listener, endTime);
			}
		}
	}

	// private Message
	// sendAXFR(Message query) throws IOException {
	// Name qname = query.getQuestion().getName();
	// ZoneTransferIn xfrin = ZoneTransferIn.newAXFR(qname, remoteAddress,
	// tsig);
	// try {
	// xfrin.run();
	// }
	// catch (ZoneTransferException e) {
	// throw new WireParseException(e.getMessage());
	// }
	// List records = xfrin.getAXFR();
	// Message response = new Message(query.getHeader().getID());
	// response.getHeader().setFlag(Flags.AA);
	// response.getHeader().setFlag(Flags.QR);
	// response.addRecord(query.getQuestion(), Section.QUESTION);
	// Iterator it = records.iterator();
	// while (it.hasNext())
	// response.addRecord((Record)it.next(), Section.ANSWER);
	// return response;
	// }

	public static Message parseMessage(byte[] b) throws WireParseException {
		try {
			return (new Message(b));
		} catch (IOException e) {
			if (Options.check("verbose"))
				e.printStackTrace();
			if (!(e instanceof WireParseException))
				e = new WireParseException("Error parsing message");
			throw (WireParseException) e;
		}
	}

	public static void verifyTSIG(Message query, Message response, byte[] b,
			TSIG tsig) {
		if (tsig == null)
			return;
		int error = tsig.verify(response, b, query.getTSIG());
		if (error == Rcode.NOERROR)
			response.tsigState = Message.TSIG_VERIFIED;
		else
			response.tsigState = Message.TSIG_FAILED;
		if (Options.check("verbose"))
			System.err.println("TSIG verify: " + Rcode.string(error));
	}

	/**
	 * Called by the Connection to check if the data received so far constitutes
	 * a complete packet.
	 * 
	 * @param in
	 * @return true if the packet is complete
	 */
	public static boolean isDataComplete(byte[] in) {
		// Match up the returned qData with the QueryDataList
		// Make sure that the header ID is correct for the header in qData
		// If it matches qData, that's great.
		// If it doesn't, then we need to find the queryData which *does* match
		// it.
		// We then close that connection.
		try {
			if (in.length < Header.LENGTH) {
				return false;
			}
			Message message = parseMessage(in);
			int messLen = message.numBytes();
			boolean ready = (messLen == in.length);
			return (ready);
		} catch (IOException e) {
			return false;
		}
	}
}
