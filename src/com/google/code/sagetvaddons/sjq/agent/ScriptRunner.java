/*
 *      Copyright 2010-2011 Battams, Derek
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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.apache.commons.lang.ArrayUtils;

import sagex.SageAPI;
import sagex.remote.rmi.RMISageAPI;

import com.google.code.sagetvaddons.sjq.agent.ProcessRunner.ExeResult;
import com.google.code.sagetvaddons.sjq.network.ServerClient;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;

/**
 * @author dbattams
 *
 */
final class ScriptRunner {

	private String script;
	private QueuedTask qt;
	private String[] args;
	
	/**
	 * 
	 */
	public ScriptRunner(String script, String[] args, QueuedTask qt) {
		this.script = script;
		this.qt = qt;
		if(args == null)
			this.args = new String[0];
		else
			this.args = args;
	}

	public ExeResult exec() {
		int lastDot = script.lastIndexOf('.');
		String ext = script.substring(lastDot + 1);
		if(lastDot == -1 || ext.length() == 0)
			return new ExeResult(-1, "Invalid script extension! [" + script + "]");
		ext = ext.toLowerCase();
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
		bindings.put("Tools", new Tools(qt));
		ScriptContext context = new SimpleScriptContext();
		context.setWriter(new StringWriter());
		context.setErrorWriter(new StringWriter());
		context.setReader(new StringReader(""));
		context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		ScriptEngineManager factory = new ScriptEngineManager();
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
				try { reader.close(); } catch(IOException e) { e.printStackTrace(); }
		}
	}
	
	static public void main(String[] args) {
		Agent.configLog4j();
		try {
			SageAPI.setProvider(new RMISageAPI(args[0], Integer.parseInt(args[1])));
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(255);
		}
		long qId = Long.parseLong(args[2]);
		ServerClient clnt = null;
		try {
			clnt = new ServerClient();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(255);
		}
		QueuedTask qt = null;
		for(QueuedTask t : clnt.getActiveQueue())
			if(t.getQueueId() == qId) {
				qt = t;
				break;
			}
		ExeResult result;
		if(qt != null)
			result = new ScriptRunner(args[3], (String[])ArrayUtils.subarray(args, 4, args.length), qt).exec();
		else
			result = new ExeResult(-1, "Failed to find QueuedTask for id " + qId);
		System.out.println(result.getOutput());
		if(result.getRc() < 0)
			System.exit(255);
		System.exit(result.getRc());
	}
}
