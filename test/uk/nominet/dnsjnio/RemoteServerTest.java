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

import junit.framework.TestCase;
import org.xbill.DNS.*;

/**
 *
 * Test that we can connect to a real server...
 */
public class RemoteServerTest extends TestCase {
    public final static String REAL_SERVER = "ns0.validation-test-servers.nominet.org.uk.";
    public final static String REAL_QUERY_NAME = "dnsjnio-0-9-6-test1.validation-test-servers.nominet.org.uk.";

    public RemoteServerTest(String arg0) {
        super(arg0);
    }

    public void testRemoteServer() throws Exception {
        Resolver resolver = new NonblockingResolver(REAL_SERVER);
        resolver.setPort(53);

        Name name = Name.fromString(REAL_QUERY_NAME, Name.root);
        Record question = Record.newRecord(name, Type.TXT, DClass.ANY);
        Message query = Message.newQuery(question);
        Message response = resolver.send(query);
        checkResponse(response);
    }

    public void testRemoteServerTcp() throws Exception {
        Resolver resolver = new NonblockingResolver(REAL_SERVER);
        resolver.setTCP(true);

        Name name = Name.fromString(REAL_QUERY_NAME, Name.root);
        Record question = Record.newRecord(name, Type.TXT, DClass.ANY);
        Message query = Message.newQuery(question);
        Message response = resolver.send(query);
        checkResponse(response);
    }

    private void checkResponse(Message response) throws Exception {
        assertTrue(response.getRcode() == Rcode.NOERROR);
        String rdata = response.getSectionArray(1)[0].rdataToString();
        assertTrue(rdata, rdata.equals("\"Ok\""));
    }

}
