/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothChatService {
    private int NODENUMBER=2;
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
//    private AcceptThread mAcceptThread;
    private static ConnectThread[] mConnectThread;
    private static ConnectedThread[] mConnectedThread;
    private static Byte whoToConn;
    private static int[] mState;
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    // Data Process
    private dataProc[] myDataProc=new dataProc[NODENUMBER];

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothChatService(Context context, Handler handler,ArrayList<MovValue> DataCollection) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState=new int[NODENUMBER];
        whoToConn=0;
        mConnectThread=new ConnectThread[NODENUMBER];
        mConnectedThread=new ConnectedThread[NODENUMBER];
        for(int i=0;i<NODENUMBER;i++){
            myDataProc[i]=new dataProc();
            myDataProc[i].setNode(i);
            myDataProc[i].start();
            mState[i]=STATE_NONE;
        }


        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state,int node) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState[node] = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState(int node) {
        return mState[node];
    }
    public synchronized int getState() {
        for (int i=0;i<NODENUMBER;i++){
            if (mState[i]!=STATE_NONE){
                return getState(i);
            }
        }
        return STATE_NONE;
    }
    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    /*public synchronized void start(int node) {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
//        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to listen on a BluetoothServerSocket
//        if (mAcceptThread == null) {
        AcceptThread  mAcceptThread = new AcceptThread(node);
           mAcceptThread.start();
//        }
        setState(STATE_LISTEN,node);
    } */

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);
        for(int i=0;i<mState.length;i++){
            if (mState[i]==STATE_NONE){
                if (mConnectThread[i]!=null)
                {mConnectThread[i].cancel(); mConnectThread[i] = null;}
                if (mConnectedThread[i] != null)
                {mConnectedThread[i].cancel(); mConnectedThread[i] = null;}
                mConnectThread[i] = new ConnectThread(device,i);
                mConnectThread[i].start();
                setState(STATE_CONNECTING,i);
                return;
            }
        }
        if (mConnectThread[whoToConn]!=null)  {mConnectThread[whoToConn].cancel(); mConnectThread[whoToConn] = null;}
        if (mConnectedThread[whoToConn] != null) {mConnectedThread[whoToConn].cancel(); mConnectedThread[whoToConn] = null;}
        mConnectThread[whoToConn] = new ConnectThread(device,whoToConn);
        mConnectThread[whoToConn].start();
        setState(STATE_CONNECTING,whoToConn);
        whoToConn++;
        whoToConn=whoToConn>NODENUMBER? 0:whoToConn;
        return;

    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device,int node) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread[node] != null) {mConnectThread[node].cancel(); mConnectThread[node] = null;}

        // Cancel any thread currently running a connection
       if (mConnectedThread[node] != null) {mConnectedThread[node].cancel(); mConnectedThread[node] = null;}


        // Start the thread to manage the connection and perform transmissions
        mConnectedThread[node] = new ConnectedThread(socket,node);
        mConnectedThread[node].start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_CONNECTED,node);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop(int node) {
        if (D) Log.d(TAG, "stop");
        for(int i=0;i<NODENUMBER;i++){
            if (mConnectThread[i] != null) {mConnectThread[i].cancel(); mConnectThread[i] = null;}
            if (mConnectedThread[i] != null) {mConnectedThread[i].cancel(); mConnectedThread[i] = null;}
        }
//        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        setState(STATE_NONE,node);
    }
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        for(int i=0;i<NODENUMBER;i++)  {
           if (mConnectThread[i] != null) {mConnectThread[i].cancel(); mConnectThread[i] = null;}
           if (mConnectedThread[i] != null) {mConnectedThread[i].cancel(); mConnectedThread[i] = null;}
           setState(STATE_NONE,i);
        }
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out,int node) {
        // Create temporary object
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState[node] != STATE_CONNECTED) return;
            r = mConnectedThread[node];
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed(int node) {
        setState(STATE_NONE,node);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost(int node) {
        setState(STATE_NONE,node);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private int myNode;
        public AcceptThread(int node) {
            BluetoothServerSocket tmp = null;
            myNode=node;
            // Create a new listening server socket
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState[myNode] != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothChatService.this) {
                        switch (mState[myNode]) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice(),myNode);
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private int myNode;
        public ConnectThread(BluetoothDevice device,int node) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            myNode=node;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed(myNode);
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                //BluetoothChatService.this.start(myNode);
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                mConnectThread[myNode] = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice,myNode);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private int myNode;
        public ConnectedThread(BluetoothSocket socket,int node) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            myNode=node;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

        }


        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            int bytes;
            //byte[] buffer=new byte[2048];
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    byte[] buffer=new byte[8192];
                    bytes = mmInStream.read(buffer);
                    addData(buffer,bytes,myNode);
                    //mHandler.obtainMessage(BluetoothChat.MESSAGE_READ,bytes,myNode,buffer).sendToTarget();

                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost(myNode);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    //Add value to the memory
    private void addData(byte[] readBuf,int arg1,int arg2){
        synchronized (BluetoothChat.gotData.get(arg2)){
            for(int i=0;i<arg1;i++)
            {
                BluetoothChat.gotData.get(arg2).add(readBuf[i]);
            }
        }
    }

    private class dataProc extends Thread {
        private int myNode;
        int delLoc = -1;
        private void setNode(int node){
            myNode=node;
        }


        private void procData(int dataLength,int arg2){
            delLoc = -1;
            synchronized (BluetoothChat.gotData.get(arg2))
            {
            for (int i=0;i<BluetoothChat.gotData.get(arg2).size()-dataLength;i++)
            {
                if (BluetoothChat.gotData.get(arg2).get(i)==65&&BluetoothChat.gotData.get(arg2).get(i+dataLength-1)==90)
                {
                    boolean sign=true;
                    for (MovValue anAllData : BluetoothChat.allData) {
                        if (anAllData.nodeNum == BluetoothChat.gotData.get(arg2).get(i + dataLength - 2)) {
                            synchronized (anAllData)
                            {
                                Byte[] abc = new Byte[dataLength - 3];
                                BluetoothChat.gotData.get(arg2).subList(i+1,i+dataLength-2).toArray(abc);
                                RecData temp = new RecData(abc);
                                anAllData.addValue(temp);
                                sign=false;
                                if(anAllData.nodeNum==BluetoothChat.myBCConfig.GateDetect)  {
                                    if ((temp.actState & 0x01)> 0) BluetoothChat.fallCount++;
                                    if ((temp.actState & 0x02)> 0) BluetoothChat.gateCount++;
                                }
                                BluetoothChat.myIMU.get(anAllData.nodeNum).IMUX=(temp.gyroRecData.x);
                                BluetoothChat.myIMU.get(anAllData.nodeNum).IMUY=(temp.gyroRecData.y);
                                BluetoothChat.myIMU.get(anAllData.nodeNum).IMUZ=(temp.gyroRecData.z);

                            }
                            break;
                        }
                    }
                    if (sign)
                    {
                        if (BluetoothChat.allData.size()<BluetoothChat.NODEMAX)
                        {
                            Byte[] abc = new Byte[dataLength-3];
                            BluetoothChat.gotData.get(arg2).subList(i+1,i+dataLength-2).toArray(abc);
                            synchronized (BluetoothChat.allData)
                            {
                                MovValue temp=new MovValue(BluetoothChat.gotData.get(arg2).get(i+dataLength-2));
                                temp.nodeNum=BluetoothChat.gotData.get(arg2).get(i + dataLength - 2);
                                temp.addValue(new RecData(abc));
                                BluetoothChat.allData.add(BluetoothChat.gotData.get(arg2).get(i+dataLength-2),temp);
                            }
                        }
                    }
                    i+=dataLength-1;
                    delLoc=i;
                }
            }

            for (int i=0;i<=delLoc;i++)
            {
                BluetoothChat.gotData.get(arg2).remove(0);
            }
            }
        }

        @Override
        public void run() {
            while(true){
                try {
                    procData(17,myNode);
                    sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
    }

}