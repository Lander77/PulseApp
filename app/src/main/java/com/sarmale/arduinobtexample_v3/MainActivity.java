package com.sarmale.arduinobtexample_v3;


import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
   // Global variables we will use in the
    private static final String TAG = "FrugalLogs";
    private static final int REQUEST_ENABLE_BT = 1;
    //We will use a Handler to get the BT Connection statys
    public static Handler handler;
    private final static int ERROR_READ = 0; // used in bluetooth handler to identify message update
    BluetoothDevice arduinoBTModule = null;
    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //We declare a default UUID to create the global variable
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Intances of BT Manager and BT Adapter needed to work with BT in Android.
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        //Intances of the Android UI elements that will will use during the execution of the APP
        TextView btReadings = findViewById(R.id.btReadings);
        TextView btDevices = findViewById(R.id.btDevices);
        Button connectToDevice = (Button) findViewById(R.id.connectToDevice);
        Button seachDevices = (Button) findViewById(R.id.seachDevices);
        Button clearValues = (Button) findViewById(R.id.refresh);
        Button saveButton = (Button) findViewById(R.id.saveButton);
        Button homeScreen = (Button) findViewById(R.id.homeScreen);
        Log.d(TAG, "Begin Execution");

        //Using a handler to update the interface in case of an error connecting to the BT device
        //My idea is to show handler vs RxAndroid
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {

                    case ERROR_READ:
                       String arduinoMsg = msg.obj.toString(); // Read message from Arduino
                        btReadings.setText(arduinoMsg);
                        break;
                }
            }
        };


        // Set a listener event on a button to clear the texts
        clearValues.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btReadings.setText("");
                saveButton.setEnabled(false);
            }
        });



        connectToDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread thread = new Thread(() -> {

                    btReadings.setText("");
                    if (arduinoBTModule != null) {
                        Log.d(TAG, "Calling connectThread class");
                        ConnectThread connectThread = new ConnectThread(arduinoBTModule, arduinoUUID, handler);
                        connectThread.run();
                        if (connectThread.getMmSocket().isConnected()) {
                            Log.d(TAG, "Calling ConnectedThread class");

                            // Pass the Open socket as arguments to call the constructor of ConnectedThread
                            ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket());
                            connectedThread.run();
                            String valueRead = connectedThread.getValueRead();
                            btReadings.setText(valueRead);
                            while (!valueRead.startsWith("[GFAP]")) {
                                ConnectedThread connectedThread_new = new ConnectedThread(connectThread.getMmSocket());
                                connectedThread_new.run();
                                valueRead = connectedThread_new.getValueRead();
                                btReadings.setText(valueRead);
                            }
                            saveButton.post(() -> saveButton.setEnabled(true));
                            connectedThread.cancel();
                        }
                        connectThread.cancel();
                    }
                });
                saveButton.post(() -> saveButton.setEnabled(false));
                thread.start();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Create a new text field for the user's name
                final EditText nameEditText = new EditText(MainActivity.this);

                // Create a new alert dialog and set its title and message
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Enter Your Name");
                builder.setMessage("Please enter your name to save the data:");

                // Set the alert dialog's custom view to the name text field
                builder.setView(nameEditText);

                // Set the alert dialog's OK button and its click listener
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Get the user's entered name
                        String name = nameEditText.getText().toString();

                        // Get the values from the text view and current date
                        String readValue = btReadings.getText().toString();
                        //String readValue = "[GFAP] = 7.5";
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        String dateNow = sdf.format(new Date());

                        // Create a new thread to perform database operation
                        Thread thread = new Thread(() -> {
                            try {
                                // Load the jTDS driver
                                Class.forName("net.sourceforge.jtds.jdbc.Driver");

                                // Establish the database connection
                                String connectionUrl = "jdbc:jtds:sqlserver://bluetooth.database.windows.net:1433;DatabaseName=BluetoothDB;user=beheerder@bluetooth;password=Project23*;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;ssl=require;";
                                Connection connection = DriverManager.getConnection(connectionUrl);

                                // Create an SQL query to insert data into the database
                                String sql = "INSERT INTO Results (Result, Date, Name) VALUES (?, ?, ?)";
                                PreparedStatement statement = connection.prepareStatement(sql);
                                statement.setString(1, readValue);
                                statement.setString(2, dateNow);
                                statement.setString(3, name);

                                // Execute the SQL query
                                statement.executeUpdate();

                                // Close the statement and connection
                                statement.close();
                                connection.close();

                                // Optionally, you can perform further operations after successful data insertion

                            } catch (ClassNotFoundException | SQLException e) {
                                e.printStackTrace();
                                // Handle any exceptions that may occur during the database operation
                            }
                        });

                        // Start the thread
                        thread.start();
                    }
                });

                // Set the alert dialog's Cancel button and its click listener
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Do nothing if the user cancels
                    }
                });

                // Create and show the alert dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });



        seachDevices.setOnClickListener(new View.OnClickListener() {
            //Display all the linked BT Devices
            @Override
            public void onClick(View view) {
                //Check if the phone supports BT
                if (bluetoothAdapter == null) {
                    // Device doesn't support Bluetooth
                    Log.d(TAG, "Device doesn't support Bluetooth");
                } else {
                    Log.d(TAG, "Device support Bluetooth");
                    //Check BT enabled. If disabled, we ask the user to enable BT
                    if (!bluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "Bluetooth is disabled");
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            Log.d(TAG, "We don't BT Permissions");
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                            Log.d(TAG, "Bluetooth is enabled now");
                        } else {
                            Log.d(TAG, "We have BT Permissions");
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                            Log.d(TAG, "Bluetooth is enabled now");
                        }

                    } else {
                        Log.d(TAG, "Bluetooth is enabled");
                    }
                    String btDevicesString="";
                    Set < BluetoothDevice > pairedDevices = bluetoothAdapter.getBondedDevices();

                    if (pairedDevices.size() > 0) {
                        // There are paired devices. Get the name and address of each paired device.
                        for (BluetoothDevice device: pairedDevices) {
                            String deviceName = device.getName();
                            String deviceHardwareAddress = device.getAddress(); // MAC address
                            Log.d(TAG, "deviceName:" + deviceName);
                            Log.d(TAG, "deviceHardwareAddress:" + deviceHardwareAddress);
                            //We append all devices to a String that we will display in the UI
                            btDevicesString=btDevicesString+deviceName+" || "+deviceHardwareAddress+"\n";
                            //If we find the HC 05 device (the Arduino BT module)
                            //We assign the device value to the Global variable BluetoothDevice
                            //We enable the button "Connect to HC 05 device"
                            if (deviceName.equals("PS-0C88")) {
                                Log.d(TAG, "Sensor found");
                                arduinoUUID = device.getUuids()[0].getUuid();
                                arduinoBTModule = device;
                                //HC -05 Found, enabling the button to read results
                                connectToDevice.setEnabled(true);
                            }
                            btDevices.setText(btDevicesString);
                            btDevices.setMovementMethod(new ScrollingMovementMethod());
                        }
                    }
                }
                Log.d(TAG, "Button Pressed");
            }
        });
    }

}