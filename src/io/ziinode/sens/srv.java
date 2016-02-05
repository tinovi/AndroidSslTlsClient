package io.ziinode.sens;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by edgars.martinovs on 12/1/2015.
 */
public class srv extends Service implements SensorEventListener,LocationListener {
    private static final String TAG = main.TAG;//"znsrv";

    private SensorManager sensorManager = null;
    private Sensor sensLight = null;
    private Sensor sensPres = null;
    private Sensor sensProx = null;
    private Sensor sensHum = null;
    private Sensor sensTemp = null;
    protected double[] data = {0,0,0,0,0,0,0};
    private main m;
    boolean stated;

    @Override
    public void onDestroy() {
        super.onDestroy();
        stated = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "ZnServ-on start");
        if(!stated) {
            stated = true;
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            sensLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensPres = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            sensProx = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            sensHum = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
            sensTemp = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            sensorManager.registerListener(this, sensLight, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensPres, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensProx, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensHum, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensTemp, SensorManager.SENSOR_DELAY_NORMAL);

            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, this);
        }
        return START_STICKY;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public srv getService() {
            // Return this instance of LocalService so clients can call public methods
            return srv.this;
        }
    }

    public void setM(main m){
        this.m=m;
    }
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.i(TAG,"onSens:"+event.sensor.getType());
        if(event.values[0] == 0){
            return;
        }
        switch (event.sensor.getType()){
            case Sensor.TYPE_LIGHT:
                data[2] = event.values[0];
                if(m!=null)
                    m.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m.light.setText(String.valueOf(data[2]));
                        }
                    });
                break;
            case Sensor.TYPE_PRESSURE:
                data[3] = event.values[0];
                if(m!=null)
                    m.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m.press.setText(String.valueOf(data[3]));
                        }
                    });
                break;
            case Sensor.TYPE_PROXIMITY:
                data[4] = event.values[0];
                if(m!=null && m.prox!=null)
                    m.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m.prox.setText(String.valueOf(data[4]));
                        }
                    });
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                data[5] = event.values[0];
                if(m!=null)
                    m.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m.hum.setText(String.valueOf(data[5]));
                        }
                    });
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                data[6] = event.values[0];
                if(m!=null)
                    m.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m.temp.setText(String.valueOf(data[6]));
                        }
                    });
                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        data[0] = location.getLatitude();
        data[1] = location.getLongitude();
        if(m!=null){
            m.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    m.lat.setText(String.valueOf(data[0]));
                    m.lng.setText(String.valueOf(data[1]));
                }
            });
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
