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

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

import static com.example.android.BluetoothChat.BluetoothChatService.*;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity {

    //config
    private static BCConfig myBCConfig=new BCConfig();

    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_UPDATE=6;

    //Node
    public static final int NODEMAX=10;
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    // Picture Surface
    private SurfaceView picSurfaceView;
    private SurfaceHolder picSurfaceHolder;
    // Gate Para and Eular
    private int gateCount;
    private int fallCount;
    private ArrayList<IMU> myIMU=new ArrayList<IMU>(NODEMAX);

    //Alert and Warning
    private Timer alertTimer=null;
    private LocationManager lm;
    public double lat;						//latitude and Longitude of the location
    public double lng;
    public String emernum="13501712009";//the emergency phone number
    public String connectperson="8613501712009";//The person smartphone number who will receive the message
    //DATA
    public static ArrayList<MovValue> allData=new ArrayList<MovValue>(NODEMAX);
    private ArrayList<Byte> gotData=new ArrayList<Byte>(256);


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        //draw picture
        picSurfaceView=(SurfaceView)this.findViewById(R.id.picsurfaceView);

        picSurfaceHolder=picSurfaceView.getHolder();
        picSurfaceHolder.addCallback(new picholdercallback());
        picSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Gate
        myIMU.add(new IMU());
        // Warning
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }


    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == STATE_NONE) {
              // Start the Bluetooth chat services
              ///////mChatService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler,allData);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");

    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState(0) != STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send,0);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                    case STATE_CONNECTED:
                        mTitle.setText(R.string.title_connected_to);
                        mTitle.append(mConnectedDeviceName);
                        mConversationArrayAdapter.clear();
                        break;
                    case STATE_CONNECTING:
                        mTitle.setText(R.string.title_connecting);
                        break;
                    case STATE_LISTEN:
                    case STATE_NONE:
                        mTitle.setText(R.string.title_not_connected);
                        break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    //synchronized (allData)
                    //{
                        procData(17, readBuf, msg.arg1);
                    //}
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                                   + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                                   Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_UPDATE:
                    if (msg.arg1==1)
                    {
                        //updatepicsurfaceholder();
                    }
                    break;
            }
        }


    };



    private void procData(int dataLength, byte[] readBuf, int arg1) {
        //Log.e("pic","null1");
        int delLoc = -1;
        if (gotData==null) gotData=new ArrayList<Byte>(NODEMAX);
        if (allData==null) allData=new ArrayList<MovValue>(256);
            for(int i=0;i<arg1;i++)
            {
                gotData.add(readBuf[i]);
            }
            for (int i=0;i<gotData.size()-dataLength;i++)
            {
                if (gotData.get(i)==65&&gotData.get(i+dataLength-1)==90)
                {
                    boolean sign=true;
                        for (MovValue anAllData : allData) {
                            if (anAllData.nodeNum == gotData.get(i + dataLength - 2)) {
                                synchronized (anAllData)
                                {
                                Byte[] abc = new Byte[dataLength - 3];
                                gotData.subList(i+1,i+dataLength-2).toArray(abc);
                                RecData temp = new RecData(abc);
                                anAllData.addValue(temp);Log.e("pic",temp.toString());
                                sign=false;
                                if ((temp.actState & 0x01)> 0) fallCount++;
                                if ((temp.actState & 0x02)> 0) gateCount++;
                                //myIMU.get(anAllData.nodeNum).IMUX=(temp.gyroRecData.x);
                                //myIMU.get(anAllData.nodeNum).IMUY=(temp.gyroRecData.y);
                                //myIMU.get(anAllData.nodeNum).IMUZ=(temp.gyroRecData.z);
                                    myIMU.get(0).IMUX=(temp.gyroRecData.x);
                                    myIMU.get(0).IMUY=(temp.gyroRecData.y);
                                    myIMU.get(0).IMUZ=(temp.gyroRecData.z);
                                }
                                break;
                            }
                        }
                        if (sign)
                        {
                            if (allData.size()<NODEMAX)
                            {
                                Byte[] abc = new Byte[dataLength-3];
                                gotData.subList(i+1,i+dataLength-2).toArray(abc);
                                synchronized (allData)
                                {
                                MovValue temp=new MovValue(gotData.get(i+dataLength-2));
                                temp.addValue(new RecData(abc));
                                allData.add(temp);
                                }
                            }
                        }

                    i+=dataLength-1;
                    delLoc=i;
                }
            }
            for (int i=0;i<=delLoc;i++)
            {
                gotData.remove(0);
            }
        //Log.e("pic","null2");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            myIMU.get(0).IMUX=0;
            myIMU.get(0).IMUY=0;
            myIMU.get(0).IMUZ=0;
            new Thread(new Runnable() {
                @Override
                public void run() {
                       while(true){
                           try{
                                updatepicsurfaceholder();
                                Thread.sleep(50);
                           }catch(Exception e){
                               e.printStackTrace();
                           }
                       }
                }
            }).start();
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        case R.id.config:
            config();
            return true;
        }
        return false;
    }

    private void config() {
        //To change body of created methods use File | Settings | File Templates.
        AlertDialog cfgAlert = new AlertDialog.Builder(this).create();
        cfgAlert.show();
        cfgAlert.getWindow().setContentView(R.layout.config);
        LayoutInflater mylayout=LayoutInflater.from(this);
        View myview=mylayout.inflate(R.layout.config,null);
        GridView gv=(GridView)findViewById(R.id.gridView);
        int[] images=new int[]{};
        String[] texts = new String[]{ "提醒设置","待定1","待定2","待定3"};

    }

    private class picholdercallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            //To change body of implemented methods use File | Settings | File Templates.
            InitPic(surfaceHolder);

        }

        private void InitPic(SurfaceHolder holder) {
            //To change body of created methods use File | Settings | File Templates.
            Canvas mCanvas=holder.lockCanvas();
            //	mCanvas.drawColor(Color.WHITE);
            //	Bitmap mbitmap = Bitmap.createBitmap(950, 150, Bitmap.Config.ARGB_8888);
            //	mCanvas.setBitmap(mbitmap);
            int width=mCanvas.getWidth();
            int height=mCanvas.getHeight();
            if(width>950) width=950;
            if(height>550) height=550;
            Paint mPaint=new Paint();
            mPaint.setColor(Color.BLUE);
            mCanvas.drawARGB(255, 255, 255, 255);
            mCanvas.drawLine(25, 25, 25, height-25, mPaint);
            mCanvas.drawLine(25, height-25, width-25, height-25, mPaint);
            Path mPath=new Path();
            mPath.moveTo(20, 25);
            mPath.lineTo(30, 25);
            mPath.lineTo(25, 20);
            mPath.close();
            mPath.moveTo(width-25, height-30);
            mPath.lineTo(width-25, height-20);
            mPath.lineTo(width-20, height-25);
            mPath.close();
            mCanvas.drawPath(mPath,mPaint);
            holder.unlockCanvasAndPost(mCanvas);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
            //To change body of implemented methods use File | Settings | File Templates.
            InitPic(surfaceHolder);

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            //To change body of implemented methods use File | Settings | File Templates.

        }
    }

    public static Object copy(Object oldobject){
        Object o=null;
        if (oldobject==null) return o;
        try {
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            ObjectOutputStream out=new ObjectOutputStream(bos);
            out.writeObject(oldobject);
            out.flush();
            out.close();

            ByteArrayInputStream bis=new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream in=new ObjectInputStream(bis);
            o=in.readObject();

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ClassNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return o;
    }

    private int updatepicsurfaceholder(){
        Canvas mCanvas=picSurfaceHolder.lockCanvas();

        if (mCanvas==null) return 0;
        int width=mCanvas.getWidth();
        int height=mCanvas.getHeight();
        if(width>950) width=950;
        if(height>550) height=550;
        Paint mPaint=new Paint();
        mPaint.setColor(Color.BLUE);
        mCanvas.drawARGB(255, 255, 255, 255);
        mCanvas.drawLine(25, 25, 25, height-25, mPaint);
        mCanvas.drawLine(25, height-25, width-25, height-25, mPaint);
        Path mPath=new Path();
        mPath.moveTo(20, 25);
        mPath.lineTo(30, 25);
        mPath.lineTo(25, 20);
        mPath.close();
        mPath.moveTo(width-25, height-30);
        mPath.lineTo(width-25, height-20);
        mPath.lineTo(width-20, height-25);
        mPath.close();
        mCanvas.drawPath(mPath,mPaint);
        //LinkedList<MovValue> temp=new LinkedList<MovValue>();


        for (int j:myBCConfig.drawNode)
        {
                for(MovValue k:allData)
                {
                    synchronized (k)
                    {
                    if (k.nodeNum==j)
                    {
                        double min=-3,max=3;

                /*for(int i=0;i<temp.num;i++){
                    if (min>temp.myAccValue.get(i).getSumValue()){
                        min=temp.myAccValue.get(i).getSumValue();
                        continue;
                    }
                    if (max<temp.myAccValue.get(i).getSumValue()){
                        max=temp.myAccValue.get(i).getSumValue();
                    }
                } */
                        float detaY=(float)(max-min)/(height-50);
                        float detaX=(float) (900.0/(width-50));
                        int num=0;
                        for(RecData i:allData.get(j).movValue)
                        {
                            num++;
                            if (myBCConfig.drawAcc)
                            {
                                mPaint.setColor(Color.BLACK);
                                mCanvas.drawCircle(25+num/detaX,(float)(height-25-(i.accRecData.x-min)/detaY),2,mPaint);
                                mPaint.setColor(Color.RED);
                                mCanvas.drawCircle(25+num/detaX,(float)(height-25-(i.accRecData.y-min)/detaY),2,mPaint);
                                mPaint.setColor(Color.GREEN);
                                mCanvas.drawCircle(25+num/detaX,(float)(height-25-(i.accRecData.z-min)/detaY),2,mPaint);
                            }
                            if (myBCConfig.drawGyro)
                            {
                                mPaint.setColor(Color.YELLOW);
                                mCanvas.drawCircle(25+num/detaX,(float)(height-25-(i.gyroRecData.x-min)/detaY),2,mPaint);
                                mPaint.setColor(Color.BLUE);
                                mCanvas.drawCircle(25+num/detaX,(float)(height-25-(i.gyroRecData.y-min)/detaY),2,mPaint);
                                mPaint.setColor(Color.LTGRAY);
                                mCanvas.drawCircle(25+num/detaX,(float)(height-25-(i.gyroRecData.z-min)/detaY),2,mPaint);
                            }

                        }
                    }
                    }
                }

        }
            mPaint.setColor(Color.BLACK);
            mPaint.setTextSize(25);
            mCanvas.drawText(myIMU.toString(),400,200,mPaint);

        //temp.clear();
        // Draw gate and fall
        mPaint.setColor(Color.BLUE);
        mCanvas.drawText("Fall: "+fallCount,500,300,mPaint);
        mCanvas.drawText("Gate: "+gateCount,500,400,mPaint);

        picSurfaceHolder.unlockCanvasAndPost(mCanvas);

        return 0;
    }



    private class Kalman{
        private double covread;
        private double covesti;
        private double kg;

        private double uncertain;

        private Kalman(double covesti, double covread, double uncertain) {
            this.covesti = covesti;
            this.covread = covread;
            this.uncertain = uncertain;
        }

        private double CalCov(double arg1, double arg2){
            return Math.sqrt(arg1*arg1+arg2*arg2);
        }

        private double CalKg(double arg1,double arg2){
            return Math.sqrt(arg1*arg1/(arg1*arg1+arg2*arg2));
        }

        private double CalEsti(double arg1,double arg2,double kgvalue){
            return arg1+kgvalue*(arg2-arg1);
        }

        private void Estimate(){

        }
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            updateWithNewLocation(location);
        }

        public void onProviderDisabled(String provider) {
            updateWithNewLocation(null);
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private String updateWithNewLocation(Location location) {

        if (location != null) {
            lat = location.getLatitude();
            lng = location.getLongitude();
            //Toast.makeText(this,lat+":"+lng,Toast.LENGTH_SHORT).show();
            Log.i("获取地理信息","第一个为"+lat+"第二个为"+lng);
            return getGeoPoint(getLocationInfo(lat,lng));
            //Toast.makeText(this,abc,Toast.LENGTH_LONG).show();
        } else {
            Log.i("无法获取地理信息","无法获取地理信息");
            return null;
        }
    }

    public static JSONObject getLocationInfo(double lat,double lng) {

        HttpGet httpGet = new HttpGet("http://maps.googleapis.com/maps/api/geocode/json?latlng=" +lat+","+lng + "&sensor=false");
        HttpClient client = new DefaultHttpClient();
        HttpResponse response;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            response = client.execute(httpGet);
            HttpEntity entity = response.getEntity();
            InputStream stream = entity.getContent();
            int b;
            while ((b = stream.read()) != -1) {
                stringBuilder.append((char) b);
            }
        } catch (ClientProtocolException e) {
        } catch (IOException e) {
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject = new JSONObject(stringBuilder.toString());
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return jsonObject;
    }

    public static String getGeoPoint(JSONObject jsonObject) {

        String address;

        try {

            address = ((JSONArray)jsonObject.get("results")).getJSONObject(0)
                    .getString("formatted_address");
            return address;
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private void warningEvent(){
        if(alertTimer==null){
            alertTimer=new Timer();
            alertTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                // TODO Auto-generated method stub
                try{
                    Looper.prepare();

                    // 返回所有已知的位置提供者的名称列表，包括未获准访问或调用活动目前已停用的。
                    Criteria criteria = new Criteria();
                    criteria.setAccuracy(Criteria.ACCURACY_FINE);
                    criteria.setAltitudeRequired(false);
                    criteria.setBearingRequired(false);
                    criteria.setCostAllowed(true);
                    criteria.setPowerRequirement(Criteria.POWER_LOW);
                    String provider = lm.getBestProvider(criteria, true);
                    Location location = lm.getLastKnownLocation(provider);
                    while(location==null)          {
                        lm.requestLocationUpdates(provider, 1000, 0, locationListener);
                    }
                    String mes= updateWithNewLocation(location);

                    //Send SMS
                    /*SmsManager smsManager = SmsManager.getDefault();
                    List<String> divideContents = smsManager.divideMessage("HELP! I am in latitude"+lat+";longitude="+lng+"   "+mes);
                    for (String text : divideContents) {
                        smsManager.sendTextMessage(connectperson, null, text, null, null);
                    }    */

                    //Call emergency
                    Intent phoneIntent = new Intent("android.intent.action.CALL",

                            Uri.parse("tel:" + emernum));

                    startActivity(phoneIntent);
                    lm.removeUpdates(locationListener);


                }catch(Exception e){
                    Log.e("wrong", e.toString());
                    Toast.makeText(getBaseContext(), "there is something wrong,i can not send sms and call number", Toast.LENGTH_SHORT).show();

                }

                alertTimer.cancel();
                alertTimer.purge();
                alertTimer=null;
                System.gc();
                Looper.loop();

                }
            },1000);
        }
        //else{
        //    alertTimer.cancel();
        //    alertTimer=null;
        //}
    }
}