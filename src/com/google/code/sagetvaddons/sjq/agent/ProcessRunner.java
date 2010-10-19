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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.log4j.Logger;

import sagex.SageAPI;
import sagex.remote.rmi.RMISageAPI;

import com.google.code.sagetvaddons.sjq.agent.network.ServerClient;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask.State;

/**
 * @author dbattams
 *
 */
final public class ProcessRunner implements Runnable {

	static public enum TestResult {
		PASS,
		FAIL,
		SKIP
	}

	static private class ExeResult {
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

	private final Logger log;
	private final QueuedTask qt;
	private final Map<String, String> env;

	/**
	 * 
	 */
	public ProcessRunner(QueuedTask qt) {
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
		} catch (IOException e) {
			log.fatal("Failed to update " + qt, e);
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
				result = runScript(exe.substring(SCRIPT_PREFIX.length()), getArgsArray(qt.getExeArguments()));
				return result.getRc();
			}
			File exeFile = new File(exe);
			if(!exeFile.canExecute()) {
				log.error("Unable to execute '" + exeFile.getAbsolutePath() + "'; marking task as FAILED!");
				return -1;
			}
			result = runExternalExe();
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
		try {
			clnt = new ServerClient(qt.getServerHost(), qt.getServerPort());
			clnt.update(qt);
		} finally {
			if(clnt != null)
				clnt.close();		
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
		ExeResult result = runScript(exe, getArgsArray(qt.getTestArgs()));
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

	private ExeResult runScript(String script, String[] args) {
		int lastDot = script.lastIndexOf('.');
		String ext = script.substring(lastDot + 1);
		if(lastDot == -1 || ext.length() == 0)
			return new ExeResult(-1, "Invalid script extension! [" + script + "]");
		try {
			SageAPI.setProvider(new RMISageAPI(qt.getServerHost(), qt.getRmiPort()));
		} catch(Exception e) {
			return new ExeResult(-1, e.getMessage());
		}
		Bindings bindings = new SimpleBindings();
		bindings.put("AiringAPI",new sagex.api.AiringAPI());
		bindings.put("AlbumAPI",new sagex.api.AlbumAPI());
		bindings.put("CaptureDeviceAPI", new sagex.api.CaptureDeviceAPI());
		bindings.put("CaptureDeviceInputAPI", new sagex.api.CaptureDeviceInputAPI());
		bindings.put("ChannelAPI",new sagex.api.ChannelAPI());
		bindings.put("Configuration",new sagex.api.Configuration());
		bindings.put("Database",new sagex.api.Database());
		bindings.put("FavoriteAPI",new sagex.api.FavoriteAPI());
		bindings.put("Global", new sagex.api.Global());
		bindings.put("LocatorAPI", new sagex.api.LocatorAPI());
		bindings.put("MediaFileAPI", new sagex.api.MediaFileAPI());
		bindings.put("MediaPlayerAPI",new sagex.api.MediaPlayerAPI());
		bindings.put("PlaylistAPI",new sagex.api.PlaylistAPI());
		bindings.put("PluginAPI", new sagex.api.PluginAPI());
		bindings.put("SeriesInfoAPI",new sagex.api.SeriesInfoAPI());
		bindings.put("ShowAPI",new sagex.api.ShowAPI());
		bindings.put("SystemMessageAPI", new sagex.api.SystemMessageAPI());
		bindings.put("TranscodeAPI",new sagex.api.TranscodeAPI());
		bindings.put("TVEditorialAPI",new sagex.api.TVEditorialAPI());
		bindings.put("UserRecordAPI", new sagex.api.UserRecordAPI());
		bindings.put("Utility",new sagex.api.Utility());
		bindings.put("WidgetAPI",new sagex.api.WidgetAPI());
		bindings.put("SJQ4_METADATA", qt.getMetadata());
		bindings.put("SJQ4_SCRIPT", script);
		bindings.put("SJQ4_ARGS", args);
		ScriptContext context = new SimpleScriptContext();
		context.setWriter(new StringWriter());
		context.setErrorWriter(new StringWriter());
		context.setReader(new StringReader(""));
		context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		ScriptEngineManager factory = new ScriptEngineManager(Config.get().getClsLoader());
		ScriptEngine engine = factory.getEngineByExtension(ext);
		if(engine == null)
			return new ExeResult(-1, "Unsupported script extension '" + ext + "'; maybe you need to install a scripting engine for this language?");
		FileReader reader = null;
		try {
			int rc;
			reader = new FileReader(new File(script));
			Object o = engine.eval(reader, context);
			if(o == null)
				rc = 0;
			else if(o instanceof Integer)
				rc = (Integer)o;
			else
				rc = -1;
			StringBuilder output = new StringBuilder();
			if(context.getWriter().toString().length() > 0) {
				output.append("----- stdout -----\n\n");
				output.append(context.getWriter().toString());
				output.append("------------------\n\n");
			}
			if(context.getErrorWriter().toString().length() > 0) {
				output.append("----- stderr -----\n\n");
				output.append(context.getErrorWriter().toString());
				output.append("------------------\n\n");
			}
			return new ExeResult(rc, output.toString());
		} catch (FileNotFoundException e) {
			return new ExeResult(-1, e.getMessage());
		} catch (ScriptException e) {
			return new ExeResult(-1, e.getMessage());
		} finally {
			if(reader != null)
				try { reader.close(); } catch(IOException e) { log.warn("IOError", e); }
		}			
	}

	private ExeResult runExternalExe() {
		File exeFile = new File(qt.getExecutable());
		if(!exeFile.canExecute()) {
			String err = "Exe does not exist or cannot be executed! [" + exeFile.getAbsolutePath() + "]";
			log.error(err);
			return new ExeResult(-1, err);
		}
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		CommandLine cmd = new CommandLine(exeFile);
		cmd.addArguments(qt.getExeArguments());
		Executor executor = new DefaultExecutor();
		executor.setExitValues(null);
		executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
		ExecuteWatchdog watchdog = new ExecuteWatchdog(qt.getMaxTime() * 1000);
		ACTIVE_TASKS.put(genThreadName(qt), new KillableExe(watchdog));
		executor.setWatchdog(watchdog);
		int rc = -1;
		try {
			rc = executor.execute(cmd, getEnvMap(qt.getMetadata()));
		} catch (ExecuteException e) {
			rc = e.getExitValue();
			log.error("Execution failed! [rc=" + rc + "]", e);
		} catch (IOException e) {
			log.error("IOError", e);
		} finally {
			ACTIVE_TASKS.remove(genThreadName(qt));
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
			return new ExeResult(-1, "*** Process killed by SJQ because it ran too long! ***\n\n" + (output.toString().length() > 0 ? output.toString() : ""));
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

	static private final String[] getArgsArray(String args) {
		return new CommandLine("a.exe").addArguments(args).getArguments();
	}
}
