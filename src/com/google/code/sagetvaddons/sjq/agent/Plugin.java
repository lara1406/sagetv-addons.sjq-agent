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
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;
import sagex.SageAPI;
import sagex.api.Global;

import com.google.code.sagetvaddons.sjq.listener.Listener;
import com.google.code.sagetvaddons.sjq.server.DataStore;
import com.google.code.sagetvaddons.sjq.shared.Client;

/**
 * @author dbattams
 *
 */
public final class Plugin implements SageTVPlugin {
	static private final Logger LOG = Logger.getLogger(Plugin.class);

	private class AgentListenerThread extends Thread {
		private Listener listener;
		
		private AgentListenerThread(Listener listener) {
			this.listener = listener;
		}
		
		@Override
		public void run() {
			try {
				Config cfg = Config.get();
				Properties props = new Properties();
				props.load(new FileReader(new File(cfg.getBaseDir() + "/conf/sjqagent.log4j.properties")));
				props.setProperty("log4j.appender.sjqAgentApp.File", cfg.getBaseDir() + props.getProperty("log4j.appender.sjqAgentApp.File"));
				PropertyConfigurator.configure(props);
				StringBuilder msg = new StringBuilder("SJQv4 Agent (Task Client) v" + cfg.getVersion() + "\n\nThe following scripting engines are available in this task client:\n");
				ScriptEngineManager mgr = new ScriptEngineManager();
				for(ScriptEngineFactory f : mgr.getEngineFactories())
					msg.append("\t" + f.getEngineName() + "/" + f.getEngineVersion() + " " + f.getExtensions() + "\n");
				LOG.info(msg.toString());
				listener.init();
			} catch (IOException e) {
				LOG.error("Error", e);
			}
		}
	}
	
	private Listener agent;
	
	/**
	 * 
	 */
	public Plugin(SageTVPluginRegistry reg) {
		
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
		try {
			Thread.sleep(5500);
		} catch (InterruptedException e) {
			LOG.error("SleepKilled", e);
		}
		agent = new Listener("com.google.code.sagetvaddons.sjq.agent.commands", Config.get().getPort());
		AgentListenerThread t = new AgentListenerThread(agent);
		t.start();
		DataStore ds = DataStore.get();
		String srv = Global.GetServerAddress();
		int port = Config.get().getPort();
		if(ds.getClient(srv, port) == null) {
			Client clnt = new Client(srv, port);
			ds.saveClient(clnt);
			LOG.info("Registered plugin agent with server!");
		}
	}

	/* (non-Javadoc)
	 * @see sage.SageTVPlugin#stop()
	 */
	@Override
	public void stop() {
		agent.setStopped(true);
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
