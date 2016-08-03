package com.mx.dxinl.quicklauncher;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;

/**
 * Created by Deng Xinliang on 2016/8/3.
 *
 */
public class PkgContentProvider extends ContentProvider {
    public static final String DATABASE_NAME = "QUICK_LAUNCHER";
    public static final String PKG_NAME = "pkg_name";
    public static final String SELECTED_PKG_NAME = "selected_" + PKG_NAME;
    public static final String PROVIDER_NAME = "com.mx.dxinl.quicklauncher";
    public static final String URI = "content://" + PROVIDER_NAME + "/selected";
    public static final Uri CONTENT_URI = Uri.parse(URI);

    private SQLiteDatabase db;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
        return db != null;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] strings, String s, String[] strings1, String s1) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(SELECTED_PKG_NAME);

        if (s1 == null || s1.equals("")) {
            s1 = "_id";
        }
        Cursor cursor = qb.query(db, strings, s, strings1, null, null, s1);
        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues contentValues) {
        long rowId = db.insert(SELECTED_PKG_NAME, "", contentValues);
        if (rowId >= 0) {
            Uri insertUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
            if (getContext() != null) {
                getContext().getContentResolver().notifyChange(insertUri, null);
            }
            return insertUri;
        }

        throw new SQLException("Failed to add a record into " + uri);
    }

    @Override
    public int delete(@NonNull Uri uri, String s, String[] strings) {
        int count = db.delete(SELECTED_PKG_NAME, s, strings);
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues contentValues, String s, String[] strings) {
        int count = db.update(SELECTED_PKG_NAME, contentValues, s, strings);
        if (getContext() != null) {
            getContext().getContentResolver().notify();
        }
        return count;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL("CREATE TABLE " + SELECTED_PKG_NAME +
                    "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    PKG_NAME + " TEXT NOT NULL);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + SELECTED_PKG_NAME);
            onCreate(sqLiteDatabase);
        }
    }
}
