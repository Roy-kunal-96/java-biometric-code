package com.access.testappfm220;


import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.acpl.access_computech_fm220_sdk.FM220_Scanner_Interface;
import com.acpl.access_computech_fm220_sdk.acpl_FM220_SDK;
import com.acpl.access_computech_fm220_sdk.fm220_Capture_Result;
import com.acpl.access_computech_fm220_sdk.fm220_Init_Result;
import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements FM220_Scanner_Interface {

    private static final String TAG = "Test Tag";
    private acpl_FM220_SDK FM220SDK;
    private Button Capture_No_Preview, Capture_PreView, Capture_BackGround, Capture_match, btnsetF, btngetF, btnREsetF;
    private TextView textMessage;
    private ImageView imageView;
    /***************************************************
     * if you are use telecom device enter "Telecom_Device_Key" as your provided key otherwise send "" ;
     */
    private static final String Telecom_Device_Key = "";
    private byte[] t1, t2;

    //region USB intent and functions
    private UsbManager manager;
    private PendingIntent mPermissionIntent;
    private UsbDevice usb_Dev;
    private static final String ACTION_USB_PERMISSION = "com.access.testappfm220.USB_PERMISSION";

    ProgressBar pb;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
//test
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                int pid, vid;
                pid = device.getProductId();
                vid = device.getVendorId();
                if ((pid == 0x8225 || pid == 0x8220) && (vid == 0x0bca)) {
                    FM220SDK.stopCaptureFM220();
                    FM220SDK.unInitFM220();
                    usb_Dev = null;
                    textMessage.setText("       FM220 disconnected");
                    DisableCapture();
                }
            }
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // call method to set up device communication
                            int pid, vid;
                            pid = device.getProductId();
                            vid = device.getVendorId();
                            if ((pid == 0x8225 || pid == 0x8220) && (vid == 0x0bca)) {
                                fm220_Init_Result res = FM220SDK.InitScannerFM220(manager, device, Telecom_Device_Key);
                                if (res.getResult()) {
                                    textMessage.setText("         FM220 ready. " + res.getSerialNo());
                                    EnableCapture();
                                } else {
                                    textMessage.setText("         Error :-" + res.getError());
                                    DisableCapture();
                                }
                            }
                        }
                    } else {
                        textMessage.setText("         User Blocked USB connection");
                        textMessage.setText("         FM220 ready");
                        DisableCapture();
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        // call method to set up device communication
                        int pid, vid;
                        pid = device.getProductId();
                        vid = device.getVendorId();
                        if ((pid == 0x8225) && (vid == 0x0bca) && !FM220SDK.FM220isTelecom()) {
                            Toast.makeText(context, "Wrong device type application restart required!", Toast.LENGTH_LONG).show();
                            finish();
                        }
                        if ((pid == 0x8220) && (vid == 0x0bca) && FM220SDK.FM220isTelecom()) {
                            Toast.makeText(context, "Wrong device type application restart required!", Toast.LENGTH_LONG).show();
                            finish();
                        }

                        if ((pid == 0x8225 || pid == 0x8220) && (vid == 0x0bca)) {
                            if (!manager.hasPermission(device)) {
                                textMessage.setText("      FM220 requesting permission");
                                manager.requestPermission(device, mPermissionIntent);
                            } else {
                                fm220_Init_Result res = FM220SDK.InitScannerFM220(manager, device, Telecom_Device_Key);
                                if (res.getResult()) {
                                    textMessage.setText("       FM220 ready. " + res.getSerialNo());
                                    EnableCapture();
                                } else {
                                    textMessage.setText("      Error :-" + res.getError());
                                    DisableCapture();
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onNewIntent(Intent intent) {
        if (getIntent() != null) {
            return;
        }
        super.onNewIntent(intent);
        setIntent(intent);
//        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            Log.v(TAG,"Permission is granted");
//            //File write logic here
//
//        }
        try {
            if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED) && usb_Dev == null) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // call method to set up device communication & Check pid
                    int pid, vid;
                    pid = device.getProductId();
                    vid = device.getVendorId();
                    if ((pid == 0x8225) && (vid == 0x0bca)) {
                        if (manager != null) {
                            if (!manager.hasPermission(device)) {
                                textMessage.setText("      FM220 requesting permission");
                                manager.requestPermission(device, mPermissionIntent);
                            }
//                            else {
//                                fm220_Init_Result res =  FM220SDK.InitScannerFM220(manager,device,Telecom_Device_Key);
//                                if (res.getResult()) {
//                                    textMessage.setText("FM220 ready. "+res.getSerialNo());
//                                    EnableCapture();
//                                }
//                                else {
//                                    textMessage.setText("Error :-"+res.getError());
//                                    DisableCapture();
//                                }
//                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();

        }
    }


    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(mUsbReceiver);
            FM220SDK.unInitFM220();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
    //endregion


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pb = (ProgressBar) findViewById(R.id.progress);

        pb.setVisibility(View.GONE);



//        FM220SDK = new acpl_FM220_SDK(getApplicationContext(),this);
        textMessage = (TextView) findViewById(R.id.textMessage);
        Capture_PreView = (Button) findViewById(R.id.button2);
        Capture_No_Preview = (Button) findViewById(R.id.button);
        Capture_BackGround = (Button) findViewById(R.id.button3);
        Capture_match = (Button) findViewById(R.id.button4);
        imageView = (ImageView) findViewById(R.id.imageView);

        btnsetF = (Button) findViewById(R.id.setflag);
        btngetF = (Button) findViewById(R.id.getflag);
        btnREsetF = (Button) findViewById(R.id.resetflag);

        //Region USB initialisation and Scanning for device
        SharedPreferences sp = getSharedPreferences("last_FM220_type", Activity.MODE_PRIVATE);
        boolean oldDevType = sp.getBoolean("FM220type", true);

        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        final Intent piIntent = new Intent(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 16) piIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mPermissionIntent = PendingIntent.getBroadcast(getBaseContext(), 1, piIntent, 0);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbReceiver, filter);
        UsbDevice device = null;
        for (UsbDevice mdevice : manager.getDeviceList().values()) {
            int pid, vid;
            pid = mdevice.getProductId();
            vid = mdevice.getVendorId();
            boolean devType;
            if ((pid == 0x8225) && (vid == 0x0bca)) {
                FM220SDK = new acpl_FM220_SDK(getApplicationContext(), this, true);
                devType = true;
            } else if ((pid == 0x8220) && (vid == 0x0bca)) {
                FM220SDK = new acpl_FM220_SDK(getApplicationContext(), this, false);
                devType = false;
            } else {
                FM220SDK = new acpl_FM220_SDK(getApplicationContext(), this, oldDevType);
                devType = oldDevType;
            }
            if (oldDevType != devType) {
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("FM220type", devType);
                editor.apply();
            }
            if ((pid == 0x8225 || pid == 0x8220) && (vid == 0x0bca)) {
                device = mdevice;
                if (!manager.hasPermission(device)) {
                    textMessage.setText("      FM220 requesting permission");
                    manager.requestPermission(device, mPermissionIntent);
                } else {
                    Intent intent = this.getIntent();
                    if (intent != null) {
                        if (intent.getAction().equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
                            finishAffinity();
                        }
                    }
                    fm220_Init_Result res = FM220SDK.InitScannerFM220(manager, device, Telecom_Device_Key);
                    if (res.getResult()) {
                        textMessage.setText("      FM220 ready. " + res.getSerialNo());
                        EnableCapture();
                    } else {
                        textMessage.setText("     Error :-" + res.getError());
                        DisableCapture();
                    }
                }
                break;
            }
        }
        if (device == null) {
            textMessage.setText("           Please connect FM220");
            System.out.println("Test");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                System.out.println("Inside");
//                System.out.println(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
//                System.out.println(PackageManager.PERMISSION_GRANTED);

                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                    Log.v(TAG,"Permission not granted");
                    //File write logic here
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                }
            }
            FM220SDK = new acpl_FM220_SDK(getApplicationContext(), this, oldDevType);
        }

        //endregion

        btnsetF.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                int val = FM220SDK.SetRegistration(2);
                if (val == 0) {
                    Toast.makeText(getApplicationContext(), "set reg", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btngetF.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean val = FM220SDK.GetRegistration(2);
                if (val) {
                    Toast.makeText(getApplicationContext(), "true", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "false", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnREsetF.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                int val = FM220SDK.ResetRegistration(2);
                if (val == 0) {
                    Toast.makeText(getApplicationContext(), "reset reg", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Capture_BackGround.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                DisableCapture();
                textMessage.setText("          Please wait..");
                imageView.setImageBitmap(null);
                FM220SDK.CaptureFM220(2);

            }
        });

        Capture_No_Preview.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                DisableCapture();
                FM220SDK.CaptureFM220(2, true, false);
            }
        });

        Capture_PreView.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                DisableCapture();
                FM220SDK.CaptureFM220(2, true, true);
            }
        });
        Capture_match.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                /***
                 * if t1 and t2 is byte so you can use MatchFM220(byte[],baye[]) function
                 and its String so you can use MatchFm220String(StringTmp1,StringTmp2) function
                 for your Matching templets. and you want at time match your finger with scaning process use
                 FM220SDK.MatchFM220(2, true, true, oldfingerprintisovale) function with pass old templet as perameter.
                 */

//                DisableCapture();
//                FM220SDK.MatchFM220(2, true, true, t1);

                if (t1 != null && t2 != null) {
                    if (FM220SDK.MatchFM220(t1, t2)) {
                        textMessage.setText("      Finger matched");
                        t1 = null;
                        t2 = null;
                    } else {
                        textMessage.setText("    Finger not matched");
                    }
                } else {
                    textMessage.setText("Please capture first");
                }
//                String teamplet match example using FunctionBAse64 function .....
                FunctionBase64();
            }
        });
    }

    private void DisableCapture() {
        Capture_BackGround.setEnabled(false);
        Capture_No_Preview.setEnabled(false);
        Capture_PreView.setEnabled(false);
        Capture_match.setEnabled(false);
        imageView.setImageBitmap(null);
    }

    private void EnableCapture() {
        Capture_BackGround.setEnabled(true);
        Capture_No_Preview.setEnabled(true);
        Capture_PreView.setEnabled(true);
        Capture_match.setEnabled(true);
    }

    private void FunctionBase64() {
        try {
            String t1base64, t2base64;
            if (t1 != null && t2 != null) {
                t1base64 = Base64.encodeToString(t1, Base64.NO_WRAP);
                t2base64 = Base64.encodeToString(t2, Base64.NO_WRAP);
                if (FM220SDK.MatchFM220String(t1base64, t2base64)) {
                    Toast.makeText(getBaseContext(), "Finger matched", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getBaseContext(), "Finger not matched", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ScannerProgressFM220(final boolean DisplayImage, final Bitmap ScanImage, final boolean DisplayText, final String statusMessage) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (DisplayText) {
//                    Toast.makeText(getBaseContext(), statusMessage, Toast.LENGTH_SHORT).show();
//                    statusMessage== "Pl place Finger Clean Finger Bitmap can't create";
                    if (statusMessage.contains("place"))
                    {
                        textMessage.setText("Please place finger or \n Clean your finger");
                        textMessage.invalidate();

                    }
//                    if (statusMessage.contains("Hard"))
//                    {
//                        textMessage.setText("Press Hard or Moist finger \n");
//                    }
//                    if (statusMessage.contains("Clean Finger"))
//                    {
//                        textMessage.setText("Clean Finger \n");
//                    }
//                    if (statusMessage.contains("Press Lightly"))
//                    {
//                        textMessage.setText("Press Lightly or wipe finger \n");
//                    }
                   else if (statusMessage.contains("Pl Wait"))
                    {
                        textMessage.setText("          Please wait");
                        textMessage.invalidate();

                    }
                    else
                    {
                        textMessage.setText(statusMessage);
                        textMessage.invalidate();
                    }

                }
                if (DisplayImage) {
                    imageView.setImageBitmap(ScanImage);
                    imageView.invalidate();
                }
            }
        });
    }

    @Override
    public void ScanCompleteFM220(final fm220_Capture_Result result) {

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (FM220SDK.FM220Initialized()) EnableCapture();
                if (result.getResult()) {
                    imageView.setImageBitmap(result.getScanImage());

                    byte[] isotem = result.getISO_Template();   // ISO TEMPLET of FingerPrint.....
//                    isotem is byte value of fingerprints
                    if (t1 == null) {
                        t1 = result.getISO_Template();
                    } else {
                        t2 = result.getISO_Template();
                    }
                    String ip = getIPAddress(true);
                    Log.i("Image", ip);

                    String base64String = convert(result.getScanImage());

                    try {
                        getRequest(ip, base64String);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    createFile(base64String);
                    textMessage.setText("         Success NFIQ:" + Integer.toString(result.getNFIQ()) + "  SrNo:" + result.getSerialNo());

                } else {
                    imageView.setImageBitmap(null);
                    textMessage.setText(result.getError());
                }
                imageView.invalidate();
                textMessage.invalidate();
            }
        });
    }

    public static String convert(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }

    @Override
    public void ScanMatchFM220(final fm220_Capture_Result _result) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (FM220SDK.FM220Initialized()) EnableCapture();
                if (_result.getResult()) {
                    imageView.setImageBitmap(_result.getScanImage());
                    textMessage.setText("Finger matched\n" + "Success NFIQ:" + Integer.toString(_result.getNFIQ()));
                } else {
                    imageView.setImageBitmap(null);
                    textMessage.setText("Finger not matched\n" + _result.getError());
                }
                imageView.invalidate();
                textMessage.invalidate();
            }
        });
    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    //create file bride

    public void createFile(String base64) {
        pb.setVisibility(View.VISIBLE);

        try {
            FileOutputStream fos = new FileOutputStream(new File("./storage/emulated/0/tn_survey_bridge.txt"));
            fos.write(base64.getBytes());
            fos.close();
            pb.setVisibility(View.GONE);
            finish();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Create GetText Metod
    public void getRequest(final String ip, String base64) {

        pb.setVisibility(View.VISIBLE);


        String URLline = "https://tnsurveyapp.azurewebsites.net/api/data/finger";

        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            String URL = URLline;
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("ip", ip);
            jsonBody.put("image", base64);
            final String requestBody = jsonBody.toString();

            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.i("VOLLEY", response);
                    pb.setVisibility(View.GONE);
                    finish();
                    System.exit(0);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("VOLLEY", error.toString());
                    pb.setVisibility(View.GONE);

                }
            }) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return requestBody == null ? null : requestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                        // can get more details such as response.headers
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };

            requestQueue.add(stringRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


}



