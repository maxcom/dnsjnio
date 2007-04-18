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

import java.nio.channels.DatagramChannel;
import java.net.InetSocketAddress;

/**
 * Single port UDP connection.
 * This class reuses the same port.
 */
public class SinglePortUDPConnection extends UDPConnection {
    private int port;
    public SinglePortUDPConnection(ConnectionListener listener, int port) {
        super(listener, SINGLE_PORT_BUFFER_SIZE);
        this.port = port;
    }
    protected void connect() {
        try {
            DatagramChannel sch = DatagramChannel.open();
            sch.configureBlocking(false);
            sch.socket().setReuseAddress(true);
            InetSocketAddress remote = new InetSocketAddress(getHost(), getPort());
            sk = sch.register(DnsController.getSelector(),0);
            sch.connect(remote);
            attach(sk);
        } catch(Exception e) {
            e.printStackTrace();
            close();
        }
    }
}
