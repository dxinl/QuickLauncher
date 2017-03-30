package com.mx.dxinl.quicklauncher.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by Deng Xinliang on 2016/8/4.
 *
 * Simple database util.
 */
public class DatabaseUtil {
    public static final String QUERY_PKG_NAME_SQL = "SELECT " + DatabaseHelper.COLUMN_PKG_NAME
            + " FROM " + DatabaseHelper.PKG_NAME_TABLE;

    private static DatabaseUtil sInstance;
    private final SQLiteDatabase mDb;

    private DatabaseUtil(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        mDb = dbHelper.getWritableDatabase();
    }

    public static DatabaseUtil createDbUtil(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseUtil(context);
        }
        return sInstance;
    }

    public void execSQL(String sql, String[] args) {
        mDb.execSQL(sql, args);
    }

    public Cursor query(String sql, String[] selectionArgs) {
        return mDb.rawQuery(sql, selectionArgs);
    }

    public void bulkInsert(String table, ContentValues[] valuesArray) {
        for (ContentValues values : valuesArray) {
            mDb.insert(table, null, values);
        }
    }

    public void delete(String table, String whereClause, String[] whereArgs) {
        mDb.delete(table, whereClause, whereArgs);
    }
}
