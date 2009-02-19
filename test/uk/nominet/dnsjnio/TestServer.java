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

import org.xbill.DNS.*;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * This class needs to listen for requests from the test code.
 * It should send back replies for "example...", and fail any others.
 */
public class TestServer extends Thread {
    final static int PORT = 34916;
    ServerSocket tcpSocket;
    DatagramSocket udpSocket;
    final static int NUM_UDP_THREADS = 250;
    final static int NUM_TCP_THREADS = 50;
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
    	stopServer();
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
//                udpSocket.setSoTimeout(20);
            }
            catch (SocketException e) {
                e.printStackTrace();
                System.exit(0);
            }
            for (int i = 0; i < numUdpThreads; i++) {
                udpServers[i] = new UdpResponder(udpSocket, this);
                udpServers[i].start();
            }
    	}    	
    }
    
    public void stopRunning() {
        if (serverStarted) {
    	for (int i = 0; i < udpServers.length; i++) {
    		udpServers[i].stopRunning();
    	}
    	for (int i = 0; i < tcpServers.length; i++) {
    		tcpServers[i].stopRunning();
    	}
        serverStarted = false;
        udpSocket.close();
        try {
        tcpSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing TCP socket " + e);
        }
        }
    }

    public void printMsg(String msg) {
        System.out.println(msg);
    }

    public Message formResponse(Message query, int port) throws UnknownHostException, TextParseException {
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
            // Add a record with the query source port number
            List txt = new LinkedList();
            txt.add(String.valueOf(port));
            Record portRec = new TXTRecord(query.getQuestion().getName(), 1, 3600, txt);
            response.addRecord(portRec, 2);
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
