package com.example.gui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.KeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.drclient.R;
import com.example.drcom.STATUS;
import com.example.service.KeepService;
import com.example.service.UIController;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private EditText userNameEditText;
    private EditText passwordEditText;
    private EditText macAddrEditText;
    private Button loginButton;
    private Context context = this;
    private static final String TAG = "MainActivity";
    private KeepService.KeepBinder keepBinder;
    private STATUS status = STATUS.offline;
    public boolean isAutoReconnect = false;
    private boolean isNotSave = false;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: 已绑定服务");
            keepBinder = (KeepService.KeepBinder) service;
            keepBinder.setUIController(uiController);
            Log.d(TAG, "onServiceConnected: 获取是否在线的信息");
            status = keepBinder.isOnline() ? STATUS.online : STATUS.offline;
            adjustUI(keepBinder.isOnline());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: 已解绑服务");
        }
    };
    private UIController uiController = new UIController() {
        @Override
        public void loggedIn(final String[] s) { // 登录成功
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "已登录", Toast.LENGTH_SHORT).show();
                    // 注销按钮可用
                    loginButton.setEnabled(true);
                    adjustUI(true);
                    status = STATUS.online;
                    SharedPreferences.Editor editor = getSharedPreferences("name_pass_mac", MODE_PRIVATE).edit();
                    editor.clear();
                    if (isNotSave) {
                        editor.apply();
                        Log.d(TAG, "run: 已清除密码");
                        userNameEditText.setText("");
                        passwordEditText.setText("");
                        macAddrEditText.setText("");
                        return;
                    }
                    editor.putString("name", s[0]);
                    editor.putString("pass", s[1]);
                    editor.putString("mac", s[2]);
                    editor.apply();
                    Log.d(TAG, "run: 已保存密码");
                }
            });
        }

        @Override
        public void offline() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 掉线了，可能是注销了也可能是异常了，此时暂时还不允许重新登录，等待后台释放资源
                    if (!isAutoReconnect)
                        Toast.makeText(context, "已离线", Toast.LENGTH_SHORT).show();
                    status = STATUS.offline;
                }
            });
        }

        @Override
        public void canLoginNow(final String[] s) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SharedPreferences settingsPreferences = getSharedPreferences(SettingsActivity.FILE_NAME, MODE_PRIVATE);
                    if (isAutoReconnect && keepBinder.canReconnect()) {
                        Log.d(TAG, "run: 无限重连中");
                        // 读取设置
                        isAutoReconnect = settingsPreferences.getBoolean(SettingsActivity.AUTO_RECONNECT, false);
                        status = STATUS.online;
                        adjustUI(true);
                        // 读取保存的用户名密码MAC地址
                        SharedPreferences preferences = getSharedPreferences("name_pass_mac", MODE_PRIVATE);
                        userNameEditText.setText(preferences.getString("name", ""));
                        passwordEditText.setText(preferences.getString("pass", ""));
                        macAddrEditText.setText(preferences.getString("mac", ""));
                        loginButton.setEnabled(false);
                        loginButton.setText("无限重连中");
                        keepBinder.login(s[0], s[1], s[2], context);
                    } else {
                        // 读取设置
                        isAutoReconnect = settingsPreferences.getBoolean(SettingsActivity.AUTO_RECONNECT, false);
                        adjustUI(false);
                        loginButton.setEnabled(true);
                    }
                }
            });
        }

        @Override
        public void logoutSucceed() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 注销成功后暂时还不允许重新登录，等待后台释放资源
                    Toast.makeText(context, "注销成功", Toast.LENGTH_SHORT).show();
                    // adjustUI(false);
                    status = STATUS.offline;
                }
            });
        }

        @Override
        public void invalidNameOrPass() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "用户名或密码错误", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void invalidMAC() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "MAC地址错误", Toast.LENGTH_SHORT).show();
                }
            });
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: 创建界面");
        // 初始化各个控件
        userNameEditText = findViewById(R.id.user_name_editText);
        passwordEditText = findViewById(R.id.password_editText);
        macAddrEditText = findViewById(R.id.mac_addr_editText);
        loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(this);
        // 启动服务
        Intent intent = new Intent(this, KeepService.class);
        startService(intent);
        // 绑定服务
        bindService(intent, connection, BIND_AUTO_CREATE);
        // 读取保存的用户名密码MAC地址
        Log.d(TAG, "onCreate: 读取保存的密码");
        SharedPreferences preferences = getSharedPreferences("name_pass_mac", MODE_PRIVATE);
        userNameEditText.setText(preferences.getString("name", ""));
        passwordEditText.setText(preferences.getString("pass", ""));
        macAddrEditText.setText(preferences.getString("mac", ""));
        // 读取设置
        SharedPreferences settingsPreferences = getSharedPreferences(SettingsActivity.FILE_NAME, MODE_PRIVATE);
        isAutoReconnect = settingsPreferences.getBoolean(SettingsActivity.AUTO_RECONNECT, false);
        isNotSave = settingsPreferences.getBoolean(SettingsActivity.DO_NOT_SAVE, false);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart: 重启界面");
        // 读取保存的用户名密码MAC地址
        SharedPreferences preferences = getSharedPreferences("name_pass_mac", MODE_PRIVATE);
        userNameEditText.setText(preferences.getString("name", ""));
        passwordEditText.setText(preferences.getString("pass", ""));
        macAddrEditText.setText(preferences.getString("mac", ""));
        // 读取设置
        SharedPreferences settingsPreferences = getSharedPreferences(SettingsActivity.FILE_NAME, MODE_PRIVATE);
        isAutoReconnect = settingsPreferences.getBoolean(SettingsActivity.AUTO_RECONNECT, false);
        isNotSave = settingsPreferences.getBoolean(SettingsActivity.DO_NOT_SAVE, false);
        // 设置当前状态以及按钮是否可用
        Log.d(TAG, "onRestart: 获取是否在线的信息");
        status = keepBinder.isOnline() ? STATUS.online : STATUS.offline;
        adjustUI(keepBinder.isOnline());
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.login_button: {
                if (status == STATUS.offline) {
                    String userName = userNameEditText.getText().toString();
                    String password = passwordEditText.getText().toString();
                    String macAddr = macAddrEditText.getText().toString();
                    if (!checkInput(userName, password, macAddr)) {
                        Toast.makeText(this, "输入有误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    status = STATUS.online;
                    adjustUI(true);
                    loginButton.setEnabled(false);
                    keepBinder.login(userName, password, macAddr, context);
                } else if (status == STATUS.online){
                    loginButton.setEnabled(false);
                    isAutoReconnect = false;
                    keepBinder.logout();
                }
                break;
            }
            default:
        }
    }

    private boolean checkInput(String name, String pass, String mac) {
        if ("".equals(name)) return false;
        if ("".equals(pass)) return false;
        if ("".equals(mac)) return false;
        if (mac.length() != 12) return false;
        for (int i = 0; i < 12; i++) {
            char x = mac.charAt(i);
            if (!((x <= '9' && x >= '0') || (x >= 'a' && x <= 'z'))) return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 解绑服务
        Log.d(TAG, "onDestroy: 正在解绑服务");
        unbindService(connection);
        if (!keepBinder.isOnline()) {
            stopService(new Intent(this, KeepService.class));
        }
        if (isNotSave) {
            SharedPreferences.Editor editor = getSharedPreferences("name_pass_mac", MODE_PRIVATE).edit();
            editor.clear();
            editor.apply();
            Log.d(TAG, "onDestroy: 已清除密码");
        }
    }

    private void adjustUI(boolean online) {
        if (online) {
            userNameEditText.setKeyListener(null);
            passwordEditText.setKeyListener(null);
            macAddrEditText.setKeyListener(null);
            loginButton.setText(R.string.logout);
        } else {
            KeyListener keyListener = (new EditText(getApplicationContext())).getKeyListener();
            userNameEditText.setKeyListener(keyListener);
            passwordEditText.setKeyListener(keyListener);
            macAddrEditText.setKeyListener(keyListener);
            loginButton.setText(R.string.login);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            }
            default:
                break;
        }
        return true;
    }
}
