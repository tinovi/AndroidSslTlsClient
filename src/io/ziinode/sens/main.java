package io.ziinode.sens;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class main extends Activity implements ZnConnectorInf{
    public static final String PREFS_NAME = "ZnPrefs";
    public static final String TAG = "io.ziinode.sens";
    public static final String TYPE = "ANDSENS";
    private srv s;
    /**
     * Called when the activity is first created.
     */
    TextView status;
    TextView lat;
    TextView lng;
    TextView light;
    TextView press;
    TextView hum;
    TextView temp;
    TextView prox;
    Button conn;
    EditText dsid;
    EditText pin;
    EditText interval;
    main m;
    SharedPreferences settings;
    ZnConnector znc;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ScheduledFuture future;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        m=this;
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        settings = getSharedPreferences(PREFS_NAME, 0);
        status = (TextView) findViewById(R.id.status);
        lat = (TextView) findViewById(R.id.lat);
        lng = (TextView) findViewById(R.id.lng);
        light = (TextView) findViewById(R.id.light);
        press = (TextView) findViewById(R.id.press);
        hum = (TextView) findViewById(R.id.hum);
        temp = (TextView) findViewById(R.id.temp);
        prox = (TextView) findViewById(R.id.prox);

        conn = (Button) findViewById(R.id.conn);
        conn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(znc.getState()==ZnConnector.STATE_DISCONNECED) {
                     if (dsid.getText().toString().length() != 7) {
                        Toast.makeText(m, "DsId field should be 7 numbers or letters, actual:" + dsid.getText().toString().length(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (pin.getText().toString().length() != 4) {
                        Toast.makeText(m, "PIN field should be 4 numbers or letters, actual:" + dsid.getText().toString().length(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        Integer.parseInt(interval.getText().toString());
                    } catch (Exception e) {
                        Toast.makeText(m, "Sending interval no correct, please provide number in seconds" + dsid.getText().toString().length(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("dsId", dsid.getText().toString());
                    editor.putString("pin", pin.getText().toString());
                    editor.putString("int", interval.getText().toString());
                    editor.commit();

                    if (s != null) {
                        znc.start();
                        conn.setText("Disconnect");
                    }
                }else{
                    znc.stop();
                }
            }
        });

        dsid = (EditText)findViewById(R.id.dsid);
        dsid.setText(settings.getString("dsId",TYPE));
        pin = (EditText)findViewById(R.id.pin);
        pin.setText(settings.getString("pin","1111"));
        interval =(EditText)findViewById(R.id.interval);
        String ii = settings.getString("int", null);
        if(ii!=null){
            interval.setText(ii);
        }
        znc = new ZnConnector(this,TYPE,(short)0);
    }

    public void sendTrap(){
        try {
            znc.getOut().write(ZnConnector.TRAP);
            znc.getOut().writeShort((short) 56);
            for (double d : s.data) {
                znc.getOut().writeDouble(d);
            }
            znc.getOut().flush();
        }catch (Exception ex){
            znc.setState(ZnConnector.STATE_GET_SERVER);
        }

    }

    private void makeT(final String toast){
        Log.i(TAG,"toast:"+toast);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(m, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setStatus(final String st){
        Log.i(TAG,"Sst:"+st);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(st);
            }
        });
    }

    @Override
    public void onStatus(int status) {
        if(status==ZnConnector.STATE_DISCONNECED){
            future.cancel(false);
            setStatus("Disconnected");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    conn.setText("Connect");
                }
            });
        }else if(status==ZnConnector.STATE_GET_DEV_ID){
            setStatus("Got deviceId, retrieving server...");
        }else if(status==ZnConnector.STATE_GET_SERVER){
            setStatus("Not registered, please register datasource Type:" + TYPE + " DsId:" + m.getDsid());
        }else if(status==ZnConnector.STATE_CONNECTING){
            setStatus("Got server address, connecting...");
        }else if(status==ZnConnector.STATE_ONLINE){
            setStatus("Connected, sending data.");
            future = scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    sendTrap();
                }
            },0,getInterval(), TimeUnit.SECONDS);

        }

    }

    @Override
    public void onDeviceId(final String devId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("dsId", dsid.getText().toString());
                editor.apply();
                dsid.setText(devId);
            }
        });
    }

    @Override
    public void onAck() {

    }

    @Override
    public void onMessage(byte type, ByteBuffer msg) {

    }

    public String getDsid() {
        return dsid.getText().toString();
    }
    public String getPin() {
        return pin.getText().toString();
    }
    public int getInterval() {
        return Integer.parseInt(interval.getText().toString());
    }
    Intent intent;
    @Override
    protected void onResume() {
        super.onResume();
        intent= new Intent(this, srv.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        startService(intent);
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            srv.LocalBinder b = (srv.LocalBinder) binder;
            s = b.getService();
            s.setM(m);
            Log.i(TAG, "onServiceConnected");
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceConnected");
            s = null;
        }
    };
 }
