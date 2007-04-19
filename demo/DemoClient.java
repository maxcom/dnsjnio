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

import org.xbill.DNS.*;
import uk.nominet.dnsjnio.Response;
import uk.nominet.dnsjnio.ResponseQueue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * This class acts as a simple demo for the dnsjnio extension.
 * It loads the file to_resolve.txt, and tries to resolve each
 * of the names. Instead of a thread (and socket) for each
 * query, which dnsjava would do, the dnsjnio extension runs
 * all the queries in a single thread, over a single socket.
 */
public class DemoClient {
    final String filename = "to_resolve.txt";
    NonblockingResolver resolver;

    public static void main(String[] args) throws Exception {
        DemoClient demo = new DemoClient();
        demo.demo(args);
    }

    public DemoClient() {
    }

    public void demo(String[] args) throws Exception {
        String name = filename;
        if (args.length == 1) {
            name = args[0];
        }
        ArrayList toResolve = loadFile(name);
        resolver = new NonblockingResolver();
        resolver.setTimeout(10);
        resolver.setTCP(true);
        ResponseQueue responseQueue = new ResponseQueue();
        // Send all the queries asynchronously
        for (int i = 0; i < toResolve.size(); i++) {
            String nextName = (String) (toResolve.get(i));
//            System.out.println("Querying for " + nextName);
            Integer id = new Integer(i);
            resolver.sendAsync(makeQuery(nextName, i), id, responseQueue);
        }
        System.out.println("Sent " + toResolve.size() + " queries");
        // Now receive all the queries
        int goodCount = 0;
        int errorCount = 0;
        for (int i = 0; i < toResolve.size(); i++) {
            Response response = responseQueue.getItem();
            if (response.isException()) {
//                System.out.println("Got exception from " + toResolve.get(i) + ", error : " + response.getException().getMessage());
                errorCount++;
            } else {
//                System.out.println(toResolve.get(i) + " resolves to " + response.getMessage().getSectionRRsets(Section.ANSWER));
                goodCount++;
            }
        }
        System.out.println("Received " + goodCount + " responses, and " + errorCount + " errors (most likely timeouts)");
        if (errorCount + goodCount < toResolve.size()) {
            System.out.println("ERROR : " + (toResolve.size() - (errorCount + goodCount)) + " queries did not return!!");
        }
    }

    private Message makeQuery(String nameString, int id) throws TextParseException {
        Name name = Name.fromString(nameString, Name.root);
        Record question = Record.newRecord(name, Type.A, DClass.ANY);
        Message query = Message.newQuery(question);
        query.getHeader().setID(id);
        return query;
    }

    public ArrayList loadFile(String fileName) throws Exception {
        if ((fileName == null) || (fileName == ""))
            throw new IllegalArgumentException();

        String line;
        ArrayList fileList = new ArrayList();

            BufferedReader in = new BufferedReader(new FileReader(fileName));

            if (!in.ready())
                throw new IOException();

            while ((line = in.readLine()) != null) fileList.add(line);

            in.close();
        return fileList;
    }
}
