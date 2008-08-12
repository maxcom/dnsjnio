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
import org.xbill.DNS.security.*;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.security.interfaces.*;

public class TsigTest extends TestCase {
	// public void testTsig() throws Exception {
	//
	// Name name = Name.fromString("example.com.");
	// int fudge = 36000;
	// Options.set("tsigfudge", String.valueOf(fudge));
	// Name alg = Name.fromString("HMAC-MD5.SIG-ALG.REG.INT.");
	// // byte[] key = base64.fromString("1234");
	// byte[] key = new byte[4];
	// key[0] = '1';
	// key[1] = '2';
	// key[2] = '3';
	// key[3] = '4';
	// System.out.println(String.valueOf(key)); // key.toString());
	// // Date timeSigned = new Date(100000);
	// // int error = 0;
	//
	// TSIG tsig = new TSIG(alg, name, key);
	// Message m = new Message();
	// m.getHeader().setID(1234);
	// // byte[] empty = new byte[0];
	// // TSIGRecord old = new TSIGRecord(name, DClass.ANY,
	// // (long) 0, alg, timeSigned, fudge, empty, 0, error, empty);
	// tsig.apply(m, null);
	// System.out.println(m);
	//
	// System.out.println("TSIG : "
	// + base64.toString(m.getTSIG().getSignature()));
	//
	// }

	public void testDnssec() throws Exception {
		Message ret = sendQuery("bigzone.uk-dnssec.nic.uk.");
//		System.out.println(ret);

		Record rr = ret.getSectionArray(1)[0];

//		System.out.println(rr);
//		System.out.println(((DNSKEYRecord) (rr)).getFootprint());
		Record rr2 = ret.getSectionArray(1)[1];
//		System.out.println(rr2);
//		System.out.println(((DNSKEYRecord) (rr2)).getFootprint());

		// Add the keys to a key cache,
		Cache cache = new Cache();
		cache.addMessage(ret);
		// And then use that to verify the NSEC RRSet
		DNSSECVerifier v = new DNSSECVerifier();
		v.addTrustedKey((DNSKEYRecord) rr);
		v.addTrustedKey((DNSKEYRecord) rr2);

		ret = sendQuery("aaa.bigzone.uk-dnssec.nic.uk.");
		RRset[] rrsets = ret.getSectionRRsets(2);
		RRset rrset = rrsets[1];
//		System.out.println(rrset);
//		System.out.println("rrset length = " + rrset.size());
		
//		System.out.println(ret);

		if (v.verify(rrset, cache) == DNSSEC.Secure)
			System.out.println("VERIFIED!!!");
		else
			System.out.println("FAILED!!!");

		Iterator sigs = rrset.sigs();
		RRSIGRecord sigrec = (RRSIGRecord) sigs.next();		

		byte [] data = DNSSEC.digestRRset(sigrec, rrset);
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
			buf.append(data[i]);
			buf.append(",");
		}
//		System.out.println(buf);
		
		
		// @TODO@ Can we print out key?!
		RSAPublicKey pub_key = (RSAPublicKey)(KEYConverter.parseRecord((DNSKEYRecord)rr2));
//		System.out.println(pub_key.getModulus());
//		System.out.println(pub_key.getPublicExponent());
//		System.out.println(pub_key.getEncoded());
}

	private Message sendQuery(String name) throws UnknownHostException,
			TextParseException, IOException {
		Resolver res = new SimpleResolver("dnssec.nominet.org.uk");
		Record rec = Record.newRecord(Name.fromString(name), Type.DNSKEY,
				DClass.IN);
		Message query = Message.newQuery(rec);
		// Do we need to add any flags?
		query.getHeader().setFlag(Flags.CD);
		query.getHeader().setFlag(Flags.RD);
		// Set the UDP payload size
		OPTRecord opt = new OPTRecord(1220, (byte) 0, (byte) 0,
				ExtendedFlags.DO);
		if (opt != null)
			query.addRecord(opt, Section.ADDITIONAL);
		Message ret = res.send(query);
		return ret;
	}
}
