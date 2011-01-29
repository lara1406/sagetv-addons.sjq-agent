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
 * @author dbattams
 *
 */
class HelperUtils {
	
	private QueuedTask qt;
	
	HelperUtils(QueuedTask qt) {
		this.qt = qt;
	}

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
	
	public File mapDir(File path) {
		return new File(mapDir(path.getAbsolutePath()));
	}
	
	public String[] mapDir(String[] paths) {
		String[] mapped = new String[paths.length];
		for(int i = 0; i < paths.length; ++i)
			mapped[i] = mapDir(paths[i]);
		return mapped;
	}
	
	public File[] mapDir(File[] paths) {
		File[] mapped = new File[paths.length];
		for(int i = 0; i < paths.length; ++i)
			mapped[i] = mapDir(paths[i]);
		return mapped;
	}
}
