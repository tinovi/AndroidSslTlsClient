package io.ziinode.sens;

import java.nio.ByteBuffer;

public interface ZnConnectorInf {
    public void onStatus(int status);
    public void onDeviceId(String devId);
    public String getDsid();
    public String getPin();
    public int getInterval();
    public void onAck();
    public void onMessage(byte type, ByteBuffer msg);
}
