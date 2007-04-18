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

import org.xbill.DNS.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

public class UdpResponder extends Thread {
    DatagramSocket socket;
    private static int idCount = 0;
    private int id = 0;
    private Random random = new Random();

    public UdpResponder(DatagramSocket s) {
        socket = s;
        id = idCount++;
    }

    public void run() {
        while (true) {
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
        int client_port = packet.getPort();
        byte[] bytes = packet.getData();

        if (bytes != null) {
            try {
                Message query = new Message(bytes);
//                    printMsg(query.toString());

                Message response = TestServer.formResponse(query);
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
                TestServer.printMsg("Can't get Message from input!" + bytes);
                e.printStackTrace();
            }
        }
    }
}
