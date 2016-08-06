package com.mx.dxinl.quicklauncher.activity;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mx.dxinl.quicklauncher.QuickLauncherAidlInterface;
import com.mx.dxinl.quicklauncher.R;
import com.mx.dxinl.quicklauncher.model.Utils;
import com.mx.dxinl.quicklauncher.model.DatabaseHelper;
import com.mx.dxinl.quicklauncher.model.DatabaseUtil;
import com.mx.dxinl.quicklauncher.services.LauncherService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private AppListAdapter adapter;
    private QuickLauncherAidlInterface binder;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(new Intent(MainActivity.this, LauncherService.class));
    }

    private void createDialog() {
        dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.accessibility_request))
                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(MainActivity.this, LauncherService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final RecyclerView appList = (RecyclerView) findViewById(R.id.app_list);
        adapter = new AppListAdapter(this, Utils.getAppsInfo(this));
        appList.setAdapter(adapter);
        appList.setLayoutManager(new GridLayoutManager(this, 3));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.done:
                DatabaseUtil dbUtil = DatabaseUtil.createDbUtil(getApplicationContext());

                Cursor c = dbUtil.query(DatabaseUtil.QUERY_PKG_NAME_SQL, null);
                if (c == null) {
                    return true;
                }

                List<String> delete = new ArrayList<>();
                List<ResolveInfo> resolveInfo = adapter.getSelectedInfo();
                List<ResolveInfo> add = new ArrayList<>(resolveInfo.size());
                add.addAll(resolveInfo);
                while (c.moveToNext()) {
                    String name = c.getString(c.getColumnIndex(DatabaseHelper.COLUMN_PKG_NAME));
                    ResolveInfo foundInfo = null;
                    for (ResolveInfo info : resolveInfo) {
                        if (info.activityInfo.packageName.equals(name)) {
                            foundInfo = info;
                            break;
                        }
                    }

                    if (foundInfo == null) {
                        delete.add(name);
                    } else {
                        add.remove(foundInfo);
                    }
                }
                c.close();

                if (delete.size() > 0) {
                    String[] names = delete.toArray(new String[delete.size()]);
                    StringBuilder where = new StringBuilder();
                    for (int i = 0, count = names.length; i < count; i++) {
                        where.append(DatabaseHelper.COLUMN_PKG_NAME + "= \"").append(names[i]).append("\"");
                        if (i < count - 1) {
                            where.append(" OR ");
                        }
                    }
                    dbUtil.delete(DatabaseHelper.PKG_NAME_TABLE, where.toString(), null);
                }

                if (add.size() > 0) {
                    int count = add.size();
                    ContentValues[] valuesArray = new ContentValues[count];
                    for (int i = 0; i < count; i++) {
                        ContentValues values = new ContentValues();
                        values.put(DatabaseHelper.COLUMN_PKG_NAME, add.get(i).activityInfo.packageName);
                        valuesArray[i] = values;
                    }
                    dbUtil.bulkInsert(DatabaseHelper.PKG_NAME_TABLE, valuesArray);
                }

                if (binder != null) {
                    try {
                        binder.updateLauncherList();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                return true;

            case R.id.refresh:
                adapter.updateData(Utils.getAppsInfo(this));
                return true;

	        case R.id.setting:
		        startActivity(new Intent(this, SettingActivity.class));
		        return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        binder = QuickLauncherAidlInterface.Stub.asInterface(iBinder);
	    boolean checkAccessibility;
	    try {
		    checkAccessibility = binder.checkAccessibility();
	    } catch (RemoteException e) {
		    checkAccessibility = true;
	    }

	    if (!checkAccessibility) {
		    if (dialog == null) {
			    createDialog();
		    }
		    dialog.show();
	    }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        binder = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(this);
    }

    private final class AppListAdapter extends RecyclerView.Adapter<ViewHolder> {
        private Context context;
        private List<ResolveInfo> appsInfo = new ArrayList<>();
        private List<ResolveInfo> selectedInfo = new ArrayList<>();

        public AppListAdapter(Context context, List<ResolveInfo> appsInfo) {
            this.context = context;
            this.appsInfo.addAll(appsInfo);

            initSelectedInfo();
        }

        private void initSelectedInfo() {
            selectedInfo.clear();
            Cursor c = DatabaseUtil.createDbUtil(getApplicationContext())
                    .query(DatabaseUtil.QUERY_PKG_NAME_SQL, null);
            if (c == null) {
                return;
            }

            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(DatabaseHelper.COLUMN_PKG_NAME));
                for (ResolveInfo info : appsInfo) {
                    if (info.activityInfo.packageName.equals(name)) {
                        selectedInfo.add(info);
                    }
                }
            }
            c.close();
        }

        public void updateData(List<ResolveInfo> appsInfo) {
            this.appsInfo.clear();
            this.appsInfo.addAll(appsInfo);
            initSelectedInfo();
            notifyDataSetChanged();
        }

        public List<ResolveInfo> getSelectedInfo() {
            return selectedInfo;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(context)
                    .inflate(R.layout.item_app_list, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.initItemView(selectedInfo, appsInfo.get(position));
        }

        @Override
        public int getItemCount() {
            return appsInfo.size();
        }
    }

    private final class ViewHolder extends RecyclerView.ViewHolder {
        private Context context;
        private ImageView appIcon;
        private TextView appName;

        public ViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();

            appIcon = (ImageView) itemView.findViewById(R.id.app_icon);
            final int appIconSize = context.getResources().getDisplayMetrics().widthPixels / 6;
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) appIcon.getLayoutParams();
            if (lp == null) {
                lp = new RelativeLayout.LayoutParams(appIconSize, appIconSize);
            } else {
                lp.width = appIconSize;
                lp.height = appIconSize;
            }
            appIcon.setLayoutParams(lp);

            appName = (TextView) itemView.findViewById(R.id.app_name);
        }

        public void initItemView(List<ResolveInfo> selectedInfo, ResolveInfo info) {
            int bkgColor;
            if (selectedInfo.contains(info)) {
                bkgColor = getResources().getColor(R.color.green_100);
            } else {
                bkgColor = Color.WHITE;
            }
            ((CardView) itemView).setCardBackgroundColor(bkgColor);
            appName.setText(info.loadLabel(context.getPackageManager()));
            appIcon.setImageDrawable(info.loadIcon(context.getPackageManager()));

            setOnItemClick(selectedInfo, info);
        }

        public void setOnItemClick(final List<ResolveInfo> selectedInfo, final ResolveInfo info) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int bkgColor;
                    if (selectedInfo.contains(info)) {
                        bkgColor = Color.WHITE;
                        selectedInfo.remove(info);
                    } else {
                        bkgColor = getResources().getColor(R.color.green_100);
                        selectedInfo.add(info);
                    }
                    ((CardView) itemView).setCardBackgroundColor(bkgColor);
                }
            });
        }
    }
}
