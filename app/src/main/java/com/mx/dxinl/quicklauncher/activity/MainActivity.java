package com.mx.dxinl.quicklauncher.activity;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
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
import com.mx.dxinl.quicklauncher.model.SimpleAppInfo;
import com.mx.dxinl.quicklauncher.utils.Utils;
import com.mx.dxinl.quicklauncher.model.DatabaseHelper;
import com.mx.dxinl.quicklauncher.model.DatabaseUtil;
import com.mx.dxinl.quicklauncher.service.LauncherService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private AppListAdapter mAdapter;
    private QuickLauncherAidlInterface mBinder;
    private AlertDialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(new Intent(MainActivity.this, LauncherService.class));
    }

    private void createDialog() {
        mDialog = new AlertDialog.Builder(this)
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
        mAdapter = new AppListAdapter(this, Utils.getAppsInfo(this));
        appList.setAdapter(mAdapter);
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
                List<String> selectedAppsName = mAdapter.getmSelectedAppsPkgName();
                List<String> add = new ArrayList<>(selectedAppsName.size());
                add.addAll(selectedAppsName);
                while (c.moveToNext()) {
                    String name = c.getString(c.getColumnIndex(DatabaseHelper.COLUMN_PKG_NAME));
                    if (!selectedAppsName.contains(name)) {
                        delete.add(name);
                    } else {
                        add.remove(name);
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
                        values.put(DatabaseHelper.COLUMN_PKG_NAME, add.get(i));
                        valuesArray[i] = values;
                    }
                    dbUtil.bulkInsert(DatabaseHelper.PKG_NAME_TABLE, valuesArray);
                }

                if (mBinder != null) {
                    try {
                        mBinder.updateLauncherList();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                return true;

            case R.id.refresh:
                mAdapter.updateData(Utils.getAppsInfo(this));
                return true;

	        case R.id.setting:
		        startActivity(new Intent(this, SettingActivity.class));
		        return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mBinder = QuickLauncherAidlInterface.Stub.asInterface(iBinder);
	    boolean checkAccessibility;
	    try {
		    checkAccessibility = mBinder.checkAccessibility();
	    } catch (RemoteException e) {
		    checkAccessibility = true;
	    }

	    if (!checkAccessibility) {
		    if (mDialog == null) {
			    createDialog();
		    }
		    mDialog.show();
	    }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mBinder = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(this);
    }

    private final class AppListAdapter extends RecyclerView.Adapter<ViewHolder> {
        private Context mContext;
        private List<SimpleAppInfo> mAppsNameIconPair = new ArrayList<>();
        private List<String> mSelectedAppsPkgName = new ArrayList<>();

        public AppListAdapter(Context context, List<ResolveInfo> appsInfo) {
            this.mContext = context;
            initNameIconPairInfo(appsInfo);
            initSelectedInfo();
        }

        private void initSelectedInfo() {
            mSelectedAppsPkgName.clear();
            Cursor c = DatabaseUtil.createDbUtil(getApplicationContext())
                    .query(DatabaseUtil.QUERY_PKG_NAME_SQL, null);
            if (c == null) {
                return;
            }

            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(DatabaseHelper.COLUMN_PKG_NAME));
                mSelectedAppsPkgName.add(name);
            }
            c.close();
        }

        public void updateData(List<ResolveInfo> appsInfo) {
            initNameIconPairInfo(appsInfo);
            initSelectedInfo();
            notifyDataSetChanged();
        }

        private void initNameIconPairInfo(List<ResolveInfo> appsInfo) {
            new AsyncTask<ResolveInfo, Object, Object>() {
                @Override
                protected Object doInBackground(ResolveInfo... params) {
                    getSimpleAppInfoList(params);
                    return null;
                }

                @Override
                protected void onPostExecute(Object o) {
                    notifyDataSetChanged();
                }
            }.execute(appsInfo.toArray(new ResolveInfo[appsInfo.size()]));
        }

        private synchronized void getSimpleAppInfoList(ResolveInfo[] params) {
            mAppsNameIconPair.clear();
            for (ResolveInfo appInfo : params) {
                PackageManager packageManager = mContext.getPackageManager();
                SimpleAppInfo simpleAppInfo = new SimpleAppInfo();
                simpleAppInfo.mName = appInfo.loadLabel(packageManager).toString();
                simpleAppInfo.mPkgName = appInfo.activityInfo.packageName;
                simpleAppInfo.mDrawable = appInfo.loadIcon(packageManager);
                mAppsNameIconPair.add(simpleAppInfo);
            }
        }

        public List<String> getmSelectedAppsPkgName() {
            return mSelectedAppsPkgName;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(mContext)
                    .inflate(R.layout.item_app_list, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.initItemView(mSelectedAppsPkgName, mAppsNameIconPair.get(position));
        }

        @Override
        public int getItemCount() {
            return mAppsNameIconPair.size();
        }
    }

    private final class ViewHolder extends RecyclerView.ViewHolder {
        private Context mContext;
        private ImageView mAppIcon;
        private TextView mAppName;

        public ViewHolder(View itemView) {
            super(itemView);
            mContext = itemView.getContext();

            mAppIcon = (ImageView) itemView.findViewById(R.id.app_icon);
            final int appIconSize = mContext.getResources().getDisplayMetrics().widthPixels / 6;
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mAppIcon.getLayoutParams();
            if (lp == null) {
                lp = new RelativeLayout.LayoutParams(appIconSize, appIconSize);
            } else {
                lp.width = appIconSize;
                lp.height = appIconSize;
            }
            mAppIcon.setLayoutParams(lp);

            mAppName = (TextView) itemView.findViewById(R.id.app_name);
        }

        public void initItemView(List<String> selectedAppsName, SimpleAppInfo info) {
            int bkgColor;
            boolean contains = selectedAppsName.contains(info.mPkgName);
            if (contains) {
                bkgColor = getResources().getColor(R.color.green_100);
            } else {
                bkgColor = Color.WHITE;
            }
            ((CardView) itemView).setCardBackgroundColor(bkgColor);
            mAppName.setText(info.mName);
            mAppIcon.setImageDrawable(info.mDrawable);

            setOnItemClick(selectedAppsName, info.mPkgName);
        }

        public void setOnItemClick(final List<String> selectedAppsName, final String name) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int bkgColor;
                    if (selectedAppsName.contains(name)) {
                        bkgColor = Color.WHITE;
                        selectedAppsName.remove(name);
                    } else {
                        bkgColor = getResources().getColor(R.color.green_100);
                        selectedAppsName.add(name);
                    }
                    ((CardView) itemView).setCardBackgroundColor(bkgColor);
                }
            });
        }
    }

}
