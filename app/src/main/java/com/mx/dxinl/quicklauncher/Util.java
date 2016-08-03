package com.mx.dxinl.quicklauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * Created by Deng Xinliang on 2016/8/3.
 *
 */
public class Util {
    public static List<ResolveInfo> getAppsInfo(Context context) {
        final PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
    }
}
