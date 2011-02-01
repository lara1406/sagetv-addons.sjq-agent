/*
 *      Copyright 2011 Battams, Derek
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
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

import com.google.code.sagetvaddons.sjq.agent.network.ServerClient;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;

/**
 * <p>A series of helper methods that provide addtional functionality to your Groovy scripts within the SJQ scripting environment.</p>
 * <p>
 *    The <code>Since</code> tags for each method denotes which task client version the method became available in.  The version refers to the last number of the version number.
 *    So, for example, if a method says <code>Since: 1350</code> then you must be running a task client whose version number ends in <code>.1350</code> or newer (i.e. <code>4.0.0.1350</code>).
 * </p>
 * <p>Note that these methods are accessible via the <code>Tools</code> global variable within your Groovy scripts (i.e. <code>Tools.setExeArgs("foo")</code>).</p>
 * @author dbattams
 * $Id$
 */
public class Tools {
	
	private QueuedTask qt;
	
	Tools(QueuedTask qt) {
		this.qt = qt;
	}

	/**
	 * <p>Dynamically modify the command line arguments for the exe associated with the currently running task.</p>
	 * 
	 * <p>
	 *    You should only call this method from the test script of a task.  Even though nothing prevents you from making this call from an exe script, it will have no effect since the exe will have already been called with the original command line arguments.
	 *    Call this method from your test script to dynamically modify the command line arguments of the exe.  Environment variables will be honoured and expanded when set via this method (i.e. $SJQ4_PATH, etc.).
	 * </p>
	 * 
	 * <p>Example</p>
	 * <pre>
	 *    // Assume the first argument of the test script is the path, as given by the SageTV core
	 *    def fileName = Tools.mapDir(SJQ4_ARGS[0])
	 *    if(!Tools.setExeArgs(fileName))
	 *       return 1 // If the call fails then return the task to the queue and try again later...
	 *     return 0 // The exe args have been modified, return success and fire off the exe!!
	 * </pre>
	 * @param args The new arguments string for the exe; be sure to double quote args with spaces, special chars, etc.
	 * @return True on success or false on any failure
	 * @since 1355
	 */
	public boolean setExeArgs(String args) {
		ServerClient sc = null;
		try {
			sc = new ServerClient(qt.getServerHost(), qt.getServerPort());
			return sc.setExeArgs(qt, args).isOk();
		} catch(IOException e) {
			return false;
		} finally {
			if(sc != null)
				sc.close();
		}
	}
	
	/**
	 * <p>Allow dynamic changes to the amount of resouces this task is consuming on the client running it at runtime.</p>
	 * 
	 * <p>
	 *    Use this method to modify the number of resources this task consumes at runtime.  You can call this from
	 *    a test script or an exe script.  The argument is the new <b>total</b> resources being consumed by the task,
	 *    it is <b>not</b> an incremental value!
	 * </p>
	 * 
	 * <p>
	 *    There is a timing issue involved with the use of this method.  It is possible to increase the total resources
	 *    being used beyond the maximum value under the following circumstances:
	 *    
	 *    <ul>
	 *       <li>Server assigns Task 1 to Client A and it originally uses 50 resources (client max resources is 100)</li>
	 *       <li>
	 *          Task 1 dynamically calls this method to increase the used resources for this task to 90, but before the call is
	 *          made, the server assigns Task 2 to Client A and it uses 30 resources (Client A is now using 80 resources).
	 *       </li>
	 *       <li>
	 *          Now Task 1 increases its resource usage from 50 to 90.  Client A is now using 120 resources (of 100).  This is
	 *          ok, except that a Client could temporarily be using more than its allotted resources.
	 *       </li>
	 *       <li>
	 *          The opposite can happen if a task dynamically lowers its resource usage.  If the server tries to assigns a new task
	 *          before the old one lowers its resource usage then the server will refuse to assign the task because there isn't enough
	 *          resources.  This is ok, as well, with the side effect being that the new task will be delayed assignment until the next
	 *          check (or possibly assigned to a different client capable of running the task).
	 *       </li>
	 *    </ul>
	 * </p>
	 * 
	 * <p>Example:</p>
	 * <pre>
	 *    // Set the used resources for this task to 95
	 *    Tools.setTaskResources(95)
	 * </pre>
	 * 
	 * @param used The new total amount of resources this task is consuming on the client running the task
	 * @return True on success or false on any failure
	 * @since 1364
	 */
	public boolean setTaskResources(int used) {
		ServerClient sc = null;
		try {
			sc = new ServerClient(qt.getServerHost(), qt.getServerPort());
			return sc.setTaskResources(qt, used).isOk();
		} catch(IOException e) {
			return false;
		} finally {
			if(sc != null)
				sc.close();
		}
	}
	
	/**
	 * <p>Remap a file path based on the agent.mapdir settings of the task client running the task.</p>
	 * 
	 * <p>
	 *    Use this method to process a file or directory path and replace the directory with the mapping defined in your task client.
	 *    This is especially useful for remote SJQ task clients when Sage gives local file paths for recordings.
	 * </p>
	 * 
	 * <p>
	 *    If SageTV returns <code>D:\tv\*</code> for your recordings, but you need to access the files as <code>\\sagetvsrv\tv\*</code>
	 *    then simply set <code>agent.mapdir = D:\\tv\\,\\\\sagetvsrv\\tv\\</code> and call this method.
	 * </p>
	 * 
	 * <p>Example:</p>
	 * <pre>
	 *    // Assume mf is a MediaFile object obtained somewhere above...
	 *    println Tools.mapDir(MediaFileAPI.GetFileForSegment(mf, 0).getAbsolutePath())
	 *    return 0
	 * </pre>
	 * @param path The path name to be remapped based on the agent.mapdir setting of the client this method is called from
	 * @return The remapped pathname, which could be unchanged if no mapping matches the given argument
	 * @since 1350
	 */
	public String mapDir(String path) {
		Map<String, String> mapDir = Config.get().getMapDir();
		for(String dir : mapDir.keySet()) {
			if(FilenameUtils.wildcardMatch(path, FilenameUtils.normalizeNoEndSeparator(dir) + File.separator + "*")) {
				if(SystemUtils.IS_OS_WINDOWS)
					return StringUtils.replaceOnce(path.toUpperCase(), dir.toUpperCase(), mapDir.get(dir).toUpperCase());
				return StringUtils.replaceOnce(path, dir, mapDir.get(dir));
			}
		}
		return path;
	}

	/**
	 * <p>Remap a file path based on the agent.mapdir settings of the task client running the task.</p>
	 * 
	 * <p>
	 *    Use this method to process a file or directory path and replace the directory with the mapping defined in your task client.
	 *    This is especially useful for remote SJQ task clients when Sage gives local file paths for recordings.
	 * </p>
	 * 
	 * <p>
	 *    If SageTV returns <code>D:\tv\*</code> for your recordings, but you need to access the files as <code>\\sagetvsrv\tv\*</code>
	 *    then simply set <code>agent.mapdir = D:\\tv\\,\\\\sagetvsrv\\tv\\</code> and call this method.
	 * </p>
	 * 
	 * <p>Example:</p>
	 * <pre>
	 *    // Assume mf is a MediaFile object obtained somewhere above...
	 *    println Tools.mapDir(MediaFileAPI.GetFileForSegment(mf, 0))
	 *    return 0
	 * </pre>
	 * @param path The path name to be remapped based on the agent.mapdir setting of the client this method is called from
	 * @return The remapped pathname, which could be unchanged if no mapping matches the given argument
	 * @since 1350
	 */
	public File mapDir(File path) {
		return new File(mapDir(path.getAbsolutePath()));
	}
	
	/**
	 * <p>Remap an array of file paths based on the agent.mapdir settings of the task client running the task.</p>
	 * 
	 * <p>
	 *    Use this method to process a file or directory path and replace the directory with the mapping defined in your task client.
	 *    This is especially useful for remote SJQ task clients when Sage gives local file paths for recordings.
	 * </p>
	 * 
	 * <p>
	 *    If SageTV returns <code>D:\tv\*</code> for your recordings, but you need to access the files as <code>\\sagetvsrv\tv\*</code>
	 *    then simply set <code>agent.mapdir = D:\\tv\\,\\\\sagetvsrv\\tv\\</code> and call this method.
	 * </p>
	 * 
	 * <p>Example:</p>
	 * <pre>
	 *    // Assume mf is a MediaFile object obtained somewhere above...
	 *    String[] paths = new String[MediaFileAPI.GetNumberOfSegments(mf)]
	 *    File[] files = MediaFileAPI.GetSegmentFiles(mf)
	 *    for(int i = 0; i < paths.length; ++i)
	 *       paths[i] = files[i].getAbsolutePath()
	 *    println Tools.mapDir(paths)
	 *    return 0
	 * </pre>
	 * @param paths The array of path names to be remapped
	 * @return The array of remapped pathnames, which could be unchanged if no mapping matches the given arguments
	 * @since 1350
	 */
	public String[] mapDir(String[] paths) {
		String[] mapped = new String[paths.length];
		for(int i = 0; i < paths.length; ++i)
			mapped[i] = mapDir(paths[i]);
		return mapped;
	}

	/**
	 * <p>Remap an array of File objects based on the agent.mapdir settings of the task client running the task.</p>
	 * 
	 * <p>
	 *    Use this method to process an array of File objects and replace the directory with the mapping defined in your task client.
	 *    This is especially useful for remote SJQ task clients when Sage gives local file paths for recordings.
	 * </p>
	 * 
	 * <p>
	 *    If SageTV returns <code>D:\tv\*</code> for your recordings, but you need to access the files as <code>\\sagetvsrv\tv\*</code>
	 *    then simply set <code>agent.mapdir = D:\\tv\\,\\\\sagetvsrv\\tv\\</code> and call this method.
	 * </p>
	 * 
	 * <p>Example:</p>
	 * <pre>
	 *    // Assume mf is a MediaFile object obtained somewhere above...
	 *    File[] files = Tools.mapDir(MediaFileAPI.GetSegmentFiles(mf))
	 *    println files
	 *    return 0
	 * </pre>
	 * @param paths The array of path names to be remapped
	 * @return The array of remapped pathnames, which could be unchanged if no mapping matches the given arguments
	 * @since 1350
	 */
	public File[] mapDir(File[] paths) {
		File[] mapped = new File[paths.length];
		for(int i = 0; i < paths.length; ++i)
			mapped[i] = mapDir(paths[i]);
		return mapped;
	}
}
