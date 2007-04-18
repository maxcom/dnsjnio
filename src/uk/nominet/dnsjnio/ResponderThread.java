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
import org.xbill.DNS.ResolverListener;

/**
 * This class is used when a NonblockingResolver is used with
 * the old sendAsync(...ResolverListener) method.
 */
public class ResponderThread extends Thread {
    Object id;
    Message response;
    ResolverListener listener;
    Exception e;
    public ResponderThread(ResolverListener listener, Object id, Message response) {
        this.listener = listener;
        this.id = id;
        this.response = response;
    }
    public ResponderThread(ResolverListener listener, Object id, Exception e) {
        this.listener = listener;
        this.id = id;
        this.e = e;
    }
    public void run() {
        if (response != null) {
            listener.receiveMessage(id, response);
        }
        else {
            listener.handleException(id, e);
        }
    }
}
