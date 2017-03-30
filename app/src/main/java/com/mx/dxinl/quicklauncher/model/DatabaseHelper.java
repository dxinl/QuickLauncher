package com.mx.dxinl.quicklauncher.model;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Deng Xinliang on 2016/8/4.
 *
 * Custom SQLiteOpenHelper
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "QUICK_LAUNCHER";
    public static final String COLUMN_PKG_NAME = "pkg_name";
    public static final String PKG_NAME_TABLE = "selected_" + COLUMN_PKG_NAME;
    public static final String GESTURE = "gesture";
    public static final String GLOBAL_ACTION = "global_action";
    public static final String GESTURE_GLOBAL_ACTION_TABLE = GESTURE + "_" + GLOBAL_ACTION;
    private static final int VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE " + PKG_NAME_TABLE
                + "(_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PKG_NAME + " TEXT NOT NULL);");
        sqLiteDatabase.execSQL("CREATE TABLE " + GESTURE_GLOBAL_ACTION_TABLE
                + "(_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GESTURE + " TEXT NOT NULL, "
                + GLOBAL_ACTION + " TEXT NOT NULL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + PKG_NAME_TABLE);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + GESTURE_GLOBAL_ACTION_TABLE);
        onCreate(sqLiteDatabase);
    }
}
