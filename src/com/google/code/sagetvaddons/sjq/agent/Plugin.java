/*
 *      Copyright 2010 Battams, Derek
 *       
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */
package com.google.code.sagetvaddons.sjq.agent;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.log4j.Logger;

import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;
import sagex.api.Global;
import sagex.api.Utility;

import com.google.code.sagetvaddons.sjq.network.ServerClient;
import com.google.code.sagetvaddons.sjq.server.DataStore;
import com.google.code.sagetvaddons.sjq.shared.Client;

/**
 * @author dbattams
 *
 */
public final class Plugin implements SageTVPlugin {
	static private final Logger LOG = Logger.getLogger(Plugin.class);
	
	Thread agent;
	
	/**
	 * 
	 */
	public Plugin(SageTVPluginRegistry reg) {
		agent = new Thread() {
			@Override
			public void run() {
				try {
					Agent.main(new String[0]);
				} catch (Exception e) {
					LOG.warn("Exception", e);
				}
			}
		};
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#destroy()
	 */
	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigHelpText(java.lang.String)
	 */
	@Override
	public String getConfigHelpText(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigLabel(java.lang.String)
	 */
	@Override
	public String getConfigLabel(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigOptions(java.lang.String)
	 */
	@Override
	public String[] getConfigOptions(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigSettings()
	 */
	@Override
	public String[] getConfigSettings() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigType(java.lang.String)
	 */
	@Override
	public int getConfigType(String arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigValue(java.lang.String)
	 */
	@Override
	public String getConfigValue(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#getConfigValues(java.lang.String)
	 */
	@Override
	public String[] getConfigValues(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#resetConfig()
	 */
	@Override
	public void resetConfig() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#setConfigValue(java.lang.String, java.lang.String)
	 */
	@Override
	public void setConfigValue(String arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#setConfigValues(java.lang.String, java.lang.String[])
	 */
	@Override
	public void setConfigValues(String arg0, String[] arg1) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#start()
	 */
	@Override
	public void start() {
		File logs = new File("plugins/sjq-agent/logs");
		if(!logs.exists())
			logs.mkdirs();
		agent = new Thread() {
			@Override
			public void run() {
				try {
					Agent.main(new String[0]);
				} catch (Exception e) {
					LOG.warn("Exception", e);
				}
			}
		};
		agent.start();
		DataStore ds = DataStore.get();
		String srv;
		if(!Global.IsClient())
			srv = Global.GetServerAddress();
		else {
			try {
				ServerClient sc = new ServerClient();
				srv = sc.getLocalHost();
			} catch (IOException e) {
				try {
					srv = InetAddress.getLocalHost().getHostName();
				} catch (UnknownHostException e1) {
					srv = Utility.GetLocalIPAddress();
				}
			}
		}
		LOG.info("Checking if hostname '" + srv + "' is registered...");
		int port = Config.get().getPort();
		if(ds.getClient(srv, port) == null) {
			Client clnt = new Client(srv, port);
			ds.saveClient(clnt);
			LOG.info("Registered '" + srv + "' as plugin agent with server! " + clnt);
		}
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#stop()
	 */
	@Override
	public void stop() {
		agent.interrupt();
	}

	/* (non-Javadoc)
	 * @see sage.SageTVEventListener#sageEvent(java.lang.String, java.util.Map)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void sageEvent(String arg0, Map arg1) {
		// TODO Auto-generated method stub

	}

}
