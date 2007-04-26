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

import org.xbill.DNS.*;

import java.io.IOException;
import java.net.*;
import java.util.Random;

/**
 * This class needs to listen for requests from the test code.
 * It should send back replies for "example...", and fail any others.
 */
public class TestServer extends Thread {
    final static int PORT = 34916;
    ServerSocket tcpSocket;
    DatagramSocket udpSocket;
    final static int NUM_UDP_THREADS = 600;
    final static int NUM_TCP_THREADS = 2;
    UdpResponder[] udpServers; // = new UdpResponder[NUM_UDP_THREADS];
    TcpResponder[] tcpServers; // = new TcpResponder[NUM_TCP_THREADS];
    Random random = new Random();
    boolean serverStarted = false;
    static boolean serverRunning = false;
    static TestServer server;

    public static void main(String[] args) {
        startServer();
    }
    
    public static TestServer startServer() {
    	if (!serverRunning) {
    		serverRunning = true;
    	server = startServer(PORT, NUM_UDP_THREADS, NUM_TCP_THREADS);
    	}
    	return server;
    }
    
    public static void stopServer() {
    	if (serverRunning) {
    		server.stopRunning();
    		serverRunning = false;
    	}
    }

    public static TestServer startServer(int port, int numUdpThreads, int numTcpThreads) {
        // Fire up a thread for tcp, and a load of threads for udp.
        // They'll read the sockets, and the TCP thread will fire up new threads to answer queries.
//        if (tcpServers[0] == null) {
    	TestServer server = new TestServer();
    	server.kickoff(port, numUdpThreads, numTcpThreads);
    	return server;
//        }
//        }
    }
    
    private void kickoff(int port, int numUdpThreads, int numTcpThreads) {
    	if (!serverStarted) {
    		serverStarted = true;
        	tcpServers = new TcpResponder[numTcpThreads];
            try {
                tcpSocket = new ServerSocket(port);
            }
            catch (IOException e) {
                printMsg("Cannot create server socket " +
                        "on port:  " + port + ".  Exiting...");
                System.exit(0);
            }
            for (int i = 0; i < numTcpThreads; i++) {
                tcpServers[i] = new TcpResponder(tcpSocket, this);
                tcpServers[i].start();
            }
//        }
//        if (udpServers[0] == null) {
        	udpServers = new UdpResponder[numUdpThreads];
            try {
                udpSocket = new DatagramSocket(port);
            }
            catch (SocketException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < numUdpThreads; i++) {
                udpServers[i] = new UdpResponder(udpSocket, this);
                udpServers[i].start();
            }
    	}    	
    }
    
    public void stopRunning() {
    	for (int i = 0; i < udpServers.length; i++) {
    		udpServers[i].stopRunning();
    	}
    	for (int i = 0; i < tcpServers.length; i++) {
    		tcpServers[i].stopRunning();
    	}
    }

    public void printMsg(String msg) {
        System.out.println(msg);
    }

    public Message formResponse(Message query) throws UnknownHostException, TextParseException {
        try {
            sleep(random.nextInt(500));
        }
        catch (Exception e) {}
        Message response = new Message();
        response.getHeader().setID(query.getHeader().getID());
        if (query.getQuestion().getName().toString().startsWith("example")) {
            response.addRecord(query.getQuestion(), 0);
            response.getHeader().setRcode(Rcode.NOERROR);
            // Add A record to reply
            Record newRec = new ARecord(query.getQuestion().getName(), 1, 3600, InetAddress.getLocalHost());
            response.addRecord(newRec, 1);
            Record nsRec = new NSRecord(query.getQuestion().getName(), 1, 3600, Name.fromString("example.com."));
            response.addRecord(nsRec, 2);
        } else if (query.getQuestion().getName().toString().startsWith("timeout")) {
            try {
                sleep(2100);
            }
            catch (InterruptedException e) {
            }
            return null;
        } else {
            response.addRecord(query.getQuestion(), 0);
            response.getHeader().setRcode(Rcode.NXDOMAIN);
            // Add SOA record to reply
            Record soaRec = new SOARecord(query.getQuestion().getName(), 1, 3600, Name.fromString("example.com."),
                    Name.fromString("example.com."), 1136992949, 1800, 900, 604800, 900);
            response.addRecord(soaRec, 1);
        }
        return response;
    }
}
