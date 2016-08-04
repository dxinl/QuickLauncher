package com.mx.dxinl.quicklauncher;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mx.dxinl.quicklauncher.model.AppsInfoUtil;
import com.mx.dxinl.quicklauncher.model.DatabaseHelper;
import com.mx.dxinl.quicklauncher.model.DatabaseUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dxinl on 2016/8/2.
 */
public class LauncherService extends Service {
    private static final int UPDATE_IC_LIST = 0x0408;
    private static final String CONFIG_CHANGE_ACTION = "android.intent.action.CONFIGURATION_CHANGED";

    private final QuickLauncherHandler handler = new QuickLauncherHandler(this);
    private RecyclerView icList;
    private final QuickLauncherAidlInterface.Stub binder = new QuickLauncherAidlInterface.Stub() {
        @Override
        public void updateLauncherList() throws RemoteException {
            if (icList.getVisibility() == View.VISIBLE) {
                handler.sendEmptyMessage(UPDATE_IC_LIST);
            }
        }
    };
    private ImageView trigger;
    private WindowManager windowManager;
    private View launcher;
    private final BroadcastReceiver configChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CONFIG_CHANGE_ACTION)) {
                onScreenOrientationChanged();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(CONFIG_CHANGE_ACTION);
        registerReceiver(configChangedReceiver, filter);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("test", "onStart");
        performCreateLauncher();
        return super.onStartCommand(intent, flags, startId);
    }

    private void performCreateLauncher() {
        if (launcher == null) {
            Context context = getApplicationContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            launcher = inflater.inflate(R.layout.service_launcher, null);
            LayoutParams params = generateLayoutParams();

            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.addView(launcher, params);

            initLauncherChildren(launcher);
        }
    }

    @NonNull
    private LayoutParams generateLayoutParams() {
        LayoutParams params = new LayoutParams();
        params.type = LayoutParams.TYPE_PHONE;
        params.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        params.format = LayoutParams.LAYOUT_CHANGED;
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        return params;
    }

    private void initLauncherChildren(View launcher) {
        icList = (RecyclerView) launcher.findViewById(R.id.ic_list);
        trigger = (ImageView) launcher.findViewById(R.id.touch_trigger);

        initIconList();
        initTrigger();
    }

    private void initIconList() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
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
        if (params == null) {
            params = new LinearLayout.LayoutParams(icListWidth, icListHeight);
        } else {
            params.width = icListWidth;
            params.height = icListHeight;
        }
        icList.setLayoutParams(params);
        icList.setAdapter(new IconListAdapter(getAppsInfo()));
        icList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
    }

    private void initTrigger() {
        changeTriggerSize(false);
        trigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (icList.getVisibility() == View.GONE) {
                    showIconList();
                } else {
                    hideIconList();
                }
            }
        });
    }

    private void changeTriggerSize(boolean icListVisible) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
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
        if (params == null) {
            params = new LinearLayout.LayoutParams(width, height);
        } else {
            params.width = width;
            params.height = height;
        }

        trigger.setLayoutParams(params);
    }

    private void showIconList() {
        icList.setVisibility(View.VISIBLE);
        updateIcList();
        changeTriggerSize(true);
        trigger.setImageResource(R.mipmap.arrow_reverse);
    }

    private void updateIcList() {
        IconListAdapter adapter = (IconListAdapter) icList.getAdapter();
        adapter.updateData(getAppsInfo());
    }

    private void hideIconList() {
        icList.setVisibility(View.GONE);
        changeTriggerSize(false);
        trigger.setImageResource(R.mipmap.quick_launcher);
    }

    private List<ResolveInfo> getAppsInfo() {
        List<ResolveInfo> allInfo = AppsInfoUtil.getAppsInfo(this);
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(configChangedReceiver);
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
                service.updateIcList();
            }
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
            DisplayMetrics displayMetrics = context.getApplicationContext()
                    .getResources().getDisplayMetrics();
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            int size;
            if (screenHeight < screenWidth) {
                size = screenHeight;
            } else {
                size = screenWidth;
            }

            final int appIconMargin = getResources().getDimensionPixelSize(R.dimen.ic_list_item_margin);
            final int appIconSize = size / 6 - 2 * appIconMargin;
            ViewGroup.LayoutParams params = appIcon.getLayoutParams();
            if (params == null) {
                params = new LinearLayout.LayoutParams(appIconSize, appIconSize);
            } else {
                params.width = appIconSize;
                params.height = appIconSize;
            }
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
