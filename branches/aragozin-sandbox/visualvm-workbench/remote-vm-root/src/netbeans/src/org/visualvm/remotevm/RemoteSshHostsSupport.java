/**
 * Copyright 2012-2014 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.visualvm.remotevm;

import java.io.File;

import com.sun.tools.visualvm.core.datasource.DataSource;
import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptorFactory;
import com.sun.tools.visualvm.core.datasupport.Utils;
import com.sun.tools.visualvm.core.ui.DataSourceViewsManager;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public final class RemoteSshHostsSupport {
    
    private static final Object hostsStorageDirectoryLock = new Object();
    // @GuardedBy hostsStorageDirectoryLock
    private static File hostsStorageDirectory;
    
    private static RemoteSshHostsSupport instance;

    private final RemoteSshHostProvider hostProvider = new RemoteSshHostProvider();


    /**
     * Returns singleton instance of HostsSupport.
     * 
     * @return singleton instance of HostsSupport.
     */
    public static synchronized RemoteSshHostsSupport getInstance() {
        if (instance == null) instance = new RemoteSshHostsSupport();
        return instance;
    }
    

    /**
     * Creates new host from provided hostname. Displays a popup dialog if wrong
     * hostname is provided or the host has already been defined.
     * 
     * @param hostname hostname of the host to be created.
     * @return new host from provided hostname or null if the hostname could not be resolved.
     */
    public RemoteSshHost createHost(String hostname) {
        return createHost(new RemoteSshHostProperties(hostname, hostname, null), true, true);
    }
    
    /**
     * Creates new host from provided hostname and display name. Displays a popup
     * dialog if wrong hostname is provided or the host has already been defined.
     * 
     * @param hostname hostname of the host to be created.
     * @param displayname displayname of the host to be created.
     * @return new host from provided hostname or null if the hostname could not be resolved.
     */
    public RemoteSshHost createHost(String hostname, String displayname) {
        return createHost(new RemoteSshHostProperties(hostname, displayname, null), true, true);
    }

    /**
     * Returns an existing Host instance or creates a new Host if needed.
     *
     * @param hostname hostname of the host to be created
     * @param interactive true if any failure should be visually presented to the user, false otherwise.
     * @return an existing or a newly created Host
     *
     * @since VisualVM 1.1.1
     */
    public RemoteSshHost getOrCreateHost(String hostname, boolean interactive) {
        return createHost(new RemoteSshHostProperties(hostname, hostname, null), false, interactive);
    }

    RemoteSshHost createHost(RemoteSshHostProperties properties, boolean createOnly, boolean interactive) {
        return hostProvider.createHost(properties, createOnly, interactive);
    }
        
    /**
     * Returns storage directory for defined hosts.
     * 
     * @return storage directory for defined hosts.
     */
    public static File getStorageDirectory() {
        synchronized(hostsStorageDirectoryLock) {
            if (hostsStorageDirectory == null) {
                String snapshotsStorageString = RemoteSshHostsSupportImpl.getStorageDirectoryString();
                hostsStorageDirectory = new File(snapshotsStorageString);
                if (hostsStorageDirectory.exists() && hostsStorageDirectory.isFile())
                    throw new IllegalStateException("Cannot create hosts storage directory " + snapshotsStorageString + ", file in the way");   // NOI18N
                if (hostsStorageDirectory.exists() && (!hostsStorageDirectory.canRead() || !hostsStorageDirectory.canWrite()))
                    throw new IllegalStateException("Cannot access hosts storage directory " + snapshotsStorageString + ", read&write permission required");    // NOI18N
                if (!Utils.prepareDirectory(hostsStorageDirectory))
                    throw new IllegalStateException("Cannot create hosts storage directory " + snapshotsStorageString); // NOI18N
            }
            return hostsStorageDirectory;
        }
    }
    
    /**
     * Returns true if the storage directory for defined hosts already exists, false otherwise.
     * 
     * @return true if the storage directory for defined hosts already exists, false otherwise.
     */
    public static boolean storageDirectoryExists() {
        return new File(RemoteSshHostsSupportImpl.getStorageDirectoryString()).isDirectory();
    }
    
    
    private RemoteSshHostsSupport() {
        DataSourceDescriptorFactory.getDefault().registerProvider(new RemoteSshHostDescriptorProvider());
        DataSourceDescriptorFactory.getDefault().registerProvider(new RemoteApplication.DescriptorProvider());
        DataSourceViewsManager.sharedInstance().addViewProvider(new RemoteSshHostOverview.Provider(), RemoteSshHost.class);
        
        RemoteSshHostsContainer container = RemoteSshHostsContainer.sharedInstance();
        DataSource.ROOT.getRepository().addDataSource(container);
        
        hostProvider.initialize();
    }

}