package net.twisterrob.inventory.android.content;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.slf4j.*;

import android.content.Context;
import android.content.res.Resources;
import android.database.*;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.*;

import net.twisterrob.android.db.DatabaseOpenHelper;
import net.twisterrob.android.utils.tools.*;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.inventory.android.database.*;
import net.twisterrob.java.annotations.DebugHelper;

import static net.twisterrob.android.utils.tools.DatabaseTools.*;
import static net.twisterrob.inventory.android.Constants.*;

@WorkerThread
@SuppressWarnings({"TryFinallyCanBeTryWithResources", "resource"})
public class Database extends VariantDatabase {
	private static final Logger LOG = LoggerFactory.getLogger(Database.class);
	public static final String NAME = "MagicHomeInventory";

	private final DatabaseOpenHelper m_helper;

	@AnyThread
	public Database(Context context) {
		this(context, context.getResources());
	}

	@AnyThread
	@VisibleForTesting
	public Database(Context hostContext, Resources resources) {
		super(resources);
		m_helper = new DatabaseOpenHelper(hostContext, NAME, 6, BuildConfig.DEBUG) {
			@Override public void onConfigure(SQLiteDatabase db) {
				super.onConfigure(db);
				// CONSIDER enabling auto_vacuum=INCREMENTAL as it speeds up a large delete a lot
				//DatabaseTools.setPragma(db, Pragma.AUTO_VACUUM, Pragma.AUTO_VACUUM_INCREMENTAL);
			}
			@Override protected String[] getDataFiles() {
				return new String[] {
						super.getDataFiles()[0],
						String.format("%s.data.Categories.sql", getDatabaseName())
				};
			}
		};
//		App.getPrefEditor().remove(Prefs.CURRENT_LANGUAGE).apply();
		m_helper.setDevMode(BuildConfig.DEBUG);
		m_helper.setAllowDump(DISABLE);
	}

	public SQLiteDatabase getReadableDatabase() {
		return m_helper.getReadableDatabase();
	}

	public SQLiteDatabase getWritableDatabase() {
		return m_helper.getWritableDatabase();
	}

	public File getFile() {
		return m_helper.getDatabaseFile();
	}

	@AnyThread
	public DatabaseOpenHelper getHelper() {
		return m_helper;
	}

	public @NonNull Database beginTransaction() {
		getWritableDatabase().beginTransaction();
		return this;
	}
	public void endTransaction() {
		getWritableDatabase().endTransaction();
	}
	public void setTransactionSuccessful() {
		getWritableDatabase().setTransactionSuccessful();
	}

	@VisibleForTesting void execSQL(@StringRes int queryResource, Object... params) {
		execSQL(getWritableDatabase(), queryResource, params);
	}

	@VisibleForTesting Cursor rawQuery(@StringRes int queryResource, Object... params) {
		return rawQuery(getReadableDatabase(), queryResource, params);
	}

	@VisibleForTesting long rawInsert(@StringRes int queryResource, Object... params) {
		return rawInsert(getReadableDatabase(), queryResource, params);
	}

	public Long getID(@StringRes int queryResource, Object... params) {
		Cursor cursor = rawQuery(queryResource, params);
		try {
			if (2 <= cursor.getCount()) {
				String name = m_resources.getResourceEntryName(queryResource);
				String paramString = Arrays.toString(params);
				throw new IllegalStateException(
						"Multiple rows found when querying ID for " + name + "(" + paramString + ")");
			}
			if (cursor.moveToFirst()) {
				return cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
			} else {
				return null;
			}
		} finally {
			cursor.close();
		}
	}

	// Filtered example
	// where pt.name like ? escape '\'
	// return rawQuery(R.string.query_..., "%" + DatabaseTools.escapeLike(nameFilter, '\\') + "%");

	public @NonNull Cursor listPropertyTypes() {
		return rawQuery(R.string.query_property_types);
	}
	public @NonNull Cursor listRoomTypes() {
		return rawQuery(R.string.query_room_types);
	}
	public @NonNull Cursor listProperties() {
		return rawQuery(R.string.query_properties);
	}
	public @NonNull Cursor getProperty(long propertyID) {
		return rawQuery(R.string.query_property, propertyID);
	}
	public @NonNull Cursor listRooms() {
		return rawQuery(R.string.query_rooms, null, null);
	}
	public @NonNull Cursor listRooms(long propertyID) {
		return rawQuery(R.string.query_rooms, propertyID, propertyID);
	}
	public @NonNull Cursor getRoom(long roomID) {
		return rawQuery(R.string.query_room, roomID);
	}
	public @NonNull Cursor listRelatedCategories(Long categoryID) {
		if (categoryID == null) {
			return rawQuery(R.string.query_categories_all);
		} else {
			return rawQuery(R.string.query_item_categories, categoryID);
		}
	}
	public @NonNull Cursor listItems(long parentID) {
		return rawQuery(R.string.query_items_by_item, parentID, parentID, parentID);
	}

	public long getRoomRoot(long roomID) {
		Cursor room = getRoom(roomID);
		try {
			if (!room.moveToFirst()) {
				return Room.ID_ADD;
			}
			return room.getLong(room.getColumnIndexOrThrow(Room.ROOT_ITEM));
		} finally {
			room.close();
		}
	}

	public @NonNull Cursor listItemsInRoom(long roomID) {
		long root = getRoomRoot(roomID);
		if (root != Room.ID_ADD) {
			return listItems(root);
		} else {
			// Behave like a select would, an empty result set
			return new MatrixCursor(new String[0], 0);
		}
	}

	public @NonNull Cursor listItemsInList(long listID) {
		return rawQuery(R.string.query_items_by_list, listID);
	}
	public @NonNull Cursor listItems() {
		return rawQuery(R.string.query_items);
	}
	public @NonNull Cursor listItemsForCategory(long categoryID, boolean include) {
		if (include) {
			return rawQuery(R.string.query_items_in_category, categoryID);
		} else {
			return rawQuery(R.string.query_items_by_category, categoryID);
		}
	}
	public @NonNull Cursor listItemParents(long itemID) {
		return rawQuery(R.string.query_item_parents, itemID);
	}

	public @NonNull Cursor getItem(long itemID, boolean addToRecents) {
		if (addToRecents) {
			execSQL(R.string.query_recent_add, itemID);
		}
		return rawQuery(R.string.query_item, itemID, itemID, itemID);
	}
	public @NonNull Cursor listCategories(Long parentCategoryID) {
		return rawQuery(R.string.query_categories, parentCategoryID, parentCategoryID);
	}
	public @NonNull Cursor getCategory(long itemID) {
		return rawQuery(R.string.query_category, itemID);
	}
	public long findCommonCategory(long... ids) {
		StringBuilder sb = new StringBuilder("select distinct category from Item where _id in (");
		for (int i = 0; i < ids.length; i++) {
			if (0 < i) {
				sb.append(",");
			}
			sb.append(ids[i]);
		}
		sb.append(");");

		Cursor cursor = getReadableDatabase().rawQuery(sb.toString(), null);
		try {
			if (cursor.getCount() == 1 && cursor.moveToPosition(0)) {
				return DatabaseTools.getInt(cursor, "category");
			} else {
				return Category.INTERNAL;
			}
		} finally {
			cursor.close();
		}
	}

	private void setImage(@StringRes int imageSetter, long id, byte[] imageContents, Long time) {
		if (imageContents == null) {
			// delete old image (via trigger)
			execSQL(imageSetter, null, id);
		} else {
			// create new image
			long imageID = addImage(imageContents, time);
			// use new image (old will be deleted via trigger)
			execSQL(imageSetter, imageID, id);
		}
	}

	public long addImage(byte[] imageContents, Long time) {
		long imageID;
		if (time != null) {
			imageID = rawInsert(R.string.query_image_create_with_time, imageContents, time);
		} else {
			imageID = rawInsert(R.string.query_image_create, (Object)imageContents);
		}
		return imageID;
	}

	public void deleteImage(long id) {
		execSQL(R.string.query_image_delete, id);
	}

	public long createProperty(long type, String name, String description) {
		return rawInsert(R.string.query_property_create, type, name, description);
	}
	public Long findProperty(String name) {
		return getID(R.string.query_property_find, name);
	}
	public void updateProperty(long id, long type, String name, String description) {
		execSQL(R.string.query_property_update, type, name, description, id);
	}
	public void setPropertyImage(long id, Long imageId) {
		execSQL(R.string.query_property_image_set, imageId, id);
	}

	public @NonNull Cursor getPropertyImage(long id) {
		return rawQuery(R.string.query_property_image_get, id);
	}
	public void deleteProperty(long id) {
		execSQL(R.string.query_property_delete, id);
	}

	public long createRoom(long propertyID, long type, String name, String description) {
		rawInsert(R.string.query_room_create, propertyID, type, name, description);
		return findRoom(propertyID, name); // last_insert_rowid() doesn't work with INSTEAD OF INSERT triggers on VIEWs
	}
	public Long findRoom(long propertyID, String name) {
		return getID(R.string.query_room_find, propertyID, name);
	}
	public void updateRoom(long id, long type, String name, String description) {
		execSQL(R.string.query_room_update, type, name, description, id);
	}
	public void setRoomImage(long id, Long imageId) {
		execSQL(R.string.query_room_image_set, imageId, id);
	}
	public @NonNull Cursor getRoomImage(long id) {
		return rawQuery(R.string.query_room_image_get, id);
	}
	public void deleteRoom(long id) {
		execSQL(R.string.query_room_delete, id);
	}
	public void moveRoom(long id, long propertyID) {
		execSQL(R.string.query_room_move, propertyID, id);
	}
	public void moveRooms(long propertyID, long... roomIDs) {
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

	public long createItem(long parentID, long category, String name, String description) {
		return rawInsert(R.string.query_item_create, parentID, category, name, description);
	}
	public Long findItem(long parentID, String name) {
		return getID(R.string.query_item_find, parentID, name);
	}
	public void updateItem(long id, long category, String name, String description) {
		execSQL(R.string.query_item_update, category, name, description, id);
	}
	public void setItemImage(long id, Long imageId) {
		execSQL(R.string.query_item_image_set, imageId, id);
	}
	public @NonNull Cursor getItemImage(long id) {
		return rawQuery(R.string.query_item_image_get, id);
	}
	public void deleteItem(long id) {
		execSQL(R.string.query_item_delete, id);
	}
	public void moveItem(long id, long parentID) {
		execSQL(R.string.query_item_move, parentID, id);
	}
	public void moveItems(long parentID, long... itemIDs) {
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

	public @NonNull Cursor listLists(long itemID) {
		return rawQuery(R.string.query_list_list, itemID);
	}
	public @NonNull Cursor getList(long listID) {
		return rawQuery(R.string.query_list, listID);
	}
	public long createList(String name) {
		return rawInsert(R.string.query_list_create, name);
	}
	public void updateList(long id, String name) {
		execSQL(R.string.query_list_update, name, id);
	}
	public void deleteList(long id) {
		execSQL(R.string.query_list_delete, id);
	}
	public Long findList(String name) {
		return getID(R.string.query_list_find, name);
	}
	public void addListEntry(long listID, long itemID) {
		execSQL(R.string.query_list_entry_add, listID, itemID);
	}
	public void deleteListEntry(long listID, long itemID) {
		execSQL(R.string.query_list_entry_remove, listID, itemID);
	}

	public void deleteRecentsOfItem(long itemID) {
		execSQL(R.string.query_recent_delete, itemID);
	}
	public @NonNull Cursor listRecents() {
		return rawQuery(R.string.query_recents, 1, 0.5);
	}

	public @NonNull Cursor searchSuggest(String query) {
		query = fixQuery(query);
		return rawQuery(R.string.query_search_suggest, query);
	}

	public @NonNull Cursor search(String query) {
		query = fixQuery(query);
		return rawQuery(R.string.query_search, query);
	}

	@SuppressWarnings("ConstantConditions")
	public long getSearchSize() {
		return DatabaseTools.singleLong(rawQuery(R.string.query_search_size), null);
	}

	public @NonNull Cursor stats() {
		Cursor cursor = rawQuery(R.string.query_stats);
		cursor.moveToFirst();
		return cursor;
	}

	private static String fixQuery(String query) {
		if (query.contains("*")) {
			return query;
		}
		return query.trim().replaceAll("\\s+", "*") + "*";
	}

	public void updateCategoryCache(Context context) {
		LOG.info("Updating category name cache");
		@SuppressWarnings("resource")
		SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = rawQuery(db, R.string.query_category_cache_names);
			try {
				while (cursor.moveToNext()) {
					String resourceName = cursor.getString(0);
					String displayName = String.valueOf(ResourceTools.getText(context, resourceName));
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

	public @NonNull Cursor export() {
		return rawQuery(R.string.query_export);
	}

	public @NonNull Cursor subtree(Long property, Long room, Long item) {
		if (1 < (property != null? 1 : 0) + (room != null? 1 : 0) + (item != null? 1 : 0)) {
			throw new IllegalArgumentException(
					"Specify at most one of property (" + property + "), room (" + room + "), item" + item + ").");
		}
		return rawQuery(R.string.query_subtree,
				/* full tree*/ property, room, item, /* property query */ property,
				/* full tree*/ property, room, item, /* room     query */ property, room,
				/* full tree*/ property, room, item, /* item     query */ property, room, item
		);
	}

	public void clearImages() {
		SQLiteDatabase db = getWritableDatabase();
		try {
			db.beginTransaction();
			db.execSQL("UPDATE Property SET image = NULL");
			db.execSQL("UPDATE Room SET image = NULL");
			db.execSQL("UPDATE Item SET image = NULL");
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		//db.execSQL("VACUUM"); // must be outside a transaction
	}

	public void rebuildSearch() {
		SQLiteDatabase db = getWritableDatabase();
		try {
			db.beginTransaction();
			db.execSQL("DELETE from Search");
			db.execSQL("insert into Search_Refresher(_id) select _id from Item");
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		//db.execSQL("VACUUM"); // must be outside a transaction
	}

	public void resetToTest() {
		DatabaseOpenHelper helper = getHelper();
		helper.close();
		helper.setTestMode(true);
		helper.getReadableDatabase();
		helper.close();
		helper.setTestMode(false);
	}
	public boolean isEmpty() {
		Cursor cursor = stats();
		try {
			return DatabaseTools.getOptionalInt(cursor, "properties", 0) == 0;
		} finally {
			cursor.close();
		}
	}

	/** To be called from immediate window. */
	@DebugHelper
	public Object debug(String query) {
		if (Pattern.matches("(?i)^\\s*(select|pragma)\\b.*$", query)) {
			Cursor cursor = getWritableDatabase().rawQuery(query, NO_ARGS);
			try {
				return DatabaseTools.dumpCursorToString(cursor);
			} finally {
				cursor.close();
			}
		} else {
			getWritableDatabase().execSQL(query);
			return "execSQL finished";
		}
	}
}
