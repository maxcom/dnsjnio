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

import org.xbill.DNS.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpResponder extends Thread {
	InputStream is = null;

	OutputStream output;

	static ServerSocket serverSocket;

	Socket clientSocket;

	TestServer server;

	private boolean keepRunning = true;

	public TcpResponder(ServerSocket s, TestServer server) {
		serverSocket = s;
		this.server = server;
	}

	public void stopRunning() {
		keepRunning = false;
		interrupt();
	}

	public void run() {
		while (keepRunning) {
			processClientRequest();
		}
	}

	private void processClientRequest() {
//		while (true) {
			try {
				// @todo Should run these as thread pool, started by single
				// accept() call.
				clientSocket = serverSocket.accept();
				output = clientSocket.getOutputStream();
				is = clientSocket.getInputStream();
			} catch (Exception e) {
                cleanup();
                return;
			}
            
			while (true) {
				byte[] inputBytes;
				try {
					int length0 = is.read();
					if (length0 == -1) {
						cleanup();
						break;
					}
					int length1 = is.read();
					if (length1 == -1) {
						cleanup();
						break;
					}
					int length = ((length0 & 0xFF) << 8) + (length1 & 0xFF);
					inputBytes = new byte[length];
					int actualLength = is.read(inputBytes);
					if (actualLength == -1) {
						cleanup();
						break;
					}
					if (actualLength != length) {
						System.out
								.println("TcpResponder : Wrong number of bytes in message! Expected "
										+ length + ", got " + actualLength);
					}
				} catch (IOException e) {
					e.printStackTrace();
					server.printMsg("I/O error while reading socket.");
					cleanup();
					return;
				}
				// Parse DNS query, and send appropriate response
				if (inputBytes != null) {
					try {
						Message query = new Message(inputBytes);
						// System.out.println("TcpResponder : Received query id
						// = " + query.getHeader().getID());
						Message response = server.formResponse(query, clientSocket.getPort());

						if (response != null) {
							byte[] bytes = response.toWire(Message.MAXLENGTH);
							byte[] ret = new byte[bytes.length + 2];
							System.arraycopy(bytes, 0, ret, 2, bytes.length);
							ret[0] = (byte) (bytes.length >>> 8);
							ret[1] = (byte) (bytes.length & 0xFF);

							// System.out.println("TcpResponder : Sending
							// response (len " + response.toWire().length + "
							// bytes), id=" + response.getHeader().getID());
							output.write(ret);
						}
					} catch (IOException e) {
						server
								.printMsg("TcpResponder : Can't get Message from input!"
										+ inputBytes);
						e.printStackTrace();
					}
				}
				// cleanup();
			}
//		}
	}

	private void cleanup() {
		try {
			if (output != null) {
				output.close();
			}
			if (is != null)
				is.close();
			if (clientSocket != null) {
				clientSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			server.printMsg("I/O error while closing connections.");
		}
	}
}
