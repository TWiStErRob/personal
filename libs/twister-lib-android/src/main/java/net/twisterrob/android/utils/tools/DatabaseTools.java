package net.twisterrob.android.utils.tools;

import java.util.Locale;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import net.twisterrob.android.db.DatabaseOpenHelper;

public class DatabaseTools {
	public static String escapeLike(Object string, char escape) {
		return string.toString().replace("%", escape + "%").replace("_", escape + "_");
	}
	public static String dbToString(final SQLiteDatabase database) {
		int version = database != null? database.getVersion() : 0;
		String path = database != null? database.getPath() : null;
		return String.format(Locale.ROOT, "v%d@%s", version, path);
	}

	public static int getOptionalInt(Cursor cursor, String columnName, int defaultValue) {
		int col = cursor.getColumnIndex(columnName);
		if (col != DatabaseOpenHelper.CURSOR_NO_COLUMN) {
			return cursor.getInt(col);
		}
		return defaultValue;
	}
	public static Integer getOptionalInt(Cursor cursor, String columnName) {
		int col = cursor.getColumnIndex(columnName);
		if (col != DatabaseOpenHelper.CURSOR_NO_COLUMN) {
			return cursor.isNull(col)? null : cursor.getInt(col);
		}
		return null;
	}
	public static long getOptionalLong(Cursor cursor, String columnName, long defaultValue) {
		int col = cursor.getColumnIndex(columnName);
		if (col != DatabaseOpenHelper.CURSOR_NO_COLUMN) {
			return cursor.getLong(col);
		}
		return defaultValue;
	}
	public static Long getOptionalLong(Cursor cursor, String columnName) {
		int col = cursor.getColumnIndex(columnName);
		if (col != DatabaseOpenHelper.CURSOR_NO_COLUMN) {
			return cursor.isNull(col)? null : cursor.getLong(col);
		}
		return null;
	}
	public static String getOptionalString(Cursor cursor, String columnName) {
		return getOptionalString(cursor, columnName, null);
	}
	public static String getOptionalString(Cursor cursor, String columnName, String defaultValue) {
		int col = cursor.getColumnIndex(columnName);
		if (col != DatabaseOpenHelper.CURSOR_NO_COLUMN) {
			return cursor.isNull(col)? null : cursor.getString(col);
		}
		return defaultValue;
	}

	public static Long singleResult(@NonNull Cursor cursor) {
		try {
			if (cursor.getCount() == 0 || cursor.getColumnCount() == 0) {
				throw new IllegalArgumentException("Empty cursor");
			}
			if (1 < cursor.getCount()) {
				throw new IllegalArgumentException("Multiple rows returned");
			}
			if (1 < cursor.getColumnCount()) {
				throw new IllegalArgumentException("Multiple columns returned");
			}
			if (!cursor.moveToFirst()) {
				throw new IllegalArgumentException("Cannot move to first item");
			}
			return cursor.isNull(0)? null : cursor.getLong(0);
		} finally {
			cursor.close();
		}
	}

	public static String singleResultFromColumn(@NonNull Cursor cursor, @NonNull String columnName) {
		try {
			if (cursor.getCount() == 0 || cursor.getColumnCount() == 0) {
				throw new IllegalArgumentException("Empty cursor");
			}
			if (1 < cursor.getCount()) {
				throw new IllegalArgumentException("Multiple rows returned");
			}
			if (!cursor.moveToFirst()) {
				throw new IllegalArgumentException("Cannot move to first item");
			}
			int columnIndex = cursor.getColumnIndexOrThrow(columnName);
			return cursor.isNull(columnIndex)? null : cursor.getString(columnIndex);
		} finally {
			cursor.close();
		}
	}
}
