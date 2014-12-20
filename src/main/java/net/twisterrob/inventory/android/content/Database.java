package net.twisterrob.inventory.android.content;

import java.util.Arrays;

import org.slf4j.*;

import android.content.Context;
import android.content.res.Resources;
import android.database.*;
import android.database.sqlite.*;

import net.twisterrob.android.db.DatabaseOpenHelper;
import net.twisterrob.android.utils.tools.*;
import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.content.contract.Category;
import net.twisterrob.java.utils.StringTools;

public class Database {
	private static final Logger LOG = LoggerFactory.getLogger(Database.class);

	private final Context m_context;
	private final Resources m_resources;
	private final DatabaseOpenHelper m_helper;

	public Database(Context context) {
		m_context = context;
		m_resources = context.getResources();
		m_helper = new DatabaseOpenHelper(context, "MagicHomeInventory", 1) {
			@Override
			public void onConfigure(SQLiteDatabase db) {
				super.onConfigure(db);
				db.execSQL("PRAGMA recursive_triggers = TRUE;");
			}
		};
		// TODO App.getPrefEditor().remove(Prefs.CURRENT_LANGUAGE).apply();
		m_helper.setDumpOnOpen(false);
	}

	public SQLiteDatabase getReadableDatabase() {
		return m_helper.getReadableDatabase();
	}

	public SQLiteDatabase getWritableDatabase() {
		return m_helper.getWritableDatabase();
	}

	public DatabaseOpenHelper getHelper() {
		return m_helper;
	}

	private void execSQL(int queryResource, Object... params) {
		execSQL(getWritableDatabase(), queryResource, params);
	}
	private void execSQL(SQLiteDatabase db, int queryResource, Object... params) {
		if (queryResource != R.string.query_category_cache_update) {
			LOG.trace("execSQL({}, {})", m_resources.getResourceEntryName(queryResource), Arrays.toString(params));
		}
		db.execSQL(m_resources.getString(queryResource), params);
	}

	private Cursor rawQuery(int queryResource, Object... params) {
		return rawQuery(getReadableDatabase(), queryResource, params);
	}
	private Cursor rawQuery(SQLiteDatabase db, int queryResource, Object... params) {
		LOG.trace("rawQuery({}, {})", m_resources.getResourceEntryName(queryResource), Arrays.toString(params));
		return db.rawQuery(m_resources.getString(queryResource), StringTools.toStringArray(params));
	}
	private Long getID(int queryResource, Object... params) {
		Cursor cursor = rawQuery(queryResource, params);
		try {
			if (cursor.moveToFirst()) {
				return cursor.getLong(0);
			} else {
				return null;
			}
		} finally {
			cursor.close();
		}
	}

	@SuppressWarnings("resource")
	private long rawInsert(int insertResource, Object... params) {
		LOG.trace("rawInsert({}, {})", m_resources.getResourceEntryName(insertResource), Arrays.toString(params));
		SQLiteDatabase db = getWritableDatabase();

		SQLiteStatement insert = db.compileStatement(m_resources.getString(insertResource));
		try {
			for (int i = 0; i < params.length; ++i) {
				DatabaseUtils.bindObjectToProgram(insert, i + 1, params[i]);
			}
			return insert.executeInsert();
		} finally {
			insert.close();
		}
	}

	public Cursor listPropertyTypes(CharSequence nameFilter) {
		if (nameFilter == null || nameFilter.toString().trim().isEmpty()) {
			return listPropertyTypes();
		}
		return rawQuery(R.string.query_property_types_filtered, "%" + DatabaseTools.escapeLike(nameFilter, '\\') + "%");
	}
	public Cursor listPropertyTypes() {
		return rawQuery(R.string.query_property_types);
	}
	public Cursor listRoomTypes() {
		return rawQuery(R.string.query_room_types);
	}
	public Cursor listProperties() {
		return rawQuery(R.string.query_properties);
	}
	public Cursor getProperty(long propertyID) {
		return rawQuery(R.string.query_property, propertyID);
	}
	public Cursor listRooms() {
		return rawQuery(R.string.query_rooms);
	}
	public Cursor listRooms(long propertyID) {
		return rawQuery(R.string.query_rooms_by_property, propertyID);
	}
	public Cursor getRoom(long roomID) {
		return rawQuery(R.string.query_room, roomID);
	}
	public Cursor listItemCategories() {
		return rawQuery(R.string.query_item_categories, Category.INTERNAL);
	}
	public Cursor listItems(long parentID) {
		return rawQuery(R.string.query_items, parentID);
	}
	public Cursor listItemsInRoom(long roomID) {
		return rawQuery(R.string.query_items_in_room, roomID);
	}
	public Cursor listItemsForCategory(long categoryID, boolean include) {
		if (include) {
			return rawQuery(R.string.query_items_in_category, categoryID);
		} else {
			return rawQuery(R.string.query_items_by_category, categoryID);
		}
	}
	public Cursor listItemParents(long itemID) {
		return rawQuery(R.string.query_item_parents, itemID);
	}

	public Cursor getItem(long itemID) {
		return rawQuery(R.string.query_item, itemID);
	}
	public Cursor listCategories(long parentID) {
		return rawQuery(R.string.query_categories, parentID);
	}
	public Cursor getCategory(long itemID) {
		return rawQuery(R.string.query_category, itemID);
	}

	public long createProperty(long type, String name, String description, String image) {
		return rawInsert(R.string.query_property_create, type, name, description, image);
	}
	public Long findProperty(String name) {
		return getID(R.string.query_property_find, name);
	}
	public void updateProperty(long id, long type, String name, String description, String image) {
		execSQL(R.string.query_property_update, type, name, description, image, id);
	}
	public void deleteProperty(long id) {
		execSQL(R.string.query_property_delete, id);
	}

	public long createRoom(long propertyID, long type, String name, String description, String image) {
		rawInsert(R.string.query_room_create, propertyID, type, name, description, image);
		return findRoom(propertyID, name); // last_insert_rowid() doesn't work with INSTEAD OF INSERT triggers on VIEWs
	}
	public Long findRoom(long propertyID, String name) {
		return getID(R.string.query_room_find, propertyID, name);
	}
	public void updateRoom(long id, long type, String name, String description, String image) {
		execSQL(R.string.query_room_update, type, name, description, image, id);
	}
	public void deleteRoom(long id) {
		execSQL(R.string.query_room_delete, id);
	}
	public void moveRoom(long id, long propertyID) {
		execSQL(R.string.query_room_move, propertyID, id);
	}
	public void moveRooms(long propertyID, long[] roomIDs) {
		SQLiteDatabase db = getWritableDatabase();
		try {
			db.beginTransaction();
			for (long roomID : roomIDs) {
				execSQL(db, R.string.query_room_move, propertyID, roomID);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	private long createItem(Long parentID, long category, String name, String description, String image) {
		return rawInsert(R.string.query_item_create, parentID, category, name, description, image);
	}
	public long createItem(long parentID, long category, String name, String description, String image) {
		return createItem((Long)parentID, category, name, description, image);
	}
	public Long findItem(long parentID, String name) {
		return getID(R.string.query_item_find, parentID, name);
	}
	public void updateItem(long id, long category, String name, String description, String image) {
		execSQL(R.string.query_item_update, category, name, description, image, id);
	}
	public void deleteItem(long id) {
		execSQL(R.string.query_item_delete, id);
	}
	public void moveItem(long id, long parentID) {
		execSQL(R.string.query_item_move, parentID, id);
	}
	public void moveItems(long parentID, long[] itemIDs) {
		SQLiteDatabase db = getWritableDatabase();
		try {
			db.beginTransaction();
			for (long itemID : itemIDs) {
				execSQL(db, R.string.query_item_move, parentID, itemID);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public Cursor searchSuggest(String query) {
		query = fixQuery(query);
		return rawQuery(R.string.query_search_suggest, query);
	}

	public Cursor search(String query) {
		query = fixQuery(query);
		return rawQuery(R.string.query_search, query);
	}

	private static String fixQuery(String query) {
		if (query.contains("*")) {
			return query;
		}
		return query.trim().replaceAll("\\s+", "*") + "*";
	}

	public void updateCategoryCache() {
		LOG.info("Updating category name cache");
		@SuppressWarnings("resource")
		SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = rawQuery(db, R.string.query_category_cache_names);
			try {
				while (cursor.moveToNext()) {
					String resourceName = cursor.getString(0);
					String displayName = String.valueOf(AndroidTools.getText(m_context, resourceName));
					execSQL(db, R.string.query_category_cache_update, displayName, resourceName);
				}
				db.setTransactionSuccessful();
			} finally {
				cursor.close();
			}
		} finally {
			db.endTransaction();
		}
	}

	public Cursor export() {
		return rawQuery(R.string.query_export);
	}
}
