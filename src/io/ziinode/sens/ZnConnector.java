package io.ziinode.sens;

import android.content.Context;
import android.util.Log;
import org.apache.http.conn.ssl.SSLSocketFactory;

import java.nio.ByteOrder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.cert.Certificate;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyStore;
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
    SSLSocket socket;
    DataInputStream in;
    DataOutputStream out;

    public void stop() {
        if(state == STATE_ONLINE){
            try {
                out.write(DISCONN);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
            Log.i(TAG, "ZN STARTED");
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
    final Context context;
    public ZnConnector(Context context, ZnConnectorInf m, String type, short version) {
        this.context = context;
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
                //if (in.available() > 0) {
                    byte cmd = in.readByte();
                    Log.i(TAG, "cmd:"+cmd);
                    if (CONN_ACK == cmd) {
                        Log.i(TAG, "!!! connack");
                        setState(STATE_ONLINE);
                    } else if (cmd == ACK) {
                        //short aa = in.readShort();
                        ByteBuffer bb = ByteBuffer.allocate(2);
                        bb.order(ByteOrder.LITTLE_ENDIAN);
                        bb.put(in.readByte());
                        bb.put(in.readByte());
                        short aa = bb.getShort(0);
                        Log.i(TAG, "ack:"+(aa&0xFFFF));
                        m.onAck(aa);
                        //ack
                    } else { //drain
                        Log.i(TAG, "drain:" + cmd);
                        byte[] bb = new byte[in.available()];
                        in.readFully(bb);
                        m.onMessage(cmd, ByteBuffer.wrap(bb));
                    }
                //}
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


    private void conn(String server, int port, String key, String pin) {
        try {

            SSLSocketFactory socketFactory = newSslSocketFactory();
            //socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            socket = (SSLSocket) socketFactory.createSocket(new Socket(server,port), server, port, false);
            socket.startHandshake();

            printServerCertificate(socket);
            printSocketInfo(socket);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.write(ENQ);
            out.write(type.getBytes("US-ASCII"));
            out.write(key.getBytes("US-ASCII"));
            out.write(pin.getBytes("US-ASCII"));
            out.writeShort(version);
            out.flush();
            Log.e(TAG, "ENQ SEND!!!");
        } catch (Exception ee) {
            Log.e(TAG, "conn:", ee);
            setState(STATE_GET_SERVER);
        }
    }

    public static final String URL = "http://tinovi.io/api/v1/node/host/";
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
                    conn(adr, 9797, m.getDsid(), m.getPin());
                } else {
                    Log.e(TAG,"faled to open URL:" + ttt);
                }
            }
        } catch (Exception ee) {
            Log.e(TAG,"faled to open URL" + ttt, ee);
        }
        return false;
    }

    private void printServerCertificate(SSLSocket socket) {
        try {
            Certificate[] serverCerts =
                    socket.getSession().getPeerCertificates();
            for (int i = 0; i < serverCerts.length; i++) {
                Certificate myCert = serverCerts[i];
                Log.i(TAG,"====Certificate:" + (i+1) + "====");
                Log.i(TAG,"-Public Key-\n" + myCert.getPublicKey());
                Log.i(TAG,"-Certificate Type-\n " + myCert.getType());

                System.out.println();
            }
        } catch (SSLPeerUnverifiedException e) {
            Log.i(TAG,"Could not verify peer");
            e.printStackTrace();
            System.exit(-1);
        }
    }
    private void printSocketInfo(SSLSocket s) {
        Log.i(TAG,"Socket class: "+s.getClass());
        Log.i(TAG,"   Remote address = "
                +s.getInetAddress().toString());
        Log.i(TAG,"   Remote port = "+s.getPort());
        Log.i(TAG,"   Local socket address = "
                +s.getLocalSocketAddress().toString());
        Log.i(TAG,"   Local address = "
                +s.getLocalAddress().toString());
        Log.i(TAG,"   Local port = "+s.getLocalPort());
        Log.i(TAG,"   Need client authentication = "
                +s.getNeedClientAuth());
        SSLSession ss = s.getSession();
        Log.i(TAG,"   Cipher suite = "+ss.getCipherSuite());
        Log.i(TAG,"   Protocol = "+ss.getProtocol());
    }
    private SSLSocketFactory newSslSocketFactory() {
        try {
            // Get an instance of the Bouncy Castle KeyStore format
            KeyStore trusted = KeyStore.getInstance("BKS");
            // Get the raw resource, which contains the keystore with
            // your trusted certificates (root and any intermediate certs)
            InputStream in = context.getResources().openRawResource(R.raw.znandroid);
            try {
                // Initialize the keystore with the provided trusted certificates
                // Also provide the password of the keystore
                trusted.load(in, "ziinode".toCharArray());
            } finally {
                in.close();
            }
            // Pass the keystore to the SSLSocketFactory. The factory is responsible
            // for the verification of the server certificate.
            SSLSocketFactory sf = new SSLSocketFactory(trusted);
            // Hostname verification from certificate
            // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d4e506
            sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
            return sf;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}


