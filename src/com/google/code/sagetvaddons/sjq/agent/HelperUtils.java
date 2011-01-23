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
import java.util.Map;

/**
 * @author dbattams
 *
 */
class HelperUtils {
	
	HelperUtils() {}

	public String mapDir(String path) {
		Map<String, String> mapDir = Config.get().getMapDir();
		for(String dir : mapDir.keySet()) {
			if(path.startsWith(dir))
				return path.replace(dir, mapDir.get(dir));
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
