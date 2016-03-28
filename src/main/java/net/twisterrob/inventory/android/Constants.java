package net.twisterrob.inventory.android;

import java.io.*;
import java.util.*;

import android.content.Context;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;

import com.bumptech.glide.*;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.engine.cache.*;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.stream.StreamModelLoader;
import com.bumptech.glide.load.resource.bitmap.ImageVideoBitmapDecoder;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gif.GifResourceDecoder;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapperResourceDecoder;
import com.bumptech.glide.load.resource.transcode.GifBitmapWrapperDrawableTranscoder;
import com.bumptech.glide.module.GlideModule;
import com.bumptech.glide.signature.StringSignature;

import net.twisterrob.android.content.glide.*;
import net.twisterrob.android.content.glide.LoggingListener.ResourceFormatter;
import net.twisterrob.android.utils.tools.IOTools;
import net.twisterrob.inventory.android.utils.PictureHelper;

public interface Constants {
	/** Turn off parts of the app permanently during build time, never change it to <code>true</code>. */
	boolean DISABLE = false;

	class Paths {
		/** Warning: this is used inlined in paths_share.xml because path doesn't support string resources */
		private static final String PUBLIC_SHARE_FOLDER_NAME = "share";
		public static final String BACKUP_DATA_FILENAME = "data.xml";

		public static @NonNull File getExportFile() throws IOException {
			Calendar now = Calendar.getInstance();
			String fileName = String.format(Locale.ROOT, "Inventory_%tF_%<tH-%<tM-%<tS.zip", now);
			File exportFolder = getPhoneHome();
			IOTools.ensure(exportFolder);
			return new File(exportFolder, fileName);
		}
		public static @NonNull File getPhoneHome() {
			return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		}

		public static File getShareFile(Context context, String ext) throws IOException {
			return getTemporaryCacheFile(context, PUBLIC_SHARE_FOLDER_NAME, "share_", "." + ext);
		}
		public static File getShareImage(Context context) throws IOException {
			return getShareFile(context, "jpg");
		}
		public static File getTempImage(Context context) throws IOException {
			return getTemporaryCacheFile(context, "temp", "temp_", ".jpg");
		}
		private static File getTemporaryCacheFile(Context context, String folderName, String prefix, String suffix)
				throws IOException {
			File folder = new File(context.getCacheDir(), folderName);
			IOTools.ensure(folder);
			File file = new File(folder, prefix + 0 + suffix);
			// TODO figure out an alternative to deleteOnExit, before that:
			//noinspection ResultOfMethodCallIgnored, use the same image file over and over again
			file.delete();
			//File file = File.createTempFile(prefix, suffix, folder);
			file.deleteOnExit();
			return file;
		}
	}

	class Pic {
		public static <T> DrawableRequestBuilder<T> baseRequest(Class<T> clazz) {
			ModelLoader<T, InputStream> loader = Glide.buildModelLoader(clazz, InputStream.class, App.getAppContext());
			DrawableRequestBuilder<T> builder = Glide
					.with(App.getAppContext())
					.using((StreamModelLoader<T>)loader)
					.from(clazz)
					.animate(android.R.anim.fade_in)
					.error(R.drawable.image_error);
			if (DISABLE && BuildConfig.DEBUG) {
				builder = builder
						.diskCacheStrategy(DiskCacheStrategy.NONE)
						.skipMemoryCache(true)
				;
			}
			return builder;
		}

		private static final DrawableRequestBuilder<Integer> SVG_REQUEST = baseRequest(Integer.class)
				.signature(new StringSignature(BuildConfig.VERSION_NAME))
				.dontAnimate()
				.priority(Priority.HIGH)
				.decoder(getSvgDecoder())
				.transcoder(getSvgTranscoder());

		private static final DrawableRequestBuilder<Uri> IMAGE_REQUEST = baseRequest(Uri.class)
				.animate(android.R.anim.fade_in)
				.priority(Priority.NORMAL);

		public static DrawableRequestBuilder<Integer> svg() {
			DrawableRequestBuilder<Integer> clone = SVG_REQUEST.clone();
			if (DISABLE && BuildConfig.DEBUG) {
				LoggingListener.ResourceFormatter formatter = new ResourceFormatter(App.getAppContext());
				clone.listener(new LoggingListener<Integer, GlideDrawable>("SVG", formatter));
			}
			return clone;
		}

		public static DrawableRequestBuilder<Uri> jpg() {
			DrawableRequestBuilder<Uri> clone = IMAGE_REQUEST.clone();
			if (DISABLE && BuildConfig.DEBUG) {
				clone.listener(new LoggingListener<Uri, GlideDrawable>("image"));
			}
			return clone;
		}

		private static GifBitmapWrapperResourceDecoder getSvgDecoder() {
			Context context = App.getAppContext();
			BitmapPool pool = Glide.get(context).getBitmapPool();
			return new GifBitmapWrapperResourceDecoder(
					new ImageVideoBitmapDecoder(
							new SvgBitmapDecoder(pool),
							null /*fileDescriptorDecoder*/
					),
					new GifResourceDecoder(context, pool),
					pool
			);
		}
		@NonNull private static GifBitmapWrapperDrawableTranscoder getSvgTranscoder() {
			return new GifBitmapWrapperDrawableTranscoder(
					new FilteredGlideBitmapDrawableTranscoder(
							App.getAppContext(),
							new ColorMatrixColorFilter(PictureHelper.tintMatrix(App.getAppContext()))
					)
			);
		}

		public static class GlideSetup implements GlideModule {
			private static File CACHE_DIR = null;
			@Override public void applyOptions(final Context context, GlideBuilder builder) {
				if (BuildConfig.DEBUG) {
					builder.setDiskCache(new DiskCache.Factory() {
						@Override public DiskCache build() {
							CACHE_DIR = new File(context.getExternalCacheDir(), "image_manager_disk_cache");
							return DiskLruCacheWrapper.get(CACHE_DIR, 250 * 1024 * 1024);
						}
					});
				}
			}
			@Override public void registerComponents(Context context, Glide glide) {
			}
			public static File getCacheDir(Context context) {
				return CACHE_DIR != null? CACHE_DIR : Glide.getPhotoCacheDir(context);
			}
		}
	}
}
