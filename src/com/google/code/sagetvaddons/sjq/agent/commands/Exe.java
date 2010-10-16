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
package com.google.code.sagetvaddons.sjq.agent.commands;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Logger;

import com.google.code.sagetvaddons.sjq.agent.ProcessRunner;
import com.google.code.sagetvaddons.sjq.listener.Command;
import com.google.code.sagetvaddons.sjq.listener.NetworkAck;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;

/**
 * @author dbattams
 *
 */
public class Exe extends Command {
	static private final Logger LOG = Logger.getLogger(Exe.class);
	
	/**
	 * @param in
	 * @param out
	 */
	public Exe(ObjectInputStream in, ObjectOutputStream out) {
		super(in, out);
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see com.google.code.sagetvaddons.sjq.listener.Command#execute()
	 */
	@Override
	public void execute() throws IOException {
		try {
			QueuedTask qt = (QueuedTask)getIn().readObject();
			LOG.info("Received task " + qt.getQueueId() + " of type '" + qt.getId() + "' from " + qt.getServerHost() + ":" + qt.getServerPort() + "...");
			getOut().writeObject(NetworkAck.get(NetworkAck.OK + QueuedTask.State.RUNNING.toString()));
			Thread t = new Thread(new ProcessRunner(qt));
			t.setName(ProcessRunner.genThreadName(qt));
			t.setDaemon(true);
			t.start();
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}
}
