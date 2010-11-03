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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import name.pachler.nio.file.FileSystems;
import name.pachler.nio.file.Path;
import name.pachler.nio.file.Paths;
import name.pachler.nio.file.StandardWatchEventKind;
import name.pachler.nio.file.WatchEvent;
import name.pachler.nio.file.WatchKey;
import name.pachler.nio.file.WatchService;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import sagex.SageAPI;

import com.google.code.sagetvaddons.sjq.shared.Client;
import com.google.code.sagetvaddons.sjq.shared.Task;

public final class Config {
	static private final Logger LOG = Logger.getLogger(Config.class);
	
	static private final String OPT_PORT = "AGENT.PORT";
	static private final String OPT_CLNT_SCHED = "AGENT.SCHEDULE";
	static private final String OPT_CLNT_RES = "AGENT.RESOURCES";
	static private final String TASK_PREFIX = "TASK.";
	static private final String TASK_OPT_EXE = "EXE";
	static private final String TASK_OPT_ARGS = "ARGS";
	static private final String TASK_OPT_SCHED = "SCHEDULE";
	static private final String TASK_OPT_RES = "RESOURCES";
	static private final String TASK_OPT_MAXPROCS = "MAXPROCS";
	static private final String TASK_OPT_MAXTIME = "MAXTIME";
	static private final String TASK_OPT_MAXRATIO = "MAXTIMERATIO";
	static private final String TASK_OPT_RCMIN = "RCMIN";
	static private final String TASK_OPT_RCMAX = "RCMAX";
	static private final String TASK_OPT_TEST = "TEST";
	static private final String TASK_OPT_TESTARGS = "TESTARGS";
	
	static private final File BASE_DIR = new File(SageAPI.isRemote() ? ".." : "plugins/sjq-agent"); 
	static private final String DEFAULT_PROPS = BASE_DIR + "/conf/sjqagent.properties";
	static private final String REFERENCE_PROPS = BASE_DIR + "/conf/sjqagent.properties.ref";
	static private final int DEFAULT_PORT = 23344;
	static private final String DEFAULT_SCHED = "* * * * *";
	static private final int DEFAULT_RESOURCES = 100;

	static private Config INSTANCE = null;
	static private final Config get(String propsPath) {
		if(INSTANCE == null)
			INSTANCE = new Config(propsPath);
		return INSTANCE; 
	}
	static public final Config get() {
		return get(DEFAULT_PROPS);
	}

	private class PropsMonitor implements Runnable {

		@Override
		public void run() {
			while(true) {
				WatchKey key = null;
				try {
					key = watcher.take();
				} catch (InterruptedException e) {
					LOG.warn("Props file monitor stopped!", e);
					break;
				}
				boolean reload = false;
				for(WatchEvent<?> e : key.pollEvents()) {
					if(e.kind() == StandardWatchEventKind.ENTRY_MODIFY) {
						Path path = (Path)e.context();
						if(path.toString().equals(propsFile.getName())) {
							reload = true;
							break;
						}
					}
				}
				key.reset();
				if(reload) {
					LOG.info("Props file update detected, reloading properties from disk!");
					parseProps();
				}
			}
		}

	}

	private Properties props;
	private int port;
	private String schedule;
	private int totalResources;
	private File propsFile;
	private WatchService watcher;
	private Map<String, Task> tasks;
	
	private Config(String propsPath) {
		propsFile = new File(propsPath);
		watcher = FileSystems.getDefault().newWatchService();
		if(!propsFile.exists()) {
			LOG.warn("Unable to find specified props file! [" + propsFile.getAbsolutePath() + "]");
			LOG.warn("Checking for default props file...");
			propsFile = new File(DEFAULT_PROPS);
			if(!propsFile.exists()) {
				LOG.warn("Unable to find default props file! [" + propsFile.getAbsolutePath() + "]");
				LOG.warn("Creating default props file...");
				File refFile = new File(REFERENCE_PROPS);
				if(!refFile.exists())
					throw new RuntimeException("Reference props file missing!  Your installation appears to be corrupted!");
				try {
					FileUtils.copyFile(refFile, propsFile);
				} catch (IOException e) {
					throw new RuntimeException("Unable to create default props file!", e);
				}
			}
		}
		tasks = new HashMap<String, Task>();
		parseProps();

		Path path = Paths.get(propsFile.getAbsoluteFile().getParent());
		try {
			path.register(watcher, StandardWatchEventKind.ENTRY_MODIFY);
			LOG.info("Watching '" + path + "'");
			Thread t = new Thread(new PropsMonitor());
			t.setDaemon(true);
			t.start();
		} catch (IOException e) {
			LOG.error("Unable to monitor props file '" + propsFile.getAbsolutePath() + "'; changes will not be reflected until an agent restart!", e);
		}
	}

	@Override
	protected void finalize() {
		try {
			if(watcher != null)
				try { watcher.close(); } catch (IOException e) { e.printStackTrace(); }
		} finally {
			try { super.finalize(); } catch(Throwable t) { LOG.warn("FinalizeError", t); }
		}
	}
	
	synchronized private void parseProps() {
		props = new Properties();
		try {
			props.load(new FileReader(propsFile));
		} catch (IOException e) {
			throw new RuntimeException("Cannot read props file! [" + propsFile.getAbsolutePath() + "]", e);
		}
		tasks.clear();
		port = DEFAULT_PORT;
		schedule = DEFAULT_SCHED;
		totalResources = DEFAULT_RESOURCES;
		for(Object k : props.keySet()) {
			if(k.toString().toUpperCase().startsWith(TASK_PREFIX)) {
				String[] parts = k.toString().split("\\.");
				if(parts.length != 3) {
					LOG.warn("Invalid task option; skipping! [" + k + "]");
					continue;
				}
				Task t = tasks.get(parts[1].toUpperCase());
				if(t == null) {
					t = new Task();
					t.setId(parts[1].toUpperCase());
					tasks.put(parts[1].toUpperCase(), t);
				}
				String option = parts[2].toUpperCase();
				if(option.equals(TASK_OPT_EXE))
					t.setExecutable(props.getProperty(k.toString()));
				else if(option.equals(TASK_OPT_ARGS))
					t.setExeArguments(props.getProperty(k.toString()));
				else if(option.equals(TASK_OPT_SCHED))
					t.setSchedule(props.getProperty(k.toString()));
				else if(option.equals(TASK_OPT_RES))
					t.setRequiredResources(Integer.parseInt(props.getProperty(k.toString())));
				else if(option.equals(TASK_OPT_MAXPROCS))
					t.setMaxInstances(Integer.parseInt(props.getProperty(k.toString())));
				else if(option.equals(TASK_OPT_MAXTIME))
					t.setMaxTime(Integer.parseInt(props.getProperty(k.toString())));
				else if(option.equals(TASK_OPT_MAXRATIO))
					t.setMaxTimeRatio(Float.parseFloat(props.getProperty(k.toString())));
				else if(option.equals(TASK_OPT_RCMIN))
					t.setMinReturnCode(Integer.parseInt(props.getProperty(k.toString())));
				else if(option.equals(TASK_OPT_RCMAX))
					t.setMaxReturnCode(Integer.parseInt(props.getProperty(k.toString())));
				else if(option.equals(TASK_OPT_TEST))
					t.setTest(props.getProperty(k.toString()));
				else if(option.equals(TASK_OPT_TESTARGS))
					t.setTestArgs(props.getProperty(k.toString()));
				else
					LOG.warn("Unknown property '" + option + "' defined for task '" + parts[1].toUpperCase() + "', skipping!");
			} else if(k.toString().toUpperCase().equals(OPT_PORT))
				port = Integer.parseInt(props.get(k).toString());
			else if(k.toString().toUpperCase().equals(OPT_CLNT_SCHED))
				schedule = props.getProperty(k.toString());
			else if(k.toString().toUpperCase().equals(OPT_CLNT_RES))
				totalResources = Integer.parseInt(props.getProperty(k.toString()));
			else
				LOG.warn("Unrecognized property skipped! [" + k + "]");
		}
	}
	/**
	 * @return the port
	 */
	synchronized public int getPort() {
		return port;
	}
	/**
	 * @return the schedule
	 */
	synchronized public String getSchedule() {
		return schedule;
	}
	/**
	 * @return the totalResources
	 */
	synchronized public int getTotalResources() {
		return totalResources;
	}

	synchronized public Task[] getTasks() {
		return tasks.values().toArray(new Task[0]);
	}

	synchronized public String[] getTaskIds() {
		return tasks.keySet().toArray(new String[0]);
	}

	synchronized public long getMaxTestTime() {
		return 30;
	}
	
	synchronized public boolean save(Client clnt) {
		Properties props = new Properties();
		props.setProperty(OPT_PORT.toLowerCase(), String.valueOf(clnt.getPort()));
		props.setProperty(OPT_CLNT_SCHED.toLowerCase(), clnt.getSchedule());
		props.setProperty(OPT_CLNT_RES.toLowerCase(), String.valueOf(clnt.getMaxResources()));
		for(Task t : clnt.getTasks()) {
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_EXE).toLowerCase(), t.getExecutable());
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_ARGS).toLowerCase(), t.getExeArguments() == null ? "" : t.getExeArguments());
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_TEST).toLowerCase(), t.getTest() == null ? "" : t.getTest());
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_TESTARGS).toLowerCase(), t.getTestArgs() == null ? "" : t.getTestArgs());
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_SCHED).toLowerCase(), t.getSchedule());
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_RES).toLowerCase(), String.valueOf(t.getRequiredResources()));
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_MAXPROCS).toLowerCase(), String.valueOf(t.getMaxInstances()));
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_MAXTIME).toLowerCase(), String.valueOf(t.getMaxTime()));
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_MAXRATIO).toLowerCase(), String.valueOf(t.getMaxTimeRatio()));
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_RCMAX).toLowerCase(), String.valueOf(t.getMaxReturnCode()));
			props.setProperty((TASK_PREFIX + t.getId() + "." + TASK_OPT_RCMIN).toLowerCase(), String.valueOf(t.getMinReturnCode()));
		}
		try {
			Writer w = new FileWriter(propsFile);
			props.store(w, "Generated by SJQv4 agent");
			w.close();
			LOG.info("Agent properties file updated via network socket!");
			return true;
		} catch(IOException e) {
			LOG.error("IOError", e);
			return false;
		} 
	}
	
	public String getBaseDir() {
		return BASE_DIR.getAbsolutePath();
	}
}
