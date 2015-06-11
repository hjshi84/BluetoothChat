package com.example.android.BluetoothChat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: hjshi84
 * Date: 13-12-10
 * Time: 下午11:01
 * To change this template use File | Settings | File Templates.
 */
public class MovValue implements Serializable{
    static int MAXNUM=500;
    ArrayList<RecData> movValue;
    int nodeNum;

    public MovValue(int NodeNum) {
        movValue=new ArrayList<RecData>(MAXNUM);
        nodeNum=NodeNum;
    }

    public void addValue(RecData temp){
        for(int i = movValue.size()-1;i>= 0 ; i--){
            if (temp.timestamp>=65535){
                saveData(temp);
                movValue.clear();
                return;
            }
            if(temp.timestamp>=movValue.get(i).timestamp){
                movValue.add(i+1,temp);
                if (movValue.size()==MAXNUM){
                    movValue.remove(0);
                }
                saveData(temp);
                return;
            }
        }
        movValue.add(0,temp);
        if (movValue.size()>=MAXNUM){
            movValue.remove(0);
        }
    }

    private void saveData(RecData temp) {
        try{
            File file=new File("/mnt/sdcard/"+this.nodeNum+".txt");
            FileOutputStream fout=new FileOutputStream(file,true);
            byte[] bytes=temp.toString().getBytes();
            fout.write(bytes);
            fout.close();
        }catch (Exception e){

        }
    }

    private void saveData() {
        try {
            File file=new File("/mnt/sdcard/"+this.nodeNum+".txt");
            FileOutputStream fout=new FileOutputStream(file,true);
            for(RecData i:movValue)  {
                byte[] bytes;
                bytes = i.toString().getBytes();
                fout.write(bytes);
            }
            fout.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean writeRecData(){
        return true;
    }

    public int getSize(){
        return movValue.size();
    }

    public void clearRecData(){
        movValue.clear();
    }

    public RecData getLastValue(){
        return movValue.get(movValue.size()-1);
    }

}
