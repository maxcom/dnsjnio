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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;

public class UdpResponder extends Thread {
    DatagramSocket socket;
    private static int idCount = 0;
    private int id = 0;
    private Random random = new Random();
    private TestServer server;
    private boolean keepRunning = true;

    public UdpResponder(DatagramSocket s, TestServer server) {
        socket = s;
        id = idCount++;
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

    private static int returned = 0;
    private void processClientRequest() {
        byte [] buffer = new byte[1024];
        DatagramPacket packet = null;
        try {
            // Create an empty Datagram Packet
            packet = new DatagramPacket(buffer, buffer.length);

            // receive request from client and get client info
            socket.receive(packet);
        }
        catch (UnknownHostException e) {
            System.out.println(e);
        }
        catch (IOException e) {
            System.out.println(e);
        }

        InetAddress client = packet.getAddress();
        InetSocketAddress sa = (InetSocketAddress)(packet.getSocketAddress());
        int client_port = sa.getPort();
        byte[] bytes = packet.getData();

        if (bytes != null) {
            try {
                Message query = new Message(bytes);
//                    printMsg(query.toString());

                Message response = server.formResponse(query, client_port);
                if (response != null) {

                    try {
                    Thread.sleep(random.nextInt(1000));
                    } catch (InterruptedException e) {}

                    byte[] ret = response.toWire(Message.MAXLENGTH);

                    DatagramPacket replyPacket = new DatagramPacket(ret, ret.length, client, client_port);
                    socket.send(replyPacket);
//                    System.out.println("Num " + returned++ + ", id = " + query.getHeader().getID() + ", sent to " + client_port);
                }
            }
            catch (IOException e) {
                server.printMsg("Can't get Message from input!" + bytes);
                e.printStackTrace();
            }
        }
    }
}
