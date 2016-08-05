package com.mx.dxinl.quicklauncher.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.mx.dxinl.quicklauncher.InfoActivity;
import com.mx.dxinl.quicklauncher.QuickLauncherAidlInterface;
import com.mx.dxinl.quicklauncher.R;
import com.mx.dxinl.quicklauncher.model.DatabaseHelper;
import com.mx.dxinl.quicklauncher.model.DatabaseUtil;
import com.mx.dxinl.quicklauncher.model.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dxinl on 2016/8/2.
 */
public class LauncherService extends Service {
    private static final String LAUNCHER_POSITION = "LauncherPosition";
    private static final String PORTRAIT_POSITION_X = "Portrait_X";
    private static final String PORTRAIT_POSITION_Y = "Portrait_Y";
    private static final String LANDSCAPE_POSITION_X = "Landscape_X";
    private static final String LANDSCAPE_POSITION_Y = "Landscape_Y";
    private static final String CONFIG_CHANGE_ACTION = "android.intent.action" +
            ".CONFIGURATION_CHANGED";
    private static final String ACCESSIBILITY_NAME = "com.mx.dxinl.quicklauncher/.service" +
            ".NavBtnAccessibilityService";

    private static final int UPDATE_IC_LIST = 0x0408;

    private final QuickLauncherHandler handler = new QuickLauncherHandler(this);
    private final QuickLauncherAidlInterface.Stub binder = new QuickLauncherAidlInterface.Stub() {
        @Override
        public void updateLauncherList() throws RemoteException {
            handler.sendEmptyMessage(UPDATE_IC_LIST);
        }
    };
    private final TriggerTouchListener triggerTouchListener = new TriggerTouchListener();

    private float moveThreshold;
    private boolean hasOrientationChanged;
    private View launcher;
    private ImageView trigger;
    private RecyclerView icList;
    private WindowManager windowManager;
    private final BroadcastReceiver configChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CONFIG_CHANGE_ACTION)) {
                onScreenOrientationChanged();
            }
        }
    };
    private Intent accessibilityIntent;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(CONFIG_CHANGE_ACTION);
        registerReceiver(configChangedReceiver, filter);

        accessibilityIntent = new Intent(this, NavBtnAccessibilityService.class);
        if (!checkAccessibility()) {
            requestAccessibility();
        } else {
            startService(accessibilityIntent);
        }
    }

    private void requestAccessibility() {
        Intent intent = new Intent(this, InfoActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean checkAccessibility() {
        AccessibilityManager accessibilityManager
                = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> infoList
		        = accessibilityManager.getInstalledAccessibilityServiceList();
        for (AccessibilityServiceInfo info : infoList) {
            if (info.getId().equals(ACCESSIBILITY_NAME)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (launcher == null || hasOrientationChanged) {
            hasOrientationChanged = false;
            performCreateLauncher();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void performCreateLauncher() {
        Context context = getApplicationContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        moveThreshold = ViewConfiguration.get(context).getScaledTouchSlop();

        launcher = inflater.inflate(R.layout.service_launcher, null);
        LayoutParams params = generateLayoutParams();

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(launcher, params);

        initLauncherChildren(launcher);
    }

    @NonNull
    private LayoutParams generateLayoutParams() {
        LayoutParams params = new LayoutParams();
        //noinspection WrongConstant
        params.type = getParamsType();
        params.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        params.format = LayoutParams.LAYOUT_CHANGED;
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;

        int[] position = getLauncherPosition();
        params.x = position[0];
        params.y = position[1];
        return params;
    }

    private int getParamsType() {
        int type;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            type = LayoutParams.TYPE_PHONE;
        } else {
            type = LayoutParams.TYPE_TOAST;
        }
        return type;
    }

    public int[] getLauncherPosition() {
        int[] position = new int[2];
        SharedPreferences sp = getSharedPreferences(LAUNCHER_POSITION, MODE_PRIVATE);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            position[0] = sp.getInt(LANDSCAPE_POSITION_X, 0);
            position[1] = sp.getInt(LANDSCAPE_POSITION_Y, 0);
        } else {
            position[0] = sp.getInt(PORTRAIT_POSITION_X, 0);
            position[1] = sp.getInt(PORTRAIT_POSITION_Y, 0);
        }
        return position;
    }

    private void initLauncherChildren(View launcher) {
        icList = (RecyclerView) launcher.findViewById(R.id.ic_list);
        trigger = (ImageView) launcher.findViewById(R.id.touch_trigger);

        initIconList();
        initTrigger();
    }

    private void initIconList() {
        int screenWidth = Utils.getScreenWidth(getApplicationContext());
        int screenHeight = Utils.getScreenHeight(getApplicationContext());
        final int icListWidth;
        final int icListHeight;
        if (screenHeight < screenWidth) {
            icListWidth = screenHeight / 6;
            icListHeight = screenHeight / 4 * 3;
        } else {
            icListWidth = screenWidth / 6;
            icListHeight = screenHeight / 5 * 3;
        }

        ViewGroup.LayoutParams params = icList.getLayoutParams();
        params.width = icListWidth;
        params.height = icListHeight;

        icList.setLayoutParams(params);
        icList.setAdapter(new IconListAdapter(getAppsInfo()));
        icList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
    }

    private void initTrigger() {
        changeTriggerSize(false);
        trigger.setOnTouchListener(triggerTouchListener);
    }

    private void changeLauncherPosition(int x, int y) {
        LayoutParams params = (LayoutParams) launcher.getLayoutParams();
        params.x += x;
        params.y += y;

        windowManager.updateViewLayout(launcher, params);
    }

    private void saveLauncherPosition(int x, int y) {
        SharedPreferences sp = getSharedPreferences(LAUNCHER_POSITION, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            editor.putInt(LANDSCAPE_POSITION_X, x);
            editor.putInt(LANDSCAPE_POSITION_Y, y);
        } else {
            editor.putInt(PORTRAIT_POSITION_X, x);
            editor.putInt(PORTRAIT_POSITION_Y, y);
        }
        editor.apply();
    }

    private void stickScreenEdge() {
        final LayoutParams params = (LayoutParams) launcher.getLayoutParams();
        int screenWidth = Utils.getScreenWidth(getApplicationContext());

        int size = trigger.getMeasuredWidth();
        int targetX;
        if (params.x + size / 2f > screenWidth / 2f) {
            targetX = screenWidth - size;
        } else {
            targetX = 0;
        }

        ValueAnimator stickEdgeAnimator = ValueAnimator.ofFloat(params.x, targetX);
        float duration = 300f / screenWidth * 2f * Math.abs(targetX - params.x);
        stickEdgeAnimator.setDuration((int) duration);
        stickEdgeAnimator.setInterpolator(new DecelerateInterpolator());
        stickEdgeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float f = (Float) valueAnimator.getAnimatedValue();
                params.x = (int) f;
                windowManager.updateViewLayout(launcher, params);
            }
        });

        stickEdgeAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                saveLauncherPosition(params.x, params.y);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        stickEdgeAnimator.start();
    }

    private void changeTriggerSize(boolean icListVisible) {
        int screenWidth = Utils.getScreenWidth(getApplicationContext());
        int screenHeight = Utils.getScreenHeight(getApplicationContext());
        final int size;
        if (screenHeight < screenWidth) {
            size = screenHeight / 6;
        } else {
            size = screenWidth / 6;
        }

        int width, height = size / 2;
        if (icListVisible) {
            width = size;
        } else {
            width = size / 2;
        }
        width -= 2 * getResources().getDimensionPixelSize(R.dimen.trigger_margin);
        ViewGroup.LayoutParams params = trigger.getLayoutParams();
        params.width = width;
        params.height = height;

        trigger.setLayoutParams(params);
    }

    private void showIconList() {
        changeIconListUI(true);
        updateIcList();

        changeTriggerSize(true);
        changeTriggerIcon(true);
    }

    private void changeIconListUI(boolean visible) {
        if (visible) {
            launcher.setAlpha(1f);
            icList.setVisibility(View.VISIBLE);
        } else {
            launcher.setAlpha(0.5f);
            icList.setVisibility(View.GONE);
        }
    }

    private void updateIcList() {
        IconListAdapter adapter = (IconListAdapter) icList.getAdapter();
        adapter.updateData(getAppsInfo());
    }

    private void changeTriggerIcon(boolean visible) {
        if (visible) {
            LayoutParams params = (LayoutParams) launcher.getLayoutParams();
            if (params.x > Utils.getScreenWidth(getApplicationContext()) / 2) {
                trigger.setImageResource(R.mipmap.arrow);
            } else {
                trigger.setImageResource(R.mipmap.arrow_reverse);
            }
        } else {
            trigger.setImageResource(R.mipmap.quick_launcher);
        }
    }

    private void hideIconList() {
        changeIconListUI(false);

        changeTriggerSize(false);
        changeTriggerIcon(false);
    }

    private List<ResolveInfo> getAppsInfo() {
        List<ResolveInfo> allInfo = Utils.getAppsInfo(this);
        List<ResolveInfo> showInfo = new ArrayList<>();

        DatabaseUtil dbUtil = DatabaseUtil.createDbUtil(getApplicationContext());
        Cursor c = dbUtil.query(DatabaseUtil.QUERY_PKG_NAME_SQL, null);
        if (c == null) {
            return showInfo;
        }
        while (c.moveToNext()) {
            String name = c.getString(c.getColumnIndex(DatabaseHelper.COLUMN_PKG_NAME));
            for (ResolveInfo info : allInfo) {
                if (info.activityInfo.packageName.equals(name)) {
                    showInfo.add(info);
                }
            }
        }
        c.close();

        return showInfo;
    }

    private void onScreenOrientationChanged() {
        windowManager.removeView(launcher);
        hasOrientationChanged = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(configChangedReceiver);
        stopService(accessibilityIntent);
    }

    private static final class QuickLauncherHandler extends Handler {
        private WeakReference<LauncherService> ref;

        public QuickLauncherHandler(LauncherService service) {
            ref = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            LauncherService service = ref.get();
            if (service == null) {
                return;
            }

            if (msg.what == UPDATE_IC_LIST) {
                if (service.icList.getVisibility() == View.VISIBLE) {
                    service.updateIcList();
                } else {
                    service.showIconList();
                }
            }
        }
    }

    private final class TriggerTouchListener implements View.OnTouchListener {
        private static final int LONG_PRESS_THRESHOLD = 500;

        private boolean moving = false;
        private boolean canDrag = false;
	    private boolean dragging = false;
	    private long downTime;
        private float downX, downY, lastX, lastY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = event.getRawX();
                    lastY = downY = event.getRawY();
                    downTime = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
	                if (moving) {
		                return true;
	                }

                    float moveX = event.getRawX();
                    float moveY = event.getRawY();

                    if (!canDrag && isDrag(moveX, moveY)) {
                        moving = true;
                        if (isIcListVisible()) {
                            return true;
                        }

	                    performGlobalAction(getAction(moveX, moveY));
                    } else if (!canDrag && isLongPress()) {
                        canDrag = true;
                        if (!isIcListVisible()) {
                            launcher.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        }
                    } else if (canDrag) {
                        if (isIcListVisible()) {
                            return true;
                        }

                        if (!dragging && (isDrag(moveX, moveY))) {
                            changeLauncherPosition((int) (moveX - lastX), (int) (moveY - lastY));
                            lastX = moveX;
                            lastY = moveY;
                            dragging = true;
                        } else if (dragging) {
                            changeLauncherPosition((int) (moveX - lastX), (int) (moveY - lastY));
                            lastX = moveX;
                            lastY = moveY;
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moving && !canDrag && !dragging) {
                        if (icList.getVisibility() == View.GONE) {
                            showIconList();
                        } else {
                            hideIconList();
                        }
                    } else if (canDrag) {
                        canDrag = false;
                        if (dragging) {
                            stickScreenEdge();
                            dragging = false;
                        }
                    } else {
                        moving = false;
                    }
                    return true;
            }
            return false;
        }

	    private int getAction(float moveX, float moveY) {
		    int action;
		    if (isHorizontalMove(moveX, moveY)) {
			    if (isRightMove(moveX)) {
				    action = AccessibilityService.GLOBAL_ACTION_BACK;
			    } else {
				    action = AccessibilityService.GLOBAL_ACTION_RECENTS;
			    }
		    } else {
			    if (isDownMove(moveY)) {
				    action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
			    } else {
				    action = AccessibilityService.GLOBAL_ACTION_HOME;
			    }
		    }
		    return action;
	    }

	    private boolean isHorizontalMove(float moveX, float moveY) {
		    return Math.abs(moveX - downX) > Math.abs(moveY - downY);
	    }

	    private boolean isDownMove(float moveY) {
		    return moveY - downY > 0;
	    }

	    private boolean isRightMove(float moveX) {
		    return moveX - downX > 0;
	    }

	    private void performGlobalAction(int action) {
		    AccessibilityService service = NavBtnAccessibilityService.getInstance();
		    if (service != null) {
		        service.performGlobalAction(action);
		    } else {
		        if (checkAccessibility()) {
		            startService(accessibilityIntent);
		        } else {
		            requestAccessibility();
		        }
		    }
	    }

	    private boolean isIcListVisible() {
            return icList.getVisibility() == View.VISIBLE;
        }

        private boolean isDrag(float moveX, float moveY) {
            return Math.abs(moveX - downX) > moveThreshold
                    || Math.abs(moveY - downY) > moveThreshold;
        }

        private boolean isLongPress() {
            return System.currentTimeMillis() - downTime >= LONG_PRESS_THRESHOLD;
        }
    }

    /**
     * Created by Deng Xinliang on 2016/8/3.
     */
    private final class ViewHolder extends RecyclerView.ViewHolder {
        private final PackageManager packageManager;
        private Context context;
        private ImageView appIcon;
        private TextView appName;

        public ViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();

            appIcon = (ImageView) itemView.findViewById(R.id.icon_app);
            int screenWidth = Utils.getScreenWidth(getApplicationContext());
            int screenHeight = Utils.getScreenHeight(getApplicationContext());
            int size;
            if (screenHeight < screenWidth) {
                size = screenHeight;
            } else {
                size = screenWidth;
            }

            final int appIconMargin = getResources().getDimensionPixelSize(R.dimen.ic_list_item_margin);
            final int appIconSize = size / 6 - 2 * appIconMargin;
            ViewGroup.LayoutParams params = appIcon.getLayoutParams();
            params.width = appIconSize;
            params.height = appIconSize;
            appIcon.setLayoutParams(params);

            appName = (TextView) itemView.findViewById(R.id.name_app);
            packageManager = context.getPackageManager();
        }

        public void initItemView(ResolveInfo info) {
            appIcon.setImageDrawable(info.loadIcon(packageManager));
            appName.setText(info.loadLabel(packageManager));
            setOnClickListener(info);
        }

        public void setOnClickListener(final ResolveInfo info) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launchIntent = packageManager
                            .getLaunchIntentForPackage(info.activityInfo.packageName);
                    if (launchIntent != null) {
                        context.startActivity(launchIntent);
                    }
                    hideIconList();
                }
            });
        }
    }

    /**
     * Created by Deng Xinliang on 2016/8/3.
     */
    private final class IconListAdapter extends RecyclerView.Adapter<ViewHolder> {
        private List<ResolveInfo> appsInfo = new ArrayList<>();

        public IconListAdapter(List<ResolveInfo> appsInfo) {
            this.appsInfo.addAll(appsInfo);
        }

        public void updateData(List<ResolveInfo> appsInfo) {
            this.appsInfo.clear();
            this.appsInfo.addAll(appsInfo);
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new ViewHolder(inflater.inflate(R.layout.item_icon_list, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ResolveInfo info = appsInfo.get(position);
            holder.initItemView(info);
        }

        @Override
        public int getItemCount() {
            return appsInfo.size();
        }
    }
}
