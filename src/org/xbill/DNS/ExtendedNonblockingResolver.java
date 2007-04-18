package org.xbill.DNS;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
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
public class ExtendedNonblockingResolver extends ExtendedResolver {
    protected List resolvers;
    protected static final int quantum = 5;
    protected void
    init() {
        resolvers = new ArrayList();
    }
    /**
     * Creates a new Extended Resolver.  The default ResolverConfig is used to
     * determine the servers for which NonblockingResolver contexts should be
     * initialized.
     * @see NonblockingResolver
     * @see ResolverConfig
     * @exception java.net.UnknownHostException Failure occured initializing NonblockingResolvers
     */
    public
    ExtendedNonblockingResolver() throws UnknownHostException {
        init();
        String [] servers = ResolverConfig.getCurrentConfig().servers();
        if (servers != null) {
            for (int i = 0; i < servers.length; i++) {
                Resolver r = new NonblockingResolver(servers[i]);
                r.setTimeout(quantum);
                resolvers.add(r);
            }
        }
        else
            resolvers.add(new NonblockingResolver());
    }

    /**
     * Creates a new Extended Resolver
     * @param servers An array of server names for which NonblockingResolver
     * contexts should be initialized.
     * @see NonblockingResolver
     * @exception UnknownHostException Failure occured initializing NonblockingResolvers
     */
    public
    ExtendedNonblockingResolver(String [] servers) throws UnknownHostException {
        init();
        for (int i = 0; i < servers.length; i++) {
            Resolver r = new NonblockingResolver(servers[i]);
            r.setTimeout(quantum);
            resolvers.add(r);
        }
    }

    /**
     * Creates a new Extended Resolver
     * @param res An array of pre-initialized Resolvers is provided.
     * @see NonblockingResolver
     * @exception UnknownHostException Failure occured initializing NonblockingResolvers
     */
    public
    ExtendedNonblockingResolver(Resolver [] res) throws UnknownHostException {
        super(res);
    }
}
