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
