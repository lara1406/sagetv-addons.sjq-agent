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

import com.google.code.sagetvaddons.sjq.agent.Config;
import com.google.code.sagetvaddons.sjq.listener.Command;
import com.google.code.sagetvaddons.sjq.listener.NetworkAck;
import com.google.code.sagetvaddons.sjq.shared.Client;

/**
 * @author dbattams
 *
 */
public class Update extends Command {

	public Update(ObjectInputStream in, ObjectOutputStream out) {
		super(in, out);
	}

	@Override
	public void execute() throws IOException {
		Config cfg = Config.get();
		Client clnt = null;
		try {
			clnt = (Client)getIn().readObject();
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
		getOut().writeObject(NetworkAck.get(cfg.save(clnt) ? NetworkAck.OK : NetworkAck.ERR));
		getOut().flush();
	}
}