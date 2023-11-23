package com.bluetoothtest;

public class Message {

    public int iMethod;// 0-控制命令, 1-查询命令, 2-目标设备回复命令
    public int iSubmethod;// 1-录像, 4-设备状态
    public String pData; // 负载(Json字符串,根据命令类型转换,比如设备状态查询回复为DeviceStateData)

}
