package com.mx.dxinl.quicklauncher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mx.dxinl.quicklauncher.services.LauncherService;

/**
 * Created by Deng Xinliang on 2016/8/4.
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("test", "test");
        context.startService(new Intent(context, LauncherService.class));
    }
}
