package com.example.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.example.gui.MainActivity;
import com.example.drclient.R;
import com.example.drcom.DrcomTask;
import com.example.drcom.STATUS;

public class KeepService extends Service {
    private static final String TAG = "KeepService";
    private KeepBinder binder = new KeepBinder();
    private KeepService keepService;
    private UIController uiController;
    private KeepListener keepListener = new KeepListener() {
        @Override
        public void keepingAlive(String content) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification notification;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel("com.example.service.KeepService", "DrClientChannel", NotificationManager.IMPORTANCE_LOW);
                channel.enableVibration(false);
                channel.enableLights(false);
                notificationManager.createNotificationChannel(channel);
                notification = new NotificationCompat.Builder(KeepService.this, "com.example.service.KeepService")
                        .setContentTitle(content)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(PendingIntent.getActivity(KeepService.this, 0, new Intent(KeepService.this, MainActivity.class), 0))
                        .build();
            } else {
                notification = new NotificationCompat.Builder(KeepService.this, "com.example.service.KeepService")
                        .setVibrate(new long[]{0L})
                        .setContentTitle(content)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(PendingIntent.getActivity(KeepService.this, 0, new Intent(KeepService.this, MainActivity.class), 0))
                        .build();
            }
            startForeground(1, notification);
        }

        @Override
        public void informLogStatus(STATUS status, String[] s) {
            if (status == STATUS.offline) {
                uiController.offline();
                stopForeground(true);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                manager.cancelAll();
            } else if (status == STATUS.online) {
                uiController.loggedIn(s);
            }
        }

        @Override
        public void informCanLoginNow(boolean canReconnect, String[] s) {
            drcomTask = new DrcomTask(keepListener);
            drcomTask.canReconnect = canReconnect;
            uiController.canLoginNow(s);
        }

        @Override
        public void informLogoutSucceed() {
            stopForeground(true);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.cancelAll();
            uiController.logoutSucceed();
        }

        @Override
        public void informInvalidNameOrPass() {
            uiController.invalidNameOrPass();
        }

        @Override
        public void informInvalidMAC() {
            uiController.invalidMAC();
        }
    };
    private DrcomTask drcomTask = new DrcomTask(keepListener);
    private Context contextInMainActivity;

    public KeepService() {
        keepService = this;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: 已启动服务");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: 已停止服务");
    }

    public class KeepBinder extends Binder {
        public void setUIController(UIController uiController) {
            keepService.uiController = uiController;
        }
        public void login(String name, String pass, String mac, Context context) {
            contextInMainActivity = context;
            drcomTask.setNamePassMAC(name, pass, mac);
            drcomTask.execute();
        }
        public void logout() {
            drcomTask.logoutNow();
        }
        public boolean isOnline() {
            Log.d(TAG, "isOnline: 在service中查看是否在线");
            return drcomTask.isOnline();
        }
        public boolean canReconnect() {
            return drcomTask.canReconnect;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
