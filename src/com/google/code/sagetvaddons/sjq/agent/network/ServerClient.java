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
	
	public NetworkAck setTaskResources(QueuedTask qt, int used) {
		NetworkAck ack = null;
		try {
			ack = sendCmd("SETTASKRES");
		} catch(IOException e) {
			setIsValid(false);
			return null;
		}
		if(ack != null && ack.isOk()) {
			try {
				getOut().writeObject(qt);
				getOut().writeInt(used);
				getOut().flush();
				return (NetworkAck)readObj();
			} catch(IOException e) {
				LOG.error("IOError", e);
				setIsValid(false);
				return NetworkAck.get(NetworkAck.ERR + e.getMessage());
			}
		}
		return NetworkAck.get(NetworkAck.ERR + "Set task resources command rejected by server!");
	}
	
	public String getExeArgs(QueuedTask qt) {
		NetworkAck ack = null;
		try {
			ack = sendCmd("GETARGS");
		} catch(IOException e) {
			setIsValid(false);
			return null;
		}
		if(ack != null && ack.isOk()) {
			try {
				getOut().writeLong(qt.getQueueId());
				getOut().flush();
				String args = getIn().readUTF();
				ack = (NetworkAck)readObj();
				if(ack == null || !ack.isOk()) {
					LOG.error("Received invalid ACK from GETARGS command!");
					setIsValid(false);
					return null;
				}
				return args;
			} catch(IOException e) {
				LOG.error("IOError", e);
				setIsValid(false);
				return null;
			}
		}
		return null;
	}
	
	public NetworkAck setExeArgs(QueuedTask qt, String args) {
		NetworkAck ack = null;
		try {
			ack = sendCmd("SETARGS");
		} catch(IOException e) {
			setIsValid(false);
			return null;
		}
		if(ack != null && ack.isOk()) {
			try {
				getOut().writeLong(qt.getQueueId());
				getOut().writeUTF(args);
				getOut().flush();
				return (NetworkAck)readObj();
			} catch(IOException e) {
				LOG.error("IOError", e);
				setIsValid(false);
				return NetworkAck.get(NetworkAck.ERR + e.getMessage());
			}
		}
		return NetworkAck.get(NetworkAck.ERR + "Update command rejected by server!");
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

	private void writeChunkedString(String output) throws IOException {
		final int MAX_CHUNK = 60000;
		int chunks = output.length() / MAX_CHUNK;
		if(output.length() % MAX_CHUNK != 0)
			++chunks;
		getOut().writeInt(chunks);
		for(int i = 0; i < chunks; ++i) {
			int start = i * MAX_CHUNK;
			int end = start + MAX_CHUNK;
			if(end > output.length())
				end = output.length();
			String chunk = output.substring(start, end);
			getOut().writeUTF(chunk);
		}
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
				writeChunkedString(output);
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
				writeChunkedString(output);
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
