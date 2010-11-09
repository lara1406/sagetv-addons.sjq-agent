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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.Logger;

import sagex.SageAPI;

import com.google.code.sagetvaddons.sjq.agent.network.ServerClient;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask.State;

/**
 * @author dbattams
 *
 */
final public class ProcessRunner implements Runnable {
	static private final Logger LOG = Logger.getLogger(ProcessRunner.class);

	static public enum TestResult {
		PASS,
		FAIL,
		SKIP
	}

	static class ExeResult {
		private int rc;
		private String output;

		public ExeResult(int rc, String output) {
			this.rc = rc;
			this.output = output;
		}

		/**
		 * @return the rc
		 */
		public int getRc() {
			return rc;
		}

		/**
		 * @return the output
		 */
		public String getOutput() {
			return output;
		}
	}

	static public final String SCRIPT_PREFIX = "script:";
	static final Map<String, Killable> ACTIVE_TASKS = Collections.synchronizedMap(new HashMap<String, Killable>());
	public static final String genThreadName(QueuedTask qt) {
		return "SJQ4Task-" + qt.getServerHost() + "-" + qt.getServerPort() + "-" + qt.getQueueId();
	}

	synchronized public static final boolean kill(String threadName) {
		Killable k = ACTIVE_TASKS.remove(threadName);
		if(k != null) {
			LOG.warn("Killing task thread '" + threadName + "'");
			k.kill();
		} else {
			LOG.error("Unable to kill '" + threadName + "'; " + ACTIVE_TASKS);
		}
		return k != null;
	}

	synchronized public static final void killAll() {
		LOG.warn("Killing all active tasks! [count=" + ACTIVE_TASKS.size() + "]");
		for(String tName : ACTIVE_TASKS.keySet())
			kill(tName);
		ACTIVE_TASKS.clear();
	}

	synchronized public static boolean isActive(QueuedTask qt) {
		boolean retVal = ACTIVE_TASKS.containsKey(genThreadName(qt));
		LOG.warn("isActive(" + genThreadName(qt) + ") = " + retVal + "; " + ACTIVE_TASKS.keySet());
		return retVal;
	}

	private final Logger log;
	private final QueuedTask qt;
	private final Map<String, String> env;

	/**
	 * 
	 */
	public ProcessRunner(QueuedTask qt) {
		synchronized(ProcessRunner.class) {
			ACTIVE_TASKS.put(genThreadName(qt), null);
		}
		this.qt = qt;
		log = Logger.getLogger(ProcessRunner.class.getName() + "." + qt.getServerHost().replace(".", "_") + "-" + qt.getServerPort() + "-" + qt.getQueueId());
		env = new HashMap<String, String>();
		try {
			Map<?, ?> parentEnv = EnvironmentUtils.getProcEnvironment();
			for(Object var : parentEnv.keySet())
				env.put(var.toString(), parentEnv.get(var).toString());
		} catch (IOException e) {
			log.warn("IOError grabbing parent env; using empty env instead!", e);
		}
		env.putAll(qt.getMetadata());
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		log.info("Starting process runner for: " + qt);
		boolean forceUpdate = true;
		if(qt.getExeArguments() != null && qt.getExeArguments().length() > 0)
			qt.setExeArguments(expandArgs(qt.getExeArguments()));
		if(qt.getTestArgs() != null && qt.getTestArgs().length() > 0)
			qt.setTestArgs(expandArgs(qt.getTestArgs()));
		try {
			TestResult testResult = runTest();
			if(testResult == TestResult.FAIL) {
				qt.setState(State.RETURNED);
				qt.setCompleted(new Date());
			} else if(testResult == TestResult.SKIP) {
				qt.setState(State.SKIPPED);
				qt.setCompleted(new Date());
			} else {
				int rc = runExe();
				qt.setCompleted(new Date());
				qt.setState(rc >= qt.getMinReturnCode() && rc <= qt.getMaxReturnCode() ? State.COMPLETED : State.FAILED);			
			}
			try {
				updateTask();
				forceUpdate = false;
			} catch (IOException e) {
				log.fatal("Failed to update " + qt, e);
			}
		} finally {
			if(forceUpdate) {
				LOG.warn("Removing task from active list: " + genThreadName(qt));
				synchronized(ProcessRunner.class) {
					ACTIVE_TASKS.remove(genThreadName(qt));
				}
			}
		}
	}

	private int runExe() {
		String exe = qt.getExecutable();
		ExeResult result = null;
		try {
			if(exe.toLowerCase().startsWith(SCRIPT_PREFIX)) {
				File script = new File(exe.substring(SCRIPT_PREFIX.length()));
				if(!script.canRead()) {
					log.error("Unable to read script '" + script.getAbsolutePath() + "'; marking task as FAILED!");
					return -1;
				}
				result = runScript(exe.substring(SCRIPT_PREFIX.length()), getArgsArray(qt.getExeArguments()), qt.getMaxTime() * 1000L);
				return result.getRc();
			}
			File exeFile = new File(exe);
			if(!exeFile.canExecute()) {
				log.error("Unable to execute '" + exeFile.getAbsolutePath() + "'; marking task as FAILED!");
				return -1;
			}
			result = runExternalExe(qt.getExecutable(), getArgsArray(qt.getExeArguments()), qt.getMaxTime() * 1000L);
			return result.getRc();
		} finally {
			if(result != null && result.getOutput().length() > 0) {
				ServerClient sc = null;
				try {
					sc = new ServerClient(qt.getServerHost(), qt.getServerPort());
					sc.logTaskOutput(qt, result.getOutput());
				} catch (IOException e) {
					log.error("Failed to send task output to server!", e);
				} finally {
					if(sc != null) sc.close();
				}
			}
		}
	}

	private void updateTask() throws IOException {
		ServerClient clnt = null;
		synchronized(ProcessRunner.class) {
			try {
				clnt = new ServerClient(qt.getServerHost(), qt.getServerPort());
				clnt.update(qt);
			} finally {
				if(clnt != null)
					clnt.close();		
			}
			LOG.warn("Removing task from active list: " + genThreadName(qt));
			ACTIVE_TASKS.remove(genThreadName(qt));
		}
	}

	private TestResult runTest() {
		TestResult rc;
		String exe = qt.getTest();
		if(exe == null || exe.length() == 0)
			rc = TestResult.PASS;
		else
			rc = runScriptTest();
		return rc;
	}

	private TestResult runScriptTest() {
		String exe = qt.getTest();
		if(exe.toLowerCase().startsWith(SCRIPT_PREFIX))
			exe = exe.substring(SCRIPT_PREFIX.length());
		if(exe.length() == 0) {
			log.error("Script path is missing!  Test considered FAILED!");
			return TestResult.FAIL;
		}
		File exeFile = new File(exe);
		if(!exeFile.canRead()) {
			log.error("'" + exeFile.getAbsolutePath() + "' does not exist or is not readable!  Test considered FAILED!");
			return TestResult.FAIL;
		}
		ExeResult result = runScript(exe, getArgsArray(qt.getTestArgs()), Config.get().getMaxTestTime() * 1000L);
		ServerClient sc = null;
		try {
			sc = new ServerClient(qt.getServerHost(), qt.getServerPort());
			sc.logTestOutput(qt, result.getOutput());
		} catch (IOException e) {
			log.error("Failed to send logs to server!", e);
		} finally {
			if(sc != null) sc.close();
		}
		switch(result.getRc()) {
		case 0: return TestResult.PASS;
		case 2: return TestResult.SKIP;
		default: return TestResult.FAIL;

		}
	}

	@SuppressWarnings("unchecked")
	private ExeResult runScript(final String script, String[] args, long maxTimeMillis) {

		String javaExe = System.getProperty("java.home") + "/bin/java";
		if(SystemUtils.IS_OS_WINDOWS)
			javaExe = javaExe.concat(".exe");
		CommandLine cmd = new CommandLine(javaExe);
		Collection<?> jars;
		if(SageAPI.isRemote()) {
			jars = FileUtils.listFiles(new File("../lib"), new String[] {"jar"}, false);
			jars.addAll(FileUtils.listFiles(new File("../engines"), new String[] {"jar"}, false));			
		} else
			jars = FileUtils.listFiles(new File("JARs"), new String[] {"jar"}, false);
		cmd.addArguments((String[])ArrayUtils.addAll(new String[] {"-cp", StringUtils.join(jars, SystemUtils.PATH_SEPARATOR), ScriptRunner.class.getName(), qt.getServerHost(), String.valueOf(qt.getRmiPort()), String.valueOf(qt.getQueueId()), script}, args));
		return runExternalExe(cmd.getExecutable(), cmd.getArguments(), maxTimeMillis);
	}

	private ExeResult runExternalExe(String exe, String[] args, long maxTimeMillis) {
		File exeFile = new File(exe);
		if(!exeFile.canExecute()) {
			String err = "Exe does not exist or cannot be executed! [" + exeFile.getAbsolutePath() + "]";
			log.error(err);
			return new ExeResult(-1, err);
		}
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		CommandLine cmd = new CommandLine(exeFile);
		cmd.addArguments(args);
		Executor executor = new DefaultExecutor();
		executor.setExitValues(null);
		executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
		ExecuteWatchdog watchdog = new ExecuteWatchdog(maxTimeMillis);
		synchronized(ProcessRunner.class){ 
			ACTIVE_TASKS.put(genThreadName(qt), new KillableExe(watchdog));
		}
		executor.setWatchdog(watchdog);
		int rc = -1;
		try {
			rc = executor.execute(cmd, getEnvMap(qt.getMetadata()));
		} catch (ExecuteException e) {
			rc = e.getExitValue();
			log.error("Execution failed! [rc=" + rc + "]", e);
		} catch (IOException e) {
			log.error("IOError", e);
		}
		StringBuilder output = new StringBuilder();
		if(stdout.toString().length() > 0) {
			output.append("----- stdout -----\n\n");
			output.append(stdout.toString());
			output.append("------------------\n\n");
		}
		if(stderr.toString().length() > 0) {
			output.append("----- stderr -----\n\n");
			output.append(stderr.toString());
			output.append("------------------\n\n");
		}
		if(watchdog.killedProcess())
			return new ExeResult(-1, "*** Process killed by SJQ ***\n\n" + (output.toString().length() > 0 ? output.toString() : ""));
		return new ExeResult(rc, output.toString());
	}

	@SuppressWarnings("unchecked")
	static private final Map<String, String> getEnvMap(Map<String, String> map) {
		Map<String, String> env;
		try {
			env = (Map<String, String>)EnvironmentUtils.getProcEnvironment();
		} catch (IOException e) {
			env = new HashMap<String, String>();
		}
		env.putAll(map);
		return env;
	}
	
	private final String expandArgs(final String args) {
		String expandedArgs = args;
		for(String key : qt.getMetadata().keySet())
			expandedArgs = expandedArgs.replace("$" + key, qt.getMetadata().get(key));
		if(!expandedArgs.equals(args))
			LOG.info("Converted '" + args + "' to '" + expandedArgs + "'");
		return expandedArgs;
	}

	static private final String[] getArgsArray(String args) {
		return new CommandLine("a.exe").addArguments(args).getArguments();
	}
}
