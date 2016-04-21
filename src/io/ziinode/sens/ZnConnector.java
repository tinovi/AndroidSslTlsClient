package io.ziinode.sens;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ZnConnector implements Runnable {
    private static final String TAG = main.TAG;//"znconn";
    public static byte SUB = 1; //Client subscribe/unsubscribe message to server, to subscribe to another device's data .
    public static byte SUB_DATA = 2; // data received by subscritions.
    public static byte DISCONN = 3; //Client sends disconnect to server.
    public static byte ACK = 4; //Server sends acknowledge after receiving any message except ENQ
    public static byte ENQ = 5; //Client sends enquiry message just after creating TCP connection with server
    public static byte CONN_ACK = 6; //Server sends acknowledge after device ENQ
    public static byte TRAP = 7;  //Client sends metrics data sent to server
    public static byte EVENT = 8;  //Client sends event data sent to server
    public static byte LOG = 9;  //Client sends log data sent to server
    public static byte REZ = 10; //reserved

    public static final int STATE_DISCONNECED = 0;
    public static final int STATE_GET_DEV_ID = 1;
    public static final int STATE_GET_SERVER = 2;
    public static final int STATE_CONNECTING = 3;
    public static final int STATE_ONLINE = 4;

    private volatile int state = STATE_DISCONNECED;

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    public void stop() {
        setState(STATE_DISCONNECED);
        if (future != null) {
            future.cancel(false);
        }
        try {
            if (socket != null) {
                //out.write(DISCONN);
                //out.flush();
                socket.close();
                //in=null;
            }
        } catch (IOException e) {
            Log.e(TAG, "stop:clse socket", e);
        }
        Log.i(TAG, "disconn");
    }

    public void start() {
        lastTime = 0;
        if (state == STATE_DISCONNECED) {
            if (m.getDsid().equals(type)) {
                setState(STATE_GET_DEV_ID);
            } else {
                setState(STATE_GET_SERVER);
            }
            future = scheduler.scheduleAtFixedRate(this, 0, 20, TimeUnit.MILLISECONDS);
        }
    }

    public void setState(int state) {
        if(this.state==STATE_ONLINE && state!=STATE_ONLINE){
            try {
                if (socket != null) {
                    socket.close();
                    //in=null;
                }
            } catch (IOException e) {
                Log.e(TAG, "stop:clse socket", e);
            }

        }
        this.state = state;
        m.onStatus(state);
    }

    ZnConnectorInf m;
    private String type;
    private short version;

    public ZnConnector(ZnConnectorInf m, String type, short version) {
        this.m = m;
        this.type = type;
        this.version = version;
    }

    public DataOutputStream getOut() {
        return out;
    }

    @Override
    public void run() {
        try {
            if (conni()) {
                if (in.available() > 0) {
                    byte cmd = in.readByte();
                    System.out.print(cmd);
                    if (CONN_ACK == cmd) {
                        setState(STATE_ONLINE);
                    } else if (cmd == ACK) {
                        Log.i(TAG, "ack");
                        m.onAck();
                        //ack
                    } else { //drain
                        Log.i(TAG, "drain:" + cmd);
                        byte[] bb = new byte[in.available()];
                        in.readFully(bb);
                        m.onMessage(cmd, ByteBuffer.wrap(bb));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "run:", e);
            setState(STATE_GET_SERVER);
        }
    }

    ScheduledFuture future;

    public int getState() {
        return state;
    }


    //    private static String url = "http://localhost:8800/site/v1/node/host/";
//    private static String url = "http://ziinode.io/site/v1/node/host/";
    private void conn(String server, int port, String key, String pin) {
        try {
            socket = new Socket(server, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.write(ENQ);
            out.write(type.getBytes("US-ASCII"));
            out.write(key.getBytes("US-ASCII"));
            out.write(pin.getBytes("US-ASCII"));
            out.writeShort(version);
            out.flush();
        } catch (Exception ee) {
            Log.e(TAG, "conn:", ee);
            setState(STATE_GET_SERVER);
        }
    }

    public static final String URL = "http://ziinode.io/api/v1/node/host/";
    long lastTime = 0;

    protected boolean conni() {
        if (state == STATE_ONLINE || state == STATE_CONNECTING) {
            return true;
        } else if (state == STATE_DISCONNECED){
            return false;
        } else {
            if (state == STATE_GET_SERVER) {
                if ((System.currentTimeMillis() - lastTime) < 5000) {
                    return false;
                } else {
                    lastTime = System.currentTimeMillis();
                }
            }
        }
        String adr = null;
        String ttt = URL + type + m.getDsid() + m.getPin();
        try {
            // Execute the method.
            Log.i(TAG, "Open:" + ttt);
            URL urla = new URL(ttt);
            HttpURLConnection conn = (HttpURLConnection) urla.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();
            //conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 200) {
                Object response = conn.getContent();
                Log.i(TAG, "response:200");
                InputStream dataIs = (InputStream) response;
                byte[] data = new byte[60];
                int received = dataIs.read(data);
                if (received > 0) {
                    final String str = new String(Arrays.copyOf(data, received));
                    Log.i(TAG, "resp:" + received + " str " + str);
                    if (data[0] == data[1] && data[1] == data[2] && data[2] == data[3] && data[3] == 0) {
                        System.err.println("sleep 0.0.0.0");
                        setState(STATE_GET_SERVER);
                    } else if (state == STATE_GET_DEV_ID) {
                        Log.i(TAG, "devid:" + adr);
                        m.onDeviceId(str);
                        setState(STATE_GET_SERVER);
                        return false;
                    }
                    if (str.lastIndexOf(":") > 6) {
                        adr = str.substring(0, str.lastIndexOf(":"));
                    } else {
                        adr = str;
                    }
                    setState(STATE_CONNECTING);
                    conn.disconnect();
                    Log.i(TAG, "Server addr:" + adr);
                    conn(adr, 8787, m.getDsid(), m.getPin());
                } else {
                    Log.e(TAG,"faled to open URL:" + ttt);
                }
            }
        } catch (Exception ee) {
            Log.e(TAG,"faled to open URL" + ttt, ee);
        }
        return false;
    }
}


