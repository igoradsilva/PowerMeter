package com.example.igor.powermeter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;

public class DeviceList extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int GRAPH_ENTRIES = 2000;
    private static final int SAMPLES = 50;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    Spinner spnDeviceList;
    TextView txtCurrentValue;
    TextView txtTensionValue;
    TextView txtConsumptionValue;
    TextView txtCostValue;
    Button btnRefresh;
    Button btnConnect;
    Button btnDisconnect;
    Switch switchInstantCurrent;
    Switch switchRMSCurrent;
    Switch switchTension;
    Switch switchApparentPower;
    GraphView graphkW;

    LineGraphSeries<DataPoint> tensionSeries;
    LineGraphSeries<DataPoint> instantCurrentSeries;
    LineGraphSeries<DataPoint> RMSCurrentSeries;
    LineGraphSeries<DataPoint> apparentPowerSeries;
    private BluetoothAdapter myBT = null;
    private ArrayAdapter<CharSequence> myAA;
    private ArrayList deviceList;
    private ArrayList MACAddressList = new ArrayList();
    private Set<BluetoothDevice> pairedDevices;
    private String deviceMACAddress = null;
    private ConnectThread connection;
    private BluetoothDevice btDevice;



    private int samples = 0;
    private double timeStamp = 0;
    private double instantCurrent = 0.0;
    private double RMSCurrent = 0.0;
    private double tension = 0.0;
    private double kWhCount = 0.0;
    private double kWhPrice = 0.45;
    private double accumulatedCost = 0.0;
    private double currentSquareSum = 0.0;
    private double averageTension = 0.0;
    private double apparentPower = 0.0;
    private boolean showInstantCurrent = false;
    private boolean showRMSCurrent = false;
    private boolean showTension = false;
    private boolean showApparentPower = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        spnDeviceList = (Spinner) findViewById(R.id.spnDeviceList);
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
        txtCurrentValue = (TextView) findViewById(R.id.txtCurrentValue);
        txtTensionValue = (TextView) findViewById(R.id.txtTensionValue);
        txtConsumptionValue = (TextView) findViewById(R.id.txtConsumptionValue);
        txtCostValue = (TextView) findViewById(R.id.txtCostValue);
        switchInstantCurrent = (Switch) findViewById(R.id.switchInstantCurrent);
        switchRMSCurrent = (Switch) findViewById(R.id.switchRMSCurrent);
        switchTension = (Switch) findViewById(R.id.switchTension);
        switchApparentPower = (Switch) findViewById(R.id.switchApparentPower);
        graphkW = (GraphView) findViewById(R.id.graphkW);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPairedDevices();
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToDevice(btDevice);
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectFromDevice();
            }
        });

        spnDeviceList.setOnItemSelectedListener(this);

        switchInstantCurrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstantCurrent = switchInstantCurrent.isChecked();
                if(showInstantCurrent)
                    graphkW.addSeries(instantCurrentSeries);
                else
                    graphkW.removeSeries(instantCurrentSeries);
            }
        });

        switchRMSCurrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRMSCurrent = switchRMSCurrent.isChecked();
                if(showRMSCurrent)
                    graphkW.addSeries(RMSCurrentSeries);
                else
                    graphkW.removeSeries(RMSCurrentSeries);
            }
        });

        switchTension.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTension = switchTension.isChecked();
                if(showTension)
                    graphkW.addSeries(tensionSeries);
                else
                    graphkW.removeSeries(tensionSeries);
            }
        });

        switchApparentPower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showApparentPower = switchApparentPower.isChecked();
                if(showApparentPower)
                    graphkW.addSeries(apparentPowerSeries);
                else
                    graphkW.removeSeries(apparentPowerSeries);
            }
        });

        // Tries to get Bluetooth adapter. Finishes app if failed
        myBT = BluetoothAdapter.getDefaultAdapter();
        if (myBT == null) {
            finish();
        }

        // Enables Bluetooth, if not yet enabled
        if (!myBT.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }

        getPairedDevices();

        graphkW.getViewport().setXAxisBoundsManual(true);
        graphkW.getViewport().setMinX(0.0);
        graphkW.getViewport().setMaxX(2.0);
        tensionSeries = new LineGraphSeries<>();
        tensionSeries.setColor(Color.parseColor("Blue"));
        instantCurrentSeries = new LineGraphSeries<>();
        instantCurrentSeries.setColor(Color.parseColor("Red"));
        RMSCurrentSeries = new LineGraphSeries<>();
        RMSCurrentSeries.setColor(Color.parseColor("Green"));
        apparentPowerSeries = new LineGraphSeries<>();
        apparentPowerSeries.setColor(Color.parseColor("Yellow"));
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String line = msg.obj.toString();
            double deltaTime;
            double lastTimeStamp;
            double watts;

            switch(msg.what) {
                case 1:
                    String[] tokens = line.split(",");
                    if(tokens.length == 3) {
                        instantCurrent = Double.parseDouble(tokens[0]);
                        tension = 0.875*tension + 0.125*Double.parseDouble(tokens[1]);
                        lastTimeStamp = timeStamp;
                        timeStamp = Double.parseDouble(tokens[2]);
                        deltaTime = timeStamp - lastTimeStamp;
                        apparentPower = RMSCurrent * tension;
                        kWhCount += ((apparentPower * deltaTime)/1000000)/3600;
                        accumulatedCost = kWhCount * kWhPrice;
                        if(samples<SAMPLES) {
                            currentSquareSum += instantCurrent * instantCurrent;
                            samples++;
                        } else {
                            RMSCurrent = 0.25*RMSCurrent + 0.75*sqrt(currentSquareSum/SAMPLES);
                            txtCurrentValue.setText(round(RMSCurrent*1000.0)/1000.0 + " A");
                            txtTensionValue.setText(round(tension*100.0)/100.0 + " V");
                            txtConsumptionValue.setText(round(kWhCount*100.0)/100.0 + " kWh");
                            txtCostValue.setText("R$ " + round(accumulatedCost*100.0)/100.0);
                            currentSquareSum = 0;
                            samples = 0;
                        }
                        DataPoint point;
                        if(showInstantCurrent) {
                            point = new DataPoint(timeStamp / 1000, instantCurrent);
                            instantCurrentSeries.appendData(point, true, GRAPH_ENTRIES);
                        }

                        if(showRMSCurrent && samples >= SAMPLES) {
                            point = new DataPoint(timeStamp / 1000, RMSCurrent);
                            RMSCurrentSeries.appendData(point, true, GRAPH_ENTRIES);
                        }

                        if (showTension && samples >= SAMPLES) {
                            point = new DataPoint(timeStamp / 1000, tension);
                            tensionSeries.appendData(point, true, GRAPH_ENTRIES);
                        }

                        if(showApparentPower && samples >= SAMPLES) {
                            point = new DataPoint(timeStamp / 1000, apparentPower);
                            apparentPowerSeries.appendData(point, true, GRAPH_ENTRIES);
                        }
                    }
            }
        }
    };

    // Gets the deviceList of paired devices
    private void getPairedDevices() {
        pairedDevices = myBT.getBondedDevices();
        deviceList = new ArrayList();
        if (pairedDevices.size() > 0) {
            myAA = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_dropdown_item, deviceList);
            for (BluetoothDevice d : pairedDevices) {
                myAA.add(d.getName());
                MACAddressList.add(d.getAddress());
            }
            spnDeviceList.setAdapter(myAA);
            btnConnect.setEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        deviceMACAddress = MACAddressList.get(position).toString();
        btDevice = myBT.getRemoteDevice(deviceMACAddress);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    void connectToDevice(BluetoothDevice device) {
        connection = new ConnectThread(device);
        connection.start();
        btnConnect.setEnabled(false);
        btnDisconnect.setEnabled(true);
    }

    void disconnectFromDevice() {
        if(connection != null) {
            connection.cancel();
        }
        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);
    }

    private class ConnectThread extends Thread {
        // Class fields
        private BluetoothDevice device;
        private final BluetoothSocket socket;

        // Constructor
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            this.device = device;

            try {
                tmp = this.device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch(IOException e) {
            }
            this.socket = tmp;
        }

        // Method called when the Thread is started
        @Override
        public void run() {
            this.connect();
            this.readFromSocket();
        }

        void connect() {
            myBT.cancelDiscovery();
            try {
                this.socket.connect();
            } catch(IOException e) {
                this.closeConnection();
                return;
            }
        }

        void readFromSocket() {
            ConnectedThread handle = new ConnectedThread(this.socket);
            handle.start();
        }

        public void cancel() {
            this.closeConnection();
        }

        private void closeConnection() {
            try {
                this.socket.close();
            } catch(IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final int BUFFER_SIZE = 1024;
        private final BluetoothSocket socket;
        private final InputStream stream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmp = null;

            try {
                tmp = this.socket.getInputStream();
            } catch(IOException e) {
            }

            this.stream = tmp;
        }

        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes=0;

            while(true) {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(stream));
                    String line;
                    while((line = r.readLine()) != null) {
                        handler.obtainMessage(1, bytes, -1, line).sendToTarget();
                    }
                } catch(IOException e) {

                }

            }
        }
    }

}