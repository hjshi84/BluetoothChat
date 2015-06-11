package com.example.android.BluetoothChat;

import java.sql.Struct;
import java.text.DecimalFormat;

/**
 * Created with IntelliJ IDEA.
 * User: hjshi84
 * Date: 13-12-19
 * Time: 下午2:27
 * To change this template use File | Settings | File Templates.
 */
public class IMU {
    float IMUX=0;
    float IMUY=0;
    float IMUZ=0;

    @Override
    public String toString() {
        String parten = "#.##";
        DecimalFormat decimal = new DecimalFormat(parten);
        return
                "X=" + decimal.format(IMUX) +
                ", Y=" + decimal.format(IMUY) +
                ", Z=" + decimal.format(IMUZ);
    }
}
