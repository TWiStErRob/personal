### -- Inventory/proguard.pro -- ###

# Debugging helpers
#-dontobfuscate
#-dontoptimize
#-optimizationpasses 2

# See res/menu/search.xml and b.android.com/170471
-keep class android.support.v7.widget.SearchView { <init>(...); }

# STOPSHIP Libraries are using Android 28 non-existent methods.
# Warning: com.caverock.androidsvg.SVGAndroidRenderer: can't find referenced method 'int save(int)' in library class android.graphics.Canvas
# Warning: com.rarepebble.colorpicker.HueSatView: can't find referenced method 'int save(int)' in library class android.graphics.Canvas
# Warning: com.rarepebble.colorpicker.SliderViewBase: can't find referenced method 'int save(int)' in library class android.graphics.Canvas
-dontwarn android.graphics.Canvas

# Note: net.twisterrob.inventory.android.content.InventoryProvider calls 'Field.getType'
# Note: there were 1 classes trying to access generic signatures using reflection.
#       You should consider keeping the signature attributes
#       (using '-keepattributes Signature').
#       (http://proguard.sourceforge.net/manual/troubleshooting.html#attributes)
-dontnote net.twisterrob.inventory.android.content.InventoryProvider

# See net.twisterrob.inventory.android.activity.BackupActivity.shouldDisplay
-keepclassmembernames class net.twisterrob.inventory.android.backup.xml.ZippedXMLExporter {
	*** copyXSLT(...);
}

# Remove Logging for now
# FIXME use isLoggable in AndroidLogger and runtime control over TAGs

# slf4j/slf4j-api
-assumenosideeffects interface org.slf4j.Logger {
	public boolean is*Enabled(...);
	public void trace(...);
	public void info(...);
	public void warn(...);
	public void debug(...);
	public void error(...);
}
-assumenosideeffects class org.slf4j.LoggerFactory {
	public static org.slf4j.Logger getLogger(...);
}
# see https://sourceforge.net/p/proguard/bugs/621/
-assumenosideeffects class ** {
	final org.slf4j.Logger LOG;
	static synthetic org.slf4j.Logger access$*();
}

# android logging
-assumenosideeffects class android.util.Log {
	public static boolean isLoggable(java.lang.String, int);
	public static int v(...);
	public static int i(...);
	public static int w(...);
	public static int d(...);
	public static int e(...);
}
