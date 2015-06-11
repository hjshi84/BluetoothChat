package com.example.android.BluetoothChat;

/**
 * Created with IntelliJ IDEA.
 * User: hjshi84
 * Date: 13-12-13
 * Time: 下午3:01
 * To change this template use File | Settings | File Templates.
 */
public class GateDetect {
    private static final double LFT=0.65;
    private static final double UFT=2.5;
    private static fallState myFallState;
    private static gateState myGateState;
    public static Byte GateDetectS(RecData temp)
    {
        if (myFallState==null)
        {
            myFallState=new fallState(temp);
        }
        else{
            myFallState.setValue(temp);
        }
        if (myGateState==null)
            myGateState=new gateState();
        return  (byte)((myFallState.isFall() | myGateState.isGate(temp)));
    }

    private static class fallState{
        RecData nowValue;
        RecData lastValue=new RecData();
        RecData startValue;
        RecData midValue;
        RecData endValue;
        boolean startfall=false;
        boolean midfall=false;
        boolean endfall=false;

        private fallState(RecData nowValue) {
            this.nowValue = nowValue;
        }

        private void setValue(RecData nowValue)
        {
            this.nowValue=nowValue;
        }

        private Byte isFall(){
            if(lastValue.accRecData.getSumValue()>LFT&&nowValue.accRecData.getSumValue()<LFT){
                startValue=nowValue;
                startfall=true;
                midfall=false;
                endfall=false;
            }else if(startfall&& nowValue.accRecData.getSumValue()>LFT&&lastValue.accRecData.getSumValue()<LFT&&!midfall)    {
                midValue=nowValue;
                midfall=true;
            }else if (startfall&&midfall&& nowValue.accRecData.getSumValue()>UFT){
                endValue=nowValue;
                endfall=true;
            }
            lastValue=nowValue;
            if(endfall){
                if(endValue.timestamp-startValue.timestamp<65&&midValue.timestamp-startValue.timestamp<35){
                    startfall=false;
                    midfall=false;
                    endfall=false;
                    return 0x01;
                }
            }
            return 0x00;
        }
    }

    private static class gateState{
        RecData sample_new=new RecData();
        RecData sample_old=new RecData();
        RecData lastValue=new RecData();
        float dynamicthreshold=1.0f;
        float precise=0.1f;
        int lastGateTime;

        private Byte isGate(RecData temp){
            if(sample_new.timestamp-lastGateTime>=2000)
            {
                lastGateTime=-100;
            }
            sample_old=sample_new;
            if(prejudge(temp)==1&&sample_old!=null)
            {
                if(sample_old.accRecData.getSumValue()>dynamicthreshold
                        &&sample_new.accRecData.getSumValue()<dynamicthreshold
                        &&Math.abs(sample_new.timestamp-lastGateTime)>20)
                {
                    lastGateTime=sample_new.timestamp;
                    return 0x02;
                }

            }
            return 0x00;
        }

        private int prejudge(RecData temp){
            if(lastValue==null){
                lastValue=temp;
                return 0;
            }
            if(Math.abs(temp.accRecData.getSumValue()- lastValue.accRecData.getSumValue())>precise){
                sample_new=temp;
                lastValue=temp;
                return 1;
            }else{
                lastValue=temp;
                return 0;
            }
        }
    }


}
