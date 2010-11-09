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
package com.google.code.sagetvaddons.sjq.agent.network;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.google.code.sagetvaddons.sjq.listener.ListenerClient;
import com.google.code.sagetvaddons.sjq.listener.NetworkAck;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;

public final class ServerClient extends ListenerClient {
	static private final Logger LOG = Logger.getLogger(ServerClient.class);

	public ServerClient(String host, int port) throws IOException {
		super(host, port, ServerClient.class.getPackage().getName());
	}
	
	public NetworkAck update(QueuedTask qt) {
		NetworkAck ack = null;
		try {
			ack = sendCmd("UPDATE");
		} catch(IOException e) {
			LOG.error("IOError", e);
			setIsValid(false);
			return null;
		}
		if(ack != null && ack.isOk()) {
			try {
				getOut().writeObject(qt);
				return (NetworkAck)readObj();
			} catch (IOException e) {
				LOG.error("IOError", e);
				setIsValid(false);
				return NetworkAck.get(NetworkAck.ERR + e.getMessage());
			}
		}
		return NetworkAck.get(NetworkAck.ERR + "Update command rejected by server!");
	}

	public NetworkAck logTaskOutput(QueuedTask qt, String output) {
		NetworkAck ack = null;
		try {
			ack = sendCmd("LOGEXE");
		} catch(IOException e) {
			LOG.error("IOError", e);
			setIsValid(false);
			return null;
		}
		if(ack != null && ack.isOk()) {
			try {
				getOut().writeObject(qt);
				getOut().writeUTF(output);
				getOut().flush();
				return (NetworkAck)readObj();
			} catch(IOException e) {
				LOG.error("IOError", e);
				setIsValid(false);
				return NetworkAck.get(NetworkAck.ERR + e.getMessage());
			}
		}
		return NetworkAck.get(NetworkAck.ERR + "LOGTASK command rejected by server!");		
	}
	
	public NetworkAck logTestOutput(QueuedTask qt, String output) {
		NetworkAck ack = null;
		try {
			ack = sendCmd("LOGTEST");
		} catch(IOException e) {
			LOG.error("IOError", e);
			setIsValid(false);
			return null;
		}
		if(ack != null && ack.isOk()) {
			try {
				getOut().writeObject(qt);
				getOut().writeUTF(output);
				getOut().flush();
				return (NetworkAck)readObj();
			} catch(IOException e) {
				LOG.error("IOError", e);
				setIsValid(false);
				return NetworkAck.get(NetworkAck.ERR + e.getMessage());
			}
		}
		return NetworkAck.get(NetworkAck.ERR + "LOGTEST command rejected by server!");
	}
}
