package com.example.gui;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.widget.CompoundButton;

import com.example.drclient.R;

public class SettingsActivity extends AppCompatActivity {
    private SwitchCompat autoReconnectSwitch;
    private SwitchCompat doNotSaveSwitch;
    private SharedPreferences sharedPreferences;
    public static final String FILE_NAME = "settings";
    public static final String AUTO_RECONNECT = "auto_reconnect";
    public static final String DO_NOT_SAVE = "not_save";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        // 获取保存的设置信息
        sharedPreferences = getSharedPreferences(FILE_NAME, MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        // 设置控件
        autoReconnectSwitch = findViewById(R.id.auto_reconnect_when_disconnected_switch);
        doNotSaveSwitch = findViewById(R.id.do_not_save_switch);
        autoReconnectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.remove(AUTO_RECONNECT);
                editor.putBoolean(AUTO_RECONNECT, isChecked);
                editor.apply();
            }
        });
        doNotSaveSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.remove(DO_NOT_SAVE);
                editor.putBoolean(DO_NOT_SAVE, isChecked);
                editor.apply();
            }
        });
        // 还原控件的设置选项
        autoReconnectSwitch.setChecked(sharedPreferences.getBoolean(AUTO_RECONNECT, false));
        doNotSaveSwitch.setChecked(sharedPreferences.getBoolean(DO_NOT_SAVE, false));
    }
}
