package com.mx.dxinl.quicklauncher.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * Created by Deng Xinliang on 2016/8/3.
 *
 * Simple utils.
 */
public class Utils {
    public static List<ResolveInfo> getAppsInfo(Context context) {
        final PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
    }

    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }
}
