package com.example.android.BluetoothChat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: hjshi84
 * Date: 13-12-11
 * Time: 下午4:54
 * To change this template use File | Settings | File Templates.
 */
public class RecData implements Serializable{
    TDVector accRecData;
    TDVector gyroRecData;
    int timestamp;
    /*0 is normal
    * 1 is fall
    * 2 is gatefind
    * */
    Byte actState;

    public RecData(){
        accRecData=new TDVector();
        gyroRecData=new TDVector();
    }

    public RecData(Byte[] temp) {
        if (temp.length!=14)
            return;
        accRecData=new TDVector();
        gyroRecData=new TDVector();
        accRecData.x=((temp[1]&0xff)<<8)|(temp[0]&0xff);
        accRecData.y=((temp[3]&0xff)<<8)|(temp[2]&0xff);
        accRecData.z=((temp[5]&0xff)<<8)|(temp[4]&0xff);
        gyroRecData.x=((temp[6]&0xff)<<8)|(temp[7]&0xff);
        gyroRecData.y=((temp[8]&0xff)<<8)|(temp[9]&0xff);
        gyroRecData.z=((temp[10]&0xff)<<8)|(temp[11]&0xff);
        this.timestamp = ((temp[13]&0xff)<<8)|(temp[12]&0xff);
        if (accRecData.x>32768) accRecData.x-=65536;
        if (accRecData.y>32768) accRecData.y-=65536;
        if (accRecData.z>32768) accRecData.z-=65536;
        if (gyroRecData.x>32768) gyroRecData.x-=65536;
        if (gyroRecData.y>32768) gyroRecData.y-=65536;
        if (gyroRecData.z>32768) gyroRecData.z-=65536;

        accRecData.x/=256;
        accRecData.y/=256;
        accRecData.z/=256;
        gyroRecData.x/=(30000/90);
        gyroRecData.y/=(30000/90);
        gyroRecData.z/=(30000/90);

        this.actState=GateDetect.GateDetectS(this);


    }

    private void saveData(RecData recData) {
        try {
            File file=new File("/mnt/sdcard/test2.txt");
            FileOutputStream fout=new FileOutputStream(file,true);
            byte[] bytes = recData.gyroRecData.toString().getBytes();
            fout.write(bytes);
            fout.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return accRecData + " "
                + gyroRecData + " "
                + timestamp +  " "
                + actState + "\n\t";
    }
    public class TDVector implements Serializable{
        float x;
        float y;
        float z;

        @Override
        public String toString() {
            /*return "{ x=" + x +
                    ", y=" + y +
                    ", z=" + z+" }";       */
            return x+" "+y+" "+z;
        }

        public float getSumValue(){
            return (float)Math.sqrt(x*x+y*y+z*z);
        }
    }
}
