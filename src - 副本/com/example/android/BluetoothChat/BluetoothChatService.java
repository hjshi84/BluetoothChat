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
    private static final double LFT=0.65*9.8;
    private static final double UFT=2.0*9.8;

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
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothChatService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
//        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to listen on a BluetoothServerSocket
//        if (mAcceptThread == null) {
        AcceptThread   mAcceptThread = new AcceptThread();
           mAcceptThread.start();
//        }
        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
 //       if (mState == STATE_CONNECTING) {
 //           if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
 //       }

        // Cancel any thread currently running a connection
 //       if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
//        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
//       if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
//        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
//        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
//        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
//        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);

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
    private void connectionLost() {
        setState(STATE_LISTEN);

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

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

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
            while (mState != STATE_CONNECTED) {
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
                        switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice());
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

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

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
                connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                BluetoothChatService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
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
        private int dateCyc;
        private checkfall mcheckfall;
        private checkgate mcheckgate;
        private AccRecord mAccRecord;
        private GyroRecord mGyroRecord;
        byte[] sendDate = new byte[1024];
        AccValue temp;
        GyroValue tempgyro;
        AccValue lasttemp;
        GyroValue lastgyro;
        ArrayList<Long> abcd=new ArrayList<Long>();
        float sumX=0,sumY=0,sumZ=0,num=0;
        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            mcheckfall=new checkfall();
            mcheckgate=new checkgate(0.05f);
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            mAccRecord=new AccRecord();
            mGyroRecord=new GyroRecord();
            num=0;
        }

        /*private void GlideFilterAD(int n){
            int loca=0;
            loca=mAccRecord.getlastloca();
            sumX+=mAccRecord.mAccValue[loca].xacc;
            sumY+=mAccRecord.mAccValue[loca].yacc;
            sumZ+=mAccRecord.mAccValue[loca].zacc;
            if(num==n)  {
                AccValue temp=new AccValue();
                temp.xacc=sumX/n;
                temp.yacc=sumY/n;
                temp.zacc=sumZ/n;
                temp.timestamp=mAccRecord.mAccValue[loca].timestamp;
                temp.lasttime=mAccRecord.mAccValue[loca].lasttime;
                mAccRecord.mAccValue[loca]=temp;
                num=-1;
            }else if (num==-1) {
                AccValue temp=new AccValue();
                if (loca-n+1>0)      {
                    sumX-=mAccRecord.mAccValue[loca-n+1].xacc;
                    sumY-=mAccRecord.mAccValue[loca-n+1].yacc;
                    sumZ-=mAccRecord.mAccValue[loca-n+1].zacc;
                }
                else{
                    sumX-=mAccRecord.mAccValue[999+loca-n+1].xacc;
                    sumY-=mAccRecord.mAccValue[999+loca-n+1].yacc;
                    sumZ-=mAccRecord.mAccValue[999+loca-n+1].zacc;
                }
                temp.xacc=sumX/n;
                temp.yacc=sumY/n;
                temp.zacc=sumZ/n;
                temp.timestamp=mAccRecord.mAccValue[loca].timestamp;
                temp.lasttime=mAccRecord.mAccValue[loca].lasttime;
                mAccRecord.mAccValue[loca]=temp;
            }else if (num<n){
                num++;
            }
        }   */

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
//            byte[] sendDate=new byte[1024];
            int totalbytes=0;
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Judge if fall happened
                    for(int i=0;i<bytes;i++){
                        if (buffer[i]==65&&buffer[i+15]==90){

                            temp=new AccValue();
                            temp.xacc=((buffer[i+2]&0xff)<<8)|(buffer[i+1]&0xff);
                            temp.yacc=((buffer[i+4]&0xff)<<8)|(buffer[i+3]&0xff);
                            temp.zacc=((buffer[i+6]&0xff)<<8)|(buffer[i+5]&0xff);
                            if (temp.xacc>32766) temp.xacc=-65536+temp.xacc;
                            if (temp.yacc>32766) temp.yacc=-65536+temp.yacc;
                            if (temp.zacc>32766) temp.zacc=-65536+temp.zacc;
                            temp.xacc= (float) (temp.xacc*3.9/100);
                            temp.yacc= (float) (temp.yacc*3.9/100);
                            temp.zacc= (float) (temp.zacc*3.9/100);
                            temp.timestamp=((buffer[i+14]&0xff)<<8)|(buffer[i+13]&0xff);
                            
                            //if(temp.getSumValue()<100){// 幅� 滤波算法+递推平均滤波算法
                            	mAccRecord.adddate(temp);
                            //}else{
                            //	if (lasttemp!=null)
                            //		mAccRecord.adddate(lasttemp);
                            //}
                            
                            
                            
                            tempgyro=new GyroValue();
                            tempgyro.xgyro= (((buffer[i+7]&0xff)<<8)|(buffer[i+8]&0xff));
                            tempgyro.ygyro= (((buffer[i+9]&0xff)<<8)|(buffer[i+10]&0xff));
                            tempgyro.zgyro= (((buffer[i+11]&0xff)<<8)|(buffer[i+12]&0xff));
                            if (tempgyro.xgyro>32766) tempgyro.xgyro=-65536+tempgyro.xgyro;
                            if (tempgyro.ygyro>32766) tempgyro.ygyro=-65536+tempgyro.ygyro;
                            if (tempgyro.zgyro>32766) tempgyro.zgyro=-65536+tempgyro.zgyro;
                            tempgyro.xgyro=(float)((tempgyro.xgyro)/14.375);
                            tempgyro.ygyro=(float)((tempgyro.ygyro)/14.375);
                            tempgyro.zgyro=(float)((tempgyro.zgyro)/14.375);
                            tempgyro.timestamp=((buffer[i+14]&0xff)<<8)|(buffer[i+13]&0xff);

                            mGyroRecord.adddate(tempgyro);


                           // GlideFilterAD(10);
                            
                            int accnum=mAccRecord.getlastloca();
                            mcheckfall.setNowvalue(mAccRecord.mAccValue[accnum]);

                            //if(mcheckgate.judegateornot(mAccRecord.mAccValue[accnum])==1){
                            //    mHandler.obtainMessage(BluetoothChat.MESSAGE_GATE).sendToTarget();
                            //}
                            long tempvalue;
                            //if ((tempvalue=mcheckfall.checkfallornot())>0){
                            //    mAccRecord.mAccValue[accnum].lasttime=tempvalue;
                            //    mHandler.obtainMessage(BluetoothChat.MESSAGE_FALL).sendToTarget();
                                mHandler.obtainMessage(BluetoothChat.MESSAGE_DRAW,1,1,mAccRecord.mAccValue[accnum]).sendToTarget();
                            //mHandler.obtainMessage(BluetoothChat.MESSAGE_DRAW,1,1,mGyroRecord.mGyroValue[accnum]).sendToTarget();
                           // }else{

                            //    mHandler.obtainMessage(BluetoothChat.MESSAGE_DRAW,0,0,mAccRecord.mAccValue[accnum]).sendToTarget();
                            //}
                            mHandler.obtainMessage(BluetoothChat.MESSAGE_GATE,0,0,mGyroRecord.mGyroValue[accnum]).sendToTarget();
                            //mHandler.obtainMessage(BluetoothChat.MESSAGE_GATE,0,0,mAccRecord.mAccValue[accnum]).sendToTarget();
                            lasttemp=temp;
                            lastgyro=tempgyro;
                            i+=15;
                        }
                    }

                    /////System.arraycopy(buffer,0,mybyte,0,bytes);
                    ////String ReadDate[]=new String(mybyte,0,bytes).split(",");
                    /*if(ReadDate.length==7){
                        AccValue temp=new AccValue();
                        temp.xacc=Float.valueOf(ReadDate[0]);
                        temp.yacc=Float.valueOf(ReadDate[1]);
                        temp.zacc=Float.valueOf(ReadDate[2]);
                        temp.timestamp=Long.valueOf(ReadDate[6])*10;
                        mcheckfall.setNowvalue(temp);
                        if (mcheckfall.checkfallornot()){
                            mHandler.obtainMessage(BluetoothChat.MESSAGE_FALL).sendToTarget();
                        }
                    }*/
                    // Send the date every five times
                    /*dateCyc++;
                    if(dateCyc==5){
                    // Send the obtained bytes to the UI Activity
                        System.arraycopy(buffer,0,sendDate,totalbytes,bytes);
                        totalbytes+=bytes;
                        mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, totalbytes, -1, sendDate)
                            .sendToTarget();
                        totalbytes=0;
                        dateCyc=0;
                    }else{
                        System.arraycopy(buffer,0,sendDate,totalbytes,bytes);
                        totalbytes+=bytes;

                    }*/
                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
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



    public class AccValue{
        float xacc;
        float yacc;
        float zacc;
        long timestamp;
        long lasttime=0;

        @Override
        public String toString() {
            return "AccValue{" +
                    "xacc=" + xacc +
                    ", yacc=" + yacc +
                    ", zacc=" + zacc +
                    ", timestamp=" + timestamp +
                    ", lasttime=" + lasttime +
                    '}';
        }

        public double getSumValue(){
            return StrictMath.sqrt(xacc*xacc+yacc*yacc+zacc*zacc);
        }


    }

    public class AccRecord{
        AccValue mAccValue[];
        int start;
        int end;

        AccRecord(){
            // TODO Auto-generated method stub
            mAccValue=new AccValue[1000];
            start=0;
            end=0;
        }

        public void adddate(AccValue accValue){
            if (end>=999){
                mAccValue[end]=accValue;
                end=0;
                start=1;

            }else if(start>end){
                mAccValue[end]=accValue;
                end++;
                start++;

            }else{
                mAccValue[end]=accValue;
                end++;

            }

        }
        
        public int getlastloca(){
         	int lastloca=end-1;
         	if (lastloca<0) lastloca=999;
         	return lastloca;
        	
        }
    }
    
    public class GyroRecord{
    	GyroValue mGyroValue[];
        int start;
        int end;

        GyroRecord(){
            // TODO Auto-generated method stub
        	mGyroValue=new GyroValue[1000];
            start=0;
            end=0;
        }

        public void adddate(GyroValue gyroValue){
            if (end>=999){
            	mGyroValue[end]=gyroValue;
                end=0;
                start=1;

            }else if(start>end){
            	mGyroValue[end]=gyroValue;
                end++;
                start++;

            }else{
            	mGyroValue[end]=gyroValue;
                end++;

            }

        }
        
        public int getlastloca(){
         	int lastloca=end-1;
         	if (lastloca<0) lastloca=999;
         	return lastloca;
        	
        }
    }

    public class GyroValue{
        float xgyro;
        float ygyro;
        float zgyro;
        long timestamp;

        @Override
        public String toString() {
            return "GyroValue{" +
                    "xgyro=" + xgyro +
                    ", ygyro=" + ygyro +
                    ", zgyro=" + zgyro +
                    ", timestamp=" + timestamp +
                    '}';
        }
        
        public double getsumvalue(){
        	return Math.sqrt(xgyro*xgyro+ygyro*ygyro+zgyro*zgyro);
        }

    }

    public class AllValue {
        AccValue myacc;
        GyroValue mygyro;
    }

    private class checkfall{
        AccValue nowvalue;
        AccValue laststate=new AccValue();
        boolean startfall=false;
        boolean midfall=false;
        boolean endfall=false;
        AccValue startACC;
        AccValue midACC;
        AccValue endACC;


        private void setNowvalue(AccValue maccvalue){
            nowvalue=maccvalue;
        }

        private int judgepoint(AccValue maccvalue){
            if(laststate.getSumValue()>LFT&&nowvalue.getSumValue()<LFT){
                startACC=maccvalue;
                startfall=true;
                midfall=false;
                endfall=false;
            }else if(startfall&&nowvalue.getSumValue()>LFT&&laststate.getSumValue()<LFT&&!midfall)    {
                midACC=maccvalue;
                midfall=true;
            }else if (startfall&&midfall&&nowvalue.getSumValue()>UFT){
                endACC=maccvalue;
                endfall=true;
            }

            return 0;
        }

        private void cleardata(){
            startfall=false;
            midfall=false;
            endfall=false;
        }
        private long checkfallornot(){
            judgepoint(nowvalue);
            laststate=nowvalue;
            if(endfall){
                if(endACC.timestamp-startACC.timestamp<65&&midACC.timestamp-startACC.timestamp<35){
                    cleardata();
                    return startACC.timestamp;
                }
            }
            return 0;
        }
    }

    public class checkgate{
       // AccValue sample_result;
        AccValue sample_new;
        AccValue sample_old=null;
        AccValue lastvalue=null;
        float dynamicthreshold=8.8f;
        float precise;
        long lastgatetime;
        int num=0;
        int calDynamic;
        float dynamicvalue;

        public checkgate(float precise) {
            this.precise = precise;
        }

        public void setnewsample(AccValue temp){
            sample_new=temp;
        }

        private int prejudge(AccValue temp){
            if(lastvalue==null){
                lastvalue=temp;
                return 0;
            }
            if(Math.abs(temp.getSumValue()-lastvalue.getSumValue())>precise){
                sample_new=temp;
                lastvalue=temp;
                return 1;
            }else{
                lastvalue=temp;
                return 0;
            }
        }

        private int judegateornot(AccValue temp){
            if(num>=2000){
                num=0;
                clearlastgatetime();
            }
            sample_old=sample_new;
            if(prejudge(temp)==1&&sample_old!=null){
                if(sample_old.getSumValue()>dynamicthreshold&&sample_new.getSumValue()<dynamicthreshold&&Math.abs(sample_new.timestamp-lastgatetime)>20)   {
                    lastgatetime=sample_new.timestamp;
                    num=0;
                    return 1;
                }
                else {
                    num++;
                    return 0;
                }

            }
            else  {
                num++;
                return 0;
            }
        }

        private void clearlastgatetime(){
             lastgatetime=0;
        }

        private void generateDynamicThres(AccValue temp){

        }
    }

    public class inclination{
        int w=0;
        float tempf=0.0f;
        int signRzGyro;
        AccValue maccvalue;
        GyroValue mgyrovalue;


        public inclination(AccValue maccvalue, GyroValue mgyrovalue) {
            this.maccvalue = maccvalue;
            this.mgyrovalue = mgyrovalue;
        }
        
        public void normalizedata(){
        	AccValue temp=new AccValue();
        	temp.xacc=(float)(maccvalue.xacc/maccvalue.getSumValue());
        	temp.yacc=(float)(maccvalue.yacc/maccvalue.getSumValue());
        	temp.zacc=(float)(maccvalue.zacc/maccvalue.getSumValue());
        	temp.lasttime=maccvalue.lasttime;
        	temp.timestamp=maccvalue.timestamp;
        	maccvalue=temp;
        }
        
        public GyroValue getinclination(){
        	
        	
        	return null;
        	
        }
    }
}