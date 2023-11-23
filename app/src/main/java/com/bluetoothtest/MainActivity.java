package com.bluetoothtest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();
    private ArrayList<Map<String, Object>> devices = new ArrayList<>();
    private Map<String, Object> selectDevice;
    private ListView listView;
    private TextView textView;
    private Button pairBtn, cancelPairBtn, connectBtn, discoverBtn, sendVideoBtn, getInfoBtn;
    private SimpleAdapter simpleAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private PrintWriter writer;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gson = new Gson();
        listView = findViewById(R.id.listview);
        textView = findViewById(R.id.tv);
        pairBtn = findViewById(R.id.pair_btn);
        cancelPairBtn = findViewById(R.id.cancel_pair_btn);
        connectBtn = findViewById(R.id.connect_btn);
        discoverBtn = findViewById(R.id.discover_btn);
        sendVideoBtn = findViewById(R.id.record_btn);
        getInfoBtn = findViewById(R.id.get_info_btn);
        initData();
        initAction();
    }

    @SuppressLint("MissingPermission")
    private void initData() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);// 蓝牙扫描需要定位权限
        }

        // 注册搜索广播
        DiscoveryReceiver discoverReceiver = new DiscoveryReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoverReceiver, intentFilter);

        // 注册配对广播
        PairReceiver pairReceiver = new PairReceiver();
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intentFilter2.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(pairReceiver, intentFilter2);

        simpleAdapter = new SimpleAdapter(this, devices, R.layout.layout_adapter, new String[]{"name", "mac", "ble"}, new int[]{R.id.adapter_name_tv, R.id.adapter_mac_tv, R.id.adapter_ble_tv});
        listView.setAdapter(simpleAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent btEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(btEnable);
        } else {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();// 主动获取已配对设备集合,因为蓝牙扫描不到已配对设备
            if (bondedDevices.size() > 0) {  //存在已经配对过的蓝牙设备
                for (BluetoothDevice item : bondedDevices) {
                    Log.i(TAG, "已配对设备 = " + item.getName() + ", " + item.getAddress());
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", item.getName() + "[已配对]");
                    map.put("mac", item.getAddress());
                    map.put("ble", item);
                    devices.add(map);
                    simpleAdapter.notifyDataSetChanged();;
                }
                simpleAdapter.notifyDataSetChanged();
            }
            if (!bluetoothAdapter.isDiscovering()) {
                discoverBtn.setEnabled(false);
                bluetoothAdapter.startDiscovery();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void initAction() {
        // TODO 配对
        pairBtn.setOnClickListener(v -> {
            if (selectDevice != null) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) selectDevice.get("ble");
                bluetoothDevice.createBond();
            }
        });
        // TODO 取消配对,非标准功能,还是有点问题的,仅用于调试时可以不用去系统蓝牙里取消
        cancelPairBtn.setOnClickListener(v -> {
            if (selectDevice != null) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) selectDevice.get("ble");
                removeBond(bluetoothDevice);
            }
        });
        // TODO 连接(断开连接逻辑没写)
        connectBtn.setOnClickListener(v -> {
            if (selectDevice != null) {
                new Thread(() -> {
                    try {
                        BluetoothDevice bluetoothDevice = (BluetoothDevice) selectDevice.get("ble");
                        BluetoothSocket temp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString("ec345c68-06bc-4034-9dc1-c7e1c63a8496"));
                        socket = temp;
                        if (socket != null) {
                            socket.connect();
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_LONG).show());
                            inputStream = socket.getInputStream();
                            outputStream = socket.getOutputStream();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                            String content;
                            while(true) {
                                content = reader.readLine();
                                Log.d(TAG, "收到消息(Json) = " + content);
                            }
                        }

                    } catch (Exception e) {
                        Log.w(TAG, "连接失败 = ", e);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接失败", Toast.LENGTH_LONG).show());
                    } finally {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.w(TAG, "socket.close error = ", e);
                            }
                            socket = null;
                        }
                    }
                }).start();
            }
        });
        // TODO 重新扫描
        discoverBtn.setOnClickListener(v -> {
            if (!bluetoothAdapter.isDiscovering()) {
                devices.clear();
                selectDevice = null;
                textView.setText("");
                discoverBtn.setEnabled(false);

                Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();// 主动获取已配对设备集合,因为蓝牙扫描不到已配对设备
                if (bondedDevices.size() > 0) {  //存在已经配对过的蓝牙设备
                    for (BluetoothDevice item : bondedDevices) {
                        Log.i(TAG, "重新扫描,已配对设备 = " + item.getName() + ", " + item.getAddress());
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", item.getName() + "[已配对]");
                        map.put("mac", item.getAddress());
                        map.put("ble", item);
                        devices.add(map);
                        simpleAdapter.notifyDataSetChanged();;
                    }
                    simpleAdapter.notifyDataSetChanged();
                }

                bluetoothAdapter.startDiscovery();
            }
        });
        // TODO 发送录像命令
        sendVideoBtn.setOnClickListener(v -> {
            // 开始和结束录像都发送这一条命令,设备会根据当前状态进行切换。比如未开始录像就会开启,如果已经开始录像了就会结束录像。
            // 调用者可以根据设备状态命令查询当前录像状态。
            // 发送录像命令后,目标设备会回复一条设备状态的命令消息。
            Message message = new Message();
            message.iMethod = 0;
            message.iSubmethod = 1;
            StorageParam storageParam = new StorageParam();
            storageParam.fileName = "测试传入文件名";
            String jsonParam = gson.toJson(storageParam);
            message.pData = jsonParam;// 如果需要传入文件名,需要带上负载
            String jsonMessage = gson.toJson(message);
            write(jsonMessage);
        });
        // TODO 获取状态命令
        getInfoBtn.setOnClickListener(v -> {
            Message message = new Message();
            message.iMethod = 1;
            message.iSubmethod = 4;
            message.pData = "";// 查询设备状态不需要填负载信息
            String jsonMessage = gson.toJson(message);
            write(jsonMessage);
        });
        // TODO 列表点击
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectDevice = devices.get(position);
            Object name = selectDevice.get("name");
            if (name != null && ((String)name).contains("[已配对]")) {
                pairBtn.setEnabled(false);
                cancelPairBtn.setEnabled(true);
                connectBtn.setEnabled(true);
                sendVideoBtn.setEnabled(true);
                getInfoBtn.setEnabled(true);
            } else {
                pairBtn.setEnabled(true);
                cancelPairBtn.setEnabled(false);
                connectBtn.setEnabled(false);
                sendVideoBtn.setEnabled(false);
                getInfoBtn.setEnabled(false);
            }
            textView.setText(String.format("%s[%s]", name, selectDevice.get("mac")));
        });
    }


    private class DiscoveryReceiver extends BroadcastReceiver {

        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {  //搜索到蓝牙设备
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "扫描到的设备 = " + bluetoothDevice.getName() + ", " + bluetoothDevice.getAddress());
                Map<String, Object> map = new HashMap<>();
                map.put("name", bluetoothDevice.getName());
                map.put("mac", bluetoothDevice.getAddress());
                map.put("ble", bluetoothDevice);
                devices.add(map);
                simpleAdapter.notifyDataSetChanged();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {  //搜索结束
                Toast.makeText(MainActivity.this, "扫描结束", Toast.LENGTH_LONG).show();
                Log.i(TAG, "搜索结束");
                discoverBtn.setEnabled(true);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private class PairReceiver extends BroadcastReceiver { //蓝牙配对广播接收

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                    BluetoothDevice btd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (btd.getName().contains(String.format("%s", selectDevice.get("name")))) {
                        //1.确认配对
                        boolean bConfirmation = btd.setPairingConfirmation(true);
                        Log.i(TAG, "bConfirmation = " + bConfirmation);
                        //2.终止有序广播
                        Log.i(TAG, "isOrderedBroadcast:" + isOrderedBroadcast() + ",isInitialStickyBroadcast:" + isInitialStickyBroadcast());
                        abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
                        //3.调用setPin方法进行配对...
                        boolean bSetPin = btd.setPin("1234".getBytes());
                        Log.i(TAG, "bSetPin = " + bSetPin);
                    } else {
                        Log.e(TAG, "这个设备不是目标蓝牙设备");
                    }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {//配对广播
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE); //当前的配对的状态
                    Log.i(TAG, "ACTION_BOND_STATE_CHANGED state = " + state);
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); //配对的设备信息
                    if (state == BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(context, "配对成功 = " + device.getName(), Toast.LENGTH_SHORT).show();
                        for (int i = 0; i < devices.size(); i++) {
                            if (devices.get(i).get("mac").equals(device.getAddress())) {
                                devices.get(i).put("name", devices.get(i).get("name") + "[已配对]");
                                simpleAdapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    } else if (state == BluetoothDevice.BOND_BONDING) {
                        Toast.makeText(context, "配对中...", Toast.LENGTH_SHORT).show();
                    } else if (state == BluetoothDevice.BOND_NONE) {
                        // 配对失败
                        Toast.makeText(context, "配对失败", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void write(String msg) {
        if (outputStream != null) {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
            }
            writer.println(msg);
        }

    }

    private void removeBond(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}