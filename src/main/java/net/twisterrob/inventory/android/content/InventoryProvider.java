package net.twisterrob.inventory.android.content;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import org.slf4j.*;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;

import static android.app.SearchManager.*;

import net.twisterrob.android.utils.tools.*;
import net.twisterrob.inventory.android.App;
import net.twisterrob.inventory.android.Constants.Paths;
import net.twisterrob.inventory.android.content.InventoryContract.*;
import net.twisterrob.java.annotations.DebugHelper;
import net.twisterrob.java.utils.StringTools;

import static net.twisterrob.inventory.android.content.InventoryContract.*;

// TODO http://www.grokkingandroid.com/android-tutorial-writing-your-own-content-provider/
// TODO http://www.vogella.com/tutorials/AndroidSQLite/article.html
// TODO https://code.google.com/p/iosched/source/browse/#git%2Fandroid%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fandroid%2Fapps%2Fiosched%2Fprovider
// TODO https://raw.githubusercontent.com/android/platform_packages_providers_contactsprovider/master/src/com/android/providers/contacts/ContactsProvider2.java
public class InventoryProvider extends ContentProvider {
	private static final Logger LOG = LoggerFactory.getLogger(InventoryProvider.class);

	private static final int FIRST_DIR = 100000;
	private static final int FIRST_ITEM = 10000;
	private static final int PROPRETIES = FIRST_DIR + 1;
	private static final int PROPERTY = FIRST_ITEM + 1;
	private static final int ROOMS = FIRST_DIR + 2;
	private static final int ROOM = FIRST_ITEM + 2;
	private static final int ITEMS = FIRST_DIR + 3;
	private static final int ITEM = FIRST_ITEM + 3;
	private static final int CATEGORIES = FIRST_DIR + 4;
	private static final int CATEGORY = FIRST_ITEM + 4;
	private static final int SEARCH_ITEMS = FIRST_DIR + 5;
	private static final int SEARCH_ITEMS_SUGGEST = FIRST_DIR + 6;

	protected static final String URI_PATH_ID = "/#";
	protected static final String URI_PATH_ANY = "/*";
	private static final String FILE_COLUMN = "_data";

	private static final UriMatcher URI_MATCHER;

	static {
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(AUTHORITY, Property.DIR_URI_PATH, PROPRETIES);
		URI_MATCHER.addURI(AUTHORITY, Property.ITEM_URI_PATH + URI_PATH_ID, PROPERTY);
		URI_MATCHER.addURI(AUTHORITY, Room.DIR_URI_PATH, ROOMS);
		URI_MATCHER.addURI(AUTHORITY, Room.ITEM_URI_PATH + URI_PATH_ID, ROOM);
		URI_MATCHER.addURI(AUTHORITY, Item.DIR_URI_PATH, ITEMS);
		URI_MATCHER.addURI(AUTHORITY, Item.ITEM_URI_PATH + URI_PATH_ID, ITEM);
		URI_MATCHER.addURI(AUTHORITY, Category.DIR_URI_PATH, CATEGORIES);
		URI_MATCHER.addURI(AUTHORITY, Category.ITEM_URI_PATH + URI_PATH_ID, CATEGORY);
		URI_MATCHER.addURI(AUTHORITY, Search.URI_PATH, SEARCH_ITEMS);
		URI_MATCHER.addURI(AUTHORITY, Search.URI_PATH_SUGGEST, SEARCH_ITEMS_SUGGEST);
	}

	@Override public boolean onCreate() {
		return false;
	}

	@Override public String getType(Uri uri) {
		switch (URI_MATCHER.match(uri)) {
			case ITEMS:
				return Item.DIR_TYPE;
			case ITEM:
				return Item.ITEM_TYPE;
			case SEARCH_ITEMS:
				return Search.TYPE;
			case SEARCH_ITEMS_SUGGEST:
				return Search.TYPE_SUGGEST;
			default:
				throw new IllegalArgumentException("Unsupported URI: " + uri);
		}
	}

	@Override public Cursor query(Uri uri,
			String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		LOG.trace("query/{}({}, {}, {}, {}, {})",
				resolveMatch(URI_MATCHER.match(uri)), uri, projection, selection, selectionArgs, sortOrder);
		long start = System.nanoTime();
		try {
			return queryInternal(uri, projection, selectionArgs);
		} catch (Exception ex) {
			LOG.error("query/{}({}, {}, {}, {}, {})",
					resolveMatch(URI_MATCHER.match(uri)),
					uri, projection, selection, selectionArgs, sortOrder, ex);
		} finally {
			long end = System.nanoTime();
			LOG.debug("query/{}({}, {}, {}, {}, {}): {}ms",
					resolveMatch(URI_MATCHER.match(uri)),
					uri, projection, selection, selectionArgs, sortOrder, (end - start) / 1e6);
		}
		return null;
	}
	private Cursor queryInternal(Uri uri, String[] projection, String[] selectionArgs) throws Exception {
		switch (URI_MATCHER.match(uri)) {
			case SEARCH_ITEMS_SUGGEST: {
				// uri.getLastPathSegment().toLowerCase(Locale.getDefault());
				String query = selectionArgs[0].toLowerCase(Locale.getDefault());
				if (StringTools.isNullOrEmpty(query)) {
					return createItemSearchHelp();
				}
				return App.db().searchSuggest(query);
			}
			case SEARCH_ITEMS: {
				if (selectionArgs == null || StringTools.isNullOrEmpty(selectionArgs[0])) {
					return App.db().listItemsForCategory(
							net.twisterrob.inventory.android.content.contract.Category.INTERNAL, true);
				}
				String query = selectionArgs[0].toLowerCase(Locale.getDefault());
				return App.db().search(query);
			}

			case PROPERTY: {
				if (!Arrays.asList(projection).contains(FILE_COLUMN)) {
					throw new IllegalArgumentException("Property can only be queried as a file");
				}
				long propertyID = Property.getID(uri);
				return buildImageCursor(propertyID, getImageFullPath(App.db().getProperty(propertyID)));
			}
			case ROOM: {
				if (!Arrays.asList(projection).contains(FILE_COLUMN)) {
					throw new IllegalArgumentException("Room can only be queried as a file");
				}
				long roomID = Room.getID(uri);
				return buildImageCursor(roomID, getImageFullPath(App.db().getRoom(roomID)));
			}
			case ITEM: {
				if (!Arrays.asList(projection).contains(FILE_COLUMN)) {
					throw new IllegalArgumentException("Item can only be queried as a file");
				}
				long itemID = Item.getID(uri);
				return buildImageCursor(itemID, getImageFullPath(App.db().getItem(itemID, false)));
			}
			default:
				throw new UnsupportedOperationException("Unknown URI: " + uri);
		}
	}

	private Cursor buildImageCursor(long belongingID, String imagePath) throws IOException {
		File file = new File(imagePath);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream((int)file.length());
		IOTools.copyStream(new FileInputStream(file), bytes);
		byte[] imageContents = bytes.toByteArray();
		MatrixCursor cursor = new MatrixCursor(new String[] {BaseColumns._ID, FILE_COLUMN}, 1);
		cursor.addRow(new Object[] {belongingID, imageContents});
		return cursor;
	}

	private String getImageFullPath(Cursor belonging) {
		String imageFileName = DatabaseTools.singleResultFromColumn(belonging, CommonColumns.IMAGE);
		return Paths.getImagePath(getContext(), imageFileName);
	}

	/**
	 * Return a singular suggestion to display more text about what can be searched
	 * Tapping it would open the search activity.
	 */
	private Cursor createItemSearchHelp() {
		MatrixCursor cursor = new MatrixCursor(new String[] {BaseColumns._ID
				, SUGGEST_COLUMN_INTENT_ACTION
				, SUGGEST_COLUMN_INTENT_DATA
				, SUGGEST_COLUMN_ICON_1
				, SUGGEST_COLUMN_TEXT_1
				, SUGGEST_COLUMN_TEXT_2
		}, 1);
		cursor.addRow(new String[] {null
				, Intent.ACTION_SEARCH // Opens search activity
				, ""
				, "android.resource://android/drawable/ic_menu_search"
				, "Search Inventory Items" // Opens search activity
				, "Search for item name above." // Opens search activity
		});
		return cursor;
	}

	@Override public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException("Unknown URI: " + uri);
	}

	@Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Unknown URI: " + uri);
	}

	@Override public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Unknown URI: " + uri);
	}

	@Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		switch (URI_MATCHER.match(uri)) {
			case PROPERTY:
			case ROOM:
			case ITEM:
				return openBlobHelper(uri, mode);
			case CATEGORY:
				Cursor category = App.db().getCategory(Category.getID(uri));
				String name = DatabaseTools.singleResultFromColumn(category,
						net.twisterrob.inventory.android.content.contract.Category.TYPE_IMAGE);
				int svgID = AndroidTools.getRawResourceID(getContext(), name);
				// The following only works if the resource is uncompressed: android.aaptOptions.noCompress 'svg'
				return getContext().getResources().openRawResourceFd(svgID).getParcelFileDescriptor();
		}
		return super.openFile(uri, mode);
	}

	protected final ParcelFileDescriptor openBlobHelper(Uri uri, String mode) throws FileNotFoundException {
		Cursor c = query(uri, new String[] {FILE_COLUMN}, null, null, null);
		int count = (c != null)? c.getCount() : 0;
		if (count != 1) {
			// If there is not exactly one result, throw an appropriate
			// exception.
			if (c != null) {
				c.close();
			}
			if (count == 0) {
				throw new FileNotFoundException("No entry for " + uri);
			}
			throw new FileNotFoundException("Multiple items at " + uri);
		}

		c.moveToFirst();
		int i = c.getColumnIndex(FILE_COLUMN);
		byte[] contents = (i >= 0? c.getBlob(i) : null);
		c.close();
		if (contents == null) {
			throw new FileNotFoundException("Column " + FILE_COLUMN + " not found.");
		}

		return AndroidTools.stream(contents);
	}

	@DebugHelper
	private static String resolveMatch(int result) {
		if (result == UriMatcher.NO_MATCH) {
			return "NO_MATCH";
		}
		for (Field f : InventoryProvider.class.getDeclaredFields()) {
			try {
				if (int.class.equals(f.getType()) && f.getInt(null) == result) {
					return f.getName();
				}
			} catch (Exception ex) {
				LOG.warn("Can't resolve field for {} from {}", result, f, ex);
			}
		}
		return "NOT_FOUND";
	}
}
