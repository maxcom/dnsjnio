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

import junit.framework.TestCase;
import org.xbill.DNS.*;

/**
 *
 * Test that we can connect to a real server...
 */
public class RemoteServerTest extends TestCase {
    public final static String REAL_SERVER = "ns0.validation-test-servers.nominet.org.uk.";
//    public final static String REAL_QUERY_NAME = "dnsjnio-1-0-3-test1.validation-test-servers.nominet.org.uk.";
    public final static String REAL_QUERY_NAME = "dnsjnio-1-0-1-test1.validation-test-servers.nominet.org.uk.";

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
