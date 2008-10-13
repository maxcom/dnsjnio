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

import junit.framework.TestCase;

public class CompressTest extends TestCase {
	public void testCompression() throws Exception {
       NonblockingResolver res = new NonblockingResolver();
       Name name = Name.fromString("000.COM", Name.root);
       Record question = Record.newRecord(name, Type.CNAME, DClass.ANY);
       Message query = Message.newQuery(question);

       try {
       Message response = res.send(query);
       }
       catch (Exception e) {
    	   fail();
       }
	}
	
	public void testCompressionTCP() throws Exception {
		NonblockingResolver res = new NonblockingResolver();
		res.setTCP(true);
        Name name = Name.fromString("000.COM", Name.root);
        Record question = Record.newRecord(name, Type.CNAME, DClass.ANY);
        Message query = Message.newQuery(question);

        try {
            res.send(query);
            } 
            catch (Exception e) {
       	     fail();
            }
	}	
}