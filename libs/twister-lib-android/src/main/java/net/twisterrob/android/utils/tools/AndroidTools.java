package net.twisterrob.android.utils.tools;

import java.io.*;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Math.*;

import org.slf4j.*;

import android.annotation.*;
import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.*;
import android.os.Build.*;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.preference.ListPreference;
import android.support.annotation.*;
import android.support.v4.app.Fragment;
import android.support.v4.view.*;
import android.support.v4.widget.SearchViewCompat;
import android.util.*;
import android.view.*;
import android.widget.*;

import static android.util.TypedValue.*;

import net.twisterrob.java.annotations.DebugHelper;
import net.twisterrob.java.utils.ReflectionTools;

public abstract class AndroidTools {
	private static final Logger LOG = LoggerFactory.getLogger(AndroidTools.class);

	private static final float CIRCLE_LIMIT = 359.9999f;
	private static final int INVALID_POSITION = -1;

	public static final @AnyRes int INVALID_RESOURCE_ID = 0;
	public static final String NULL = "null";
	public static final String ERROR = "error";

	private AndroidTools() {
		// static class
	}

	public static boolean hasPermission(Context context, String permission) {
		PackageManager packageManager = context.getPackageManager();
		// alternative: context.checkCallingOrSelfPermission
		int permissionResult = packageManager.checkPermission(permission, context.getPackageName());
		return permissionResult == PackageManager.PERMISSION_GRANTED;
	}

	public static int findItemPosition(Adapter adapter, long id) {
		for (int position = 0, n = adapter.getCount(); position < n; position++) {
			if (adapter.getItemId(position) == id) {
				return position;
			}
		}
		return INVALID_POSITION;
	}

	public static void selectByID(AdapterView<?> view, long id) {
		int position = findItemPosition(view.getAdapter(), id);
		if (position != INVALID_POSITION) {
			view.setSelection(position);
		}
	}

	public static float dip(Context context, float number) {
		return TypedValue.applyDimension(COMPLEX_UNIT_DIP, number, context.getResources().getDisplayMetrics());
	}
	public static int dipInt(Context context, float number) {
		return (int)dip(context, number);
	}

	public static @RawRes int getRawResourceID(Context context, String rawResourceName) {
		return getResourceID(context, "raw", rawResourceName);
	}

	public static @DrawableRes int getDrawableResourceID(Context context, String drawableResourceName) {
		return getResourceID(context, "drawable", drawableResourceName);
	}

	public static @StringRes int getStringResourceID(Context context, String stringResourceName) {
		return getResourceID(context, "string", stringResourceName);
	}

	public static CharSequence getText(Context context, String stringResourceName) {
		int id = getStringResourceID(context, stringResourceName);
		if (id == INVALID_RESOURCE_ID) {
			throw new NotFoundException(String.format(Locale.ROOT, "Resource '%s' is not a valid string in '%s'",
					stringResourceName, context.getPackageName()));
		}
		return context.getText(id);
	}

	private static @AnyRes int getResourceID(Context context, String resourceType, String resourceName) {
		int resID = context.getResources().getIdentifier(resourceName, resourceType, context.getPackageName());
		if (resID == INVALID_RESOURCE_ID) {
			LOG.warn("No {} resource found with name '{}' in package '{}'",
					resourceType, resourceName, context.getPackageName());
		}
		return resID;
	}

	@DebugHelper
	public static String toLongString(Bundle bundle) {
		return toString(bundle, "Bundle of ", "\n", "\t", "\n", "");
	}

	@DebugHelper
	public static String toShortString(Bundle bundle) {
		return toString(bundle, "(Bundle)", "#{", "", ", ", "}");
	}

	@DebugHelper
	private static String toString(Bundle bundle, String number, String start, String preItem, String postItem,
			String end) {
		if (bundle == null) {
			return NULL;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(number).append(bundle.size()).append(start);
		for (Iterator<String> it = new TreeSet<>(bundle.keySet()).iterator(); it.hasNext(); ) {
			String key = it.next();
			String value = toString(bundle.get(key));

			sb.append(preItem).append(key).append("=").append(value);
			if (it.hasNext()) {
				sb.append(postItem);
			}
		}
		sb.append(end);
		return sb.toString();
	}

	@DebugHelper
	public static String toString(Object value) {
		if (value == null) {
			return NULL;
		}
		String type = value.getClass().getName();
		String display;
		if (value instanceof Bundle) {
			display = toString((Bundle)value, " ", "#{", "", ", ", "}");
		} else if (value instanceof android.app.Fragment.SavedState) {
			return "(SavedState)" + toString(ReflectionTools.get(value, "mState"));
		} else if (value instanceof android.support.v4.app.Fragment.SavedState) {
			return "(v4.SavedState)" + toString(ReflectionTools.get(value, "mState"));
		} else {
			display = value.toString();
			if (type.length() <= display.length() && display.startsWith(type)) {
				display = display.substring(type.length()); // from @ sign or { in case of View
			}
			display = shortenPackageNames(display);
		}
		return "(" + shortenPackageNames(type) + ")" + display;
	}

	@DebugHelper
	private static String shortenPackageNames(String string) {
		string = string.replaceAll("^android\\.(?:[a-z0-9]+\\.)+(v4|v7|v13)\\.(?:[a-z0-9]+\\.)+", "$1.");
		string = string.replaceAll("^android\\.(?:[a-z0-9]+\\.)+", "");
		string = string.replaceAll("^javax?\\.(?:[a-z0-9]+\\.)+", "");
		string = string.replaceAll("^net\\.twisterrob\\.([a-z0-9.]+\\.)+", "tws.");
		return string;
	}

	@SuppressWarnings("deprecation")
	public static Camera.Size getOptimalSize(List<Camera.Size> sizes, int w, int h) {
		if (sizes == null) {
			return null;
		}

		Camera.Size optimalSize = findClosestAspect(sizes, w, h, 0.1);

		if (optimalSize == null) {
			optimalSize = findClosestAspect(sizes, w, h, Double.POSITIVE_INFINITY);
		}

		return optimalSize;
	}

	@SuppressWarnings("deprecation")
	private static Camera.Size findClosestAspect(List<Camera.Size> sizes, int width, int height, double tolerance) {
		Camera.Size optimalSize = null;

		final double targetRatio = (double)width / (double)height;
		double minDiff = Double.MAX_VALUE;
		for (Camera.Size size : sizes) {
			double ratio = (double)size.width / (double)size.height;
			if (Math.abs(ratio - targetRatio) <= tolerance && Math.abs(size.height - height) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - height);
			}
		}
		return optimalSize;
	}

	public static boolean hasCameraHardware(Context context) {
		return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
	}

	@SuppressWarnings("deprecation")
	public static int calculateRotation(Context context, Camera.CameraInfo cameraInfo) {
		WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		int rotation = windowManager.getDefaultDisplay().getRotation();
		int degrees = rotation * 90; // consider using Surface.ROTATION_ constants

		int result;
		if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (cameraInfo.orientation + degrees) % 360;
			result = (360 - result) % 360;  // compensate the mirror
		} else {  // back-facing
			result = (cameraInfo.orientation - degrees + 360) % 360;
		}
		return result;
	}

	/**
	 * Draws a thick arc between the defined angles, see {@link Canvas#drawArc} for more.
	 * This method is equivalent to
	 * <pre><code>
	 * float rMid = (rInn + rOut) / 2;
	 * paint.setStyle(Style.STROKE); // there's nothing to fill
	 * paint.setStrokeWidth(rOut - rInn); // thickness
	 * canvas.drawArc(new RectF(cx - rMid, cy - rMid, cx + rMid, cy + rMid), startAngle, sweepAngle, false, paint);
	 * </code></pre>
	 * but supports different fill and stroke paints.
	 *
	 * @param cx horizontal middle point of the oval
	 * @param cy vertical middle point of the oval
	 * @param rInn inner radius of the arc segment
	 * @param rOut outer radius of the arc segment
	 * @param startAngle see {@link Canvas#drawArc}
	 * @param sweepAngle see {@link Canvas#drawArc}, capped at &plusmn;360
	 * @param fill filling paint, can be <code>null</code>
	 * @param stroke stroke paint, can be <code>null</code>
	 * @see Canvas#drawArc
	 */
	public static void drawArcSegment(Canvas canvas, float cx, float cy, float rInn, float rOut, float startAngle,
			float sweepAngle, Paint fill, Paint stroke) {
		boolean circle = false;
		if (sweepAngle > CIRCLE_LIMIT) {
			sweepAngle = CIRCLE_LIMIT;
			circle = true;
		}
		if (sweepAngle < -CIRCLE_LIMIT) {
			sweepAngle = -CIRCLE_LIMIT;
			circle = true;
		}

		RectF outerRect = new RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut);
		RectF innerRect = new RectF(cx - rInn, cy - rInn, cx + rInn, cy + rInn);

		Path segmentPath = new Path();
		double start = toRadians(startAngle);
		double end = toRadians(startAngle + sweepAngle);
		if (circle) {
			segmentPath.addArc(outerRect, startAngle, sweepAngle);
			segmentPath.addArc(innerRect, startAngle + sweepAngle, -sweepAngle);
		} else {
			segmentPath.moveTo((float)(cx + rInn * cos(start)), (float)(cy + rInn * sin(start)));
			segmentPath.lineTo((float)(cx + rOut * cos(start)), (float)(cy + rOut * sin(start)));
			segmentPath.arcTo(outerRect, startAngle, sweepAngle);
			// Path currently at (float)(cx + rOut * cos(end)), (float)(cy + rOut * sin(end))
			segmentPath.lineTo((float)(cx + rInn * cos(end)), (float)(cy + rInn * sin(end)));
			segmentPath.arcTo(innerRect, startAngle + sweepAngle, -sweepAngle); // drawn backwards
		}
		if (fill != null) {
			canvas.drawPath(segmentPath, fill);
		}
		if (stroke != null) {
			canvas.drawPath(segmentPath, stroke);
		}
	}

	/**
	 * Draws a thick arc between the defined angles, see {@link Canvas#drawArc} for more.
	 * This method is equivalent to
	 * <pre><code>
	 * float rMid = (rInn + rOut) / 2;
	 * paint.setStyle(Style.STROKE); // there's nothing to fill
	 * paint.setStrokeWidth(rOut - rInn); // thickness
	 * canvas.drawArc(new RectF(cx - rMid, cy - rMid, cx + rMid, cy + rMid), startAngle, sweepAngle, false, paint);
	 * </code></pre>
	 * but supports different fill and stroke paints.
	 *
	 * @param cx horizontal middle point of the oval
	 * @param cy vertical middle point of the oval
	 * @param rInn inner radius of the arc segment
	 * @param rOut outer radius of the arc segment
	 * @param startAngle see {@link Canvas#drawArc}
	 * @param sweepAngle see {@link Canvas#drawArc}, capped at &plusmn;360
	 * @param fill filling paint, can be <code>null</code>
	 * @param strokeInner stroke paint for inner ring segment, can be <code>null</code>
	 * @param strokeOuter stroke paint for outer ring segment, can be <code>null</code>
	 * @param strokeSides stroke paint for lines connecting the ends of the ring segments, can be <code>null</code>
	 * @see Canvas#drawArc
	 */
	public static void drawArcSegment(Canvas canvas, float cx, float cy, float rInn, float rOut, float startAngle,
			float sweepAngle, Paint fill, Paint strokeInner, Paint strokeOuter, Paint strokeSides) {
		boolean circle = false;
		if (sweepAngle > CIRCLE_LIMIT) {
			sweepAngle = CIRCLE_LIMIT;
			circle = true;
		}
		if (sweepAngle < -CIRCLE_LIMIT) {
			sweepAngle = -CIRCLE_LIMIT;
			circle = true;
		}

		RectF outerRect = new RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut);
		RectF innerRect = new RectF(cx - rInn, cy - rInn, cx + rInn, cy + rInn);

		if (fill != null || strokeSides != null) { // to prevent calculating this lot of floats
			double start = toRadians(startAngle);
			double end = toRadians(startAngle + sweepAngle);
			float innerStartX = (float)(cx + rInn * cos(start));
			float innerStartY = (float)(cy + rInn * sin(start));
			float innerEndX = (float)(cx + rInn * cos(end));
			float innerEndY = (float)(cy + rInn * sin(end));
			float outerStartX = (float)(cx + rOut * cos(start));
			float outerStartY = (float)(cy + rOut * sin(start));
			float outerEndX = (float)(cx + rOut * cos(end));
			float outerEndY = (float)(cy + rOut * sin(end));
			if (fill != null) {
				Path segmentPath = new Path();
				segmentPath.moveTo(innerStartX, innerStartY);
				segmentPath.lineTo(outerStartX, outerStartY);
				segmentPath.arcTo(outerRect, startAngle, sweepAngle);
				// Path currently at outerEndX,outerEndY
				segmentPath.lineTo(innerEndX, innerEndY);
				segmentPath.arcTo(innerRect, startAngle + sweepAngle, -sweepAngle); // drawn backwards
				canvas.drawPath(segmentPath, fill);
			}
			if (!circle && strokeSides != null) {
				canvas.drawLine(innerStartX, innerStartY, outerStartX, outerStartY, strokeSides);
				canvas.drawLine(innerEndX, innerEndY, outerEndX, outerEndY, strokeSides);
			}
		}
		if (strokeInner != null) {
			canvas.drawArc(innerRect, startAngle, sweepAngle, false, strokeInner);
		}
		if (strokeOuter != null) {
			canvas.drawArc(outerRect, startAngle, sweepAngle, false, strokeOuter);
		}
	}

	public static void drawTextOnArc(Canvas canvas, String label, float cx, float cy, float rInn, float rOut,
			float startAngle, float sweepAngle, Paint textPaint) {
		Path midway = new Path();
		float r = (rInn + rOut) / 2;
		RectF segment = new RectF(cx - r, cy - r, cx + r, cy + r);
		midway.addArc(segment, startAngle, sweepAngle);
		canvas.drawTextOnPath(label, midway, 0, 0, textPaint);
	}

	/**
	 * @see AsyncTask#execute(Object[])
	 * @see <a href="http://commonsware.com/blog/2012/04/20/asynctask-threading-regression-confirmed.html">AsyncTask Threading Regression Confirmed</a>
	 * @see <a href="https://groups.google.com/forum/#!topic/android-developers/8M0RTFfO7-M">AsyncTask in Android 4.0</a>
	 * @see <a href="http://www.jayway.com/2012/11/28/is-androids-asynctask-executing-tasks-serially-or-concurrently/">AsyncTask ordering</a>
	 */
	@SafeVarargs
	@TargetApi(VERSION_CODES.HONEYCOMB)
	public static <Params> void executeParallel(AsyncTask<Params, ?, ?> as, Params... params) {
		if (VERSION.SDK_INT < VERSION_CODES.DONUT) {
			throw new IllegalStateException("Cannot execute AsyncTask in parallel before DONUT");
		} else if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB) {
			as.execute(params); // default is pooling, cannot be explicit
		} else {
			as.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params); // default is serial, explicit pooling
		}
	}

	/**
	 * @see AsyncTask#execute(Object[])
	 * @see #executeParallel(AsyncTask, Object[])
	 */
	@SafeVarargs
	@TargetApi(VERSION_CODES.HONEYCOMB)
	public static <Params> void executeSerial(AsyncTask<Params, ?, ?> as, Params... params) {
		if (VERSION.SDK_INT < VERSION_CODES.DONUT) {
			as.execute(params); // default is serial
		} else if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB) {
			throw new IllegalStateException("Cannot execute AsyncTask in serial between DONUT and HONEYCOMB");
		} else {
			as.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, params); // default is serial, explicit serial
		}
	}

	public static int findIndexInResourceArray(Context context, @ArrayRes int arrayResourceID, String value) {
		ListPreference pref = new ListPreference(context);
		pref.setEntryValues(arrayResourceID);
		return pref.findIndexOfValue(value);
	}

	public static Intent getApplicationInfoScreen(Context context) {
		return getApplicationInfoScreen(context, context.getPackageName());
	}

	public static Intent getApplicationInfoScreen(Context context, String packageName) {
		// The specific app page
		Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.setData(Uri.parse("package:" + packageName));
		if (context.getPackageManager().resolveActivity(intent, 0) == null) {
			// The generic apps page
			intent = new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
		}
		return intent;
	}

	/**
	 * Tries to find an instance of {@code eventsClass} among the {@code fragment}'s parents,
	 * that is {@link Fragment#getParentFragment()} and {@link Fragment#getActivity()}.
	 * Closest one wins, activity being the farthest.
	 *
	 * @throws IllegalArgumentException if callback is null or is not the right {@code eventsClass}.
	 */
	public static @NonNull <T> T findAttachedListener(@NonNull Fragment fragment, @NonNull Class<T> eventsClass)
			throws IllegalArgumentException {
		T listener = null;
		List<Fragment> parents = getParents(fragment);
		Iterator<Fragment> iterator = parents.iterator();
		while (listener == null && iterator.hasNext()) {
			listener = tryGetAttachedListener(iterator.next(), eventsClass);
		}
		if (listener == null) {
			listener = tryGetAttachedListener(fragment.getActivity(), eventsClass);
		}
		if (listener != null) {
			return listener;
		} else {
			throw new IllegalArgumentException("One of " + fragment + "'s parents (" + parents + ") or its activity ("
					+ fragment.getActivity() + ") must implement " + eventsClass);
		}
	}

	public static @NonNull List<Fragment> getParents(@NonNull Fragment fragment) {
		List<Fragment> parents = new LinkedList<>();
		Fragment parent = fragment.getParentFragment();
		while (parent != null) {
			parents.add(parent);
			parent = parent.getParentFragment();
		}
		return parents;
	}

	public static @Nullable <T> T tryGetAttachedListener(Object callback, @NonNull Class<T> eventsClass) {
		if (eventsClass.isInstance(callback)) {
			return eventsClass.cast(callback);
		} else {
			return null;
		}
	}

	/** @throws IllegalArgumentException if callback is null or is not the right {@code eventsClass}. */
	public static @NonNull <T> T getAttachedListener(Object callback, @NonNull Class<T> eventsClass)
			throws IllegalArgumentException {
		T listener = tryGetAttachedListener(callback, eventsClass);
		if (listener != null) {
			return listener;
		} else {
			throw new IllegalArgumentException("Parent " + callback + " must implement " + eventsClass);
		}
	}

	public static View prepareSearch(Activity activity, Menu menu, int searchItemID) {
		SearchManager searchManager = (SearchManager)activity.getSystemService(Context.SEARCH_SERVICE);
		MenuItem item = menu.findItem(searchItemID);
		if (item == null) {
			return null;
		}
		View view = MenuItemCompat.getActionView(item);
		if (view instanceof android.support.v7.widget.SearchView) {
			android.support.v7.widget.SearchView searchView = (android.support.v7.widget.SearchView)view;
			SearchableInfo info = searchManager.getSearchableInfo(activity.getComponentName());
			if (info == null) {
				throw new NullPointerException("No searchable info for " + activity.getComponentName()
						+ "\nDid you define <meta-data android:name=\"android.app.default_searchable\" android:value=\".SearchActivity\" />"
						+ "\neither on application level or inside the activity in AndroidManifest.xml?"
						+ "\nAlso make sure that in the merged manifest the class name resolves correctly (package).");
			}
			searchView.setSearchableInfo(info);
			return searchView;
		} else {
			SearchViewCompat.setSearchableInfo(view, activity.getComponentName());
			return view;
		}
	}

	@SuppressLint("LogConditional") // Should only be used in debug code
	@DebugHelper
	public static void screenshot(View view) {
		Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
		view.draw(new Canvas(bitmap));
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
		try {
			File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			if (!storageDir.mkdirs() || !storageDir.isDirectory()) {
				throw new IOException("Not a directory: " + storageDir);
			}
			File file = File.createTempFile(timeStamp, ".png", storageDir);
			@SuppressWarnings("resource")
			OutputStream stream = new FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
			stream.close();
			Log.d("SCREENSHOT", "adb pull " + file);
		} catch (IOException e) {
			Log.e("SCREENSHOT", "Cannot save screenshot of " + view, e);
		}
	}

	public static void setEnabled(MenuItem item, boolean enabled) {
		item.setEnabled(enabled);
		Drawable icon = item.getIcon();
		if (icon != null) {
			icon = icon.mutate();
			icon.setAlpha(enabled? 0xFF : 0x80);
			item.setIcon(icon);
		}
	}

	/** Call from {@link android.app.Activity#onMenuOpened(int, Menu)}. */
	public static void showActionBarOverflowIcons(int featureId, Menu menu, boolean show) {
		// http://stackoverflow.com/questions/18374183/how-to-show-icons-in-overflow-menu-in-actionbar
		if ((featureId == WindowCompat.FEATURE_ACTION_BAR || featureId == WindowCompat.FEATURE_ACTION_BAR_OVERLAY)
				&& menu != null && "MenuBuilder".equals(menu.getClass().getSimpleName())) {
			try {
				Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
				m.setAccessible(true);
				m.invoke(menu, show);
			} catch (NoSuchMethodException e) {
				LOG.error("ActionBar overflow icons hack failed", e);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@TargetApi(VERSION_CODES.HONEYCOMB)
	public static void setItemChecked(AdapterView parent, int position, boolean value) {
		if (parent instanceof ListView) {
			((ListView)parent).setItemChecked(position, value);
		} else if (parent instanceof AbsListView) {
			if (VERSION_CODES.HONEYCOMB <= VERSION.SDK_INT) {
				((AbsListView)parent).setItemChecked(position, value);
			}
		} else {
			LOG.warn("Cannot setItemChecked({}) #{} on {}", value, position, parent);
		}
	}

	public static View findClosest(View view, @IdRes int viewId) {
		if (view.getId() == viewId) {
			return view;
		}
		ViewParent parent = view.getParent();
		if (parent instanceof ViewGroup) {
			ViewGroup group = (ViewGroup)parent;
			for (int index = 0; index < group.getChildCount(); index++) {
				View child = group.getChildAt(index);
				if (child.getId() == viewId) {
					return child;
				}
			}
			return findClosest(group, viewId);
		}
		return null;
	}

	/** @see ComponentCallbacks2 */
	@DebugHelper
	public static String toTrimMemoryString(int level) {
		switch (level) {
			case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
				return "TRIM_MEMORY_COMPLETE";
			case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
				return "TRIM_MEMORY_MODERATE";
			case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
				return "TRIM_MEMORY_BACKGROUND";
			case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
				return "TRIM_MEMORY_UI_HIDDEN";
			case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
				return "TRIM_MEMORY_RUNNING_CRITICAL";
			case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
				return "TRIM_MEMORY_RUNNING_LOW";
			case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
				return "TRIM_MEMORY_RUNNING_MODERATE";
		}
		return "trimMemoryLevel=" + level;
	}

	public static String toColorString(int color) {
		return String.format(Locale.ROOT, "#%02X%02X%02X%02X",
				Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
	}

	@DebugHelper
	public static String toFeatureString(int featureId) {
		switch (featureId) {
			case Window.FEATURE_OPTIONS_PANEL:
				return "FEATURE_OPTIONS_PANEL";
			case Window.FEATURE_NO_TITLE:
				return "FEATURE_NO_TITLE";
			case Window.FEATURE_PROGRESS:
				return "FEATURE_PROGRESS";
			case Window.FEATURE_LEFT_ICON:
				return "FEATURE_LEFT_ICON";
			case Window.FEATURE_RIGHT_ICON:
				return "FEATURE_RIGHT_ICON";
			case Window.FEATURE_INDETERMINATE_PROGRESS:
				return "FEATURE_INDETERMINATE_PROGRESS";
			case Window.FEATURE_CONTEXT_MENU:
				return "FEATURE_CONTEXT_MENU";
			case Window.FEATURE_CUSTOM_TITLE:
				return "FEATURE_CUSTOM_TITLE";
			case Window.FEATURE_ACTION_BAR:
				return "FEATURE_ACTION_BAR";
			case Window.FEATURE_ACTION_BAR_OVERLAY:
				return "FEATURE_ACTION_BAR_OVERLAY";
			case Window.FEATURE_ACTION_MODE_OVERLAY:
				return "FEATURE_ACTION_MODE_OVERLAY";
			case Window.FEATURE_SWIPE_TO_DISMISS:
				return "FEATURE_SWIPE_TO_DISMISS";
			case Window.FEATURE_CONTENT_TRANSITIONS:
				return "FEATURE_CONTENT_TRANSITIONS";
			case Window.FEATURE_ACTIVITY_TRANSITIONS:
				return "FEATURE_ACTIVITY_TRANSITIONS";
		}
		return "featureId=" + featureId;
	}

	/** @see PackageManager#getActivityInfo(ComponentName, int) */
	public static ActivityInfo getActivityInfo(Activity activity, int flags) {
		try {
			return activity.getPackageManager().getActivityInfo(activity.getComponentName(), flags);
		} catch (NameNotFoundException e) {
			LOG.warn("Activity doesn't exists, but has an instance? {}", activity, e);
			throw new RuntimeException(e);
		}
	}
	public static ParcelFileDescriptor stream(final byte[] contents) throws FileNotFoundException {
		ParcelFileDescriptor[] pipe;
		try {
			pipe = ParcelFileDescriptor.createPipe();
		} catch (IOException e) {
			throw new FileNotFoundException(e.toString());
		}
		final ParcelFileDescriptor readEnd = pipe[0];
		final ParcelFileDescriptor writeEnd = pipe[1];

		executeParallel(new AsyncTask<Object, Object, Object>() {
			@Override
			protected Object doInBackground(Object... params) {
				InputStream in = new ByteArrayInputStream(contents);
				OutputStream out = new AutoCloseOutputStream(writeEnd);
				try {
					IOTools.copyStream(in, out);
				} catch (IOException e) {
					IOTools.ignorantCloseWithError(writeEnd, e.toString());
				}
				try {
					writeEnd.close();
				} catch (IOException e) {
					LOG.warn("Failure closing pipe", e);
				}
				return null;
			}
		});

		return readEnd;
	}

	public interface PopupCallbacks<T> {
		void finished(T value);
	}
	public static AlertDialog.Builder prompt(Context context, final PopupCallbacks<String> callbacks) {
		final EditText input = new EditText(context);
		return new AlertDialog.Builder(context)
				.setView(input)
				.setPositiveButton(android.R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String value = input.getText().toString();
						callbacks.finished(value);
					}
				})
				.setNegativeButton(android.R.string.cancel, new OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						callbacks.finished(null);
					}
				});
	}
	public static AlertDialog.Builder confirm(Context context, final PopupCallbacks<Boolean> callbacks) {
		return new AlertDialog.Builder(context)
				.setPositiveButton(android.R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						callbacks.finished(true);
					}
				})
				.setNegativeButton(android.R.string.cancel, new OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						callbacks.finished(false);
					}
				});
	}
}
