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

import java.nio.channels.DatagramChannel;

/**
 * Single port UDP connection.
 * This class reuses the same port.
 */
public class SinglePortUDPConnection extends UDPConnection {
    public SinglePortUDPConnection(ConnectionListener listener, int port) {
        super(listener, SINGLE_PORT_BUFFER_SIZE);
    }
    protected void connect() {
        try {
            DatagramChannel sch = DatagramChannel.open();
            sch.configureBlocking(false);
            sch.socket().setReuseAddress(true);
        	sch.socket().bind(localAddress);
            sk = sch.register(DnsController.getSelector(),0);
            sch.connect(remoteAddress);
            attach(sk);
        } catch(Exception e) {
            e.printStackTrace();
            close();
        }
    }
}
