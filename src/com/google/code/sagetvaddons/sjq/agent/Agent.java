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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.google.code.sagetvaddons.sjq.listener.Listener;

public final class Agent {
	static private final Logger LOG = Logger.getLogger(Agent.class);
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		PropertyConfigurator.configure("../conf/sjqagent.log4j.properties");
		Config cfg = Config.get();
		File engines = new File("../engines");
		URLClassLoader clsLoader = null;
		if(engines.isDirectory() && engines.canRead()) {
			Collection<URL> urls = new ArrayList<URL>();
			for(Object f : FileUtils.listFiles(engines, new String[] {"jar"}, false))
				urls.add(((File)f).toURI().toURL());
			if(urls.size() > 0)
				clsLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Agent.class.getClassLoader());
		}
		StringBuilder msg = new StringBuilder("The following scripting engines are available in this task client:\n");
		ScriptEngineManager mgr = new ScriptEngineManager(clsLoader);
		for(ScriptEngineFactory f : mgr.getEngineFactories())
			msg.append("\t" + f.getEngineName() + "/" + f.getEngineVersion() + " " + f.getExtensions() + "\n");
		LOG.info(msg.toString());
		Listener listener = new Listener("com.google.code.sagetvaddons.sjq.agent.commands", cfg.getPort());
		listener.init();
	}
}
