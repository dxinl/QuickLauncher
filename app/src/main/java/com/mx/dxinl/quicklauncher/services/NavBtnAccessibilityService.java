package com.mx.dxinl.quicklauncher.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Binder;
import android.view.accessibility.AccessibilityEvent;

/**
 * Created by Deng Xinliang on 2016/8/5.
 *
 * AccessibilityService
 */
public class NavBtnAccessibilityService extends AccessibilityService {
    private static AccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static AccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }

    @Override
    public void onInterrupt() {

    }
}
