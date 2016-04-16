package net.twisterrob.android.activity;

import java.io.*;

import org.slf4j.*;

import android.Manifest;
import android.animation.*;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.support.annotation.CheckResult;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.bumptech.glide.*;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.*;

import net.twisterrob.android.R;
import net.twisterrob.android.content.glide.WrapViewTarget;
import net.twisterrob.android.utils.concurrent.Callback;
import net.twisterrob.android.utils.tools.*;
import net.twisterrob.android.view.*;
import net.twisterrob.android.view.CameraPreview.*;
import net.twisterrob.android.view.SelectionView.SelectionStatus;
import net.twisterrob.java.io.IOTools;

public class CaptureImage extends Activity {
	private static final Logger LOG = LoggerFactory.getLogger(CaptureImage.class);
	public static final String EXTRA_OUTPUT = MediaStore.EXTRA_OUTPUT;
	public static final String EXTRA_MAXSIZE = MediaStore.EXTRA_SIZE_LIMIT;
	public static final String EXTRA_QUALITY = "quality";
	public static final String EXTRA_FORMAT = "format";
	public static final String EXTRA_ASPECT = "keepAspect";
	public static final String EXTRA_SQUARE = "isSquare";
	public static final String EXTRA_FLASH = "flash";
	public static final String EXTRA_PICK = "pickImage";
	private static final String PREF_FLASH = EXTRA_FLASH;
	private static final float DEFAULT_MARGIN = 0.10f;
	private static final boolean DEFAULT_FLASH = false;
	private static final int EXTRA_MAXSIZE_NO_MAX = 0;

	private CameraPreview mPreview;
	private SelectionView mSelection;
	private File mTargetFile;
	private File mSavedFile;
	private ImageView mImage;
	private View controls;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		String output = getIntent().getStringExtra(EXTRA_OUTPUT);
		if (output == null) {
			setResult(RESULT_CANCELED);
			finish();
			return;
		} else {
			mTargetFile = new File(output);
		}

		setContentView(R.layout.activity_camera);
		controls = findViewById(R.id.controls);
		final View cameraControls = findViewById(R.id.camera_controls);
		final ImageButton btnPick = (ImageButton)controls.findViewById(R.id.btn_pick);
		final ImageButton btnCapture = (ImageButton)controls.findViewById(R.id.btn_capture);
		final ImageButton btnCrop = (ImageButton)controls.findViewById(R.id.btn_crop);
		final ToggleButton btnFlash = (ToggleButton)cameraControls.findViewById(R.id.btn_flash);
		mPreview = (CameraPreview)findViewById(R.id.preview);
		mImage = (ImageView)findViewById(R.id.image);
		mSelection = (SelectionView)findViewById(R.id.selection);

		mPreview.setListener(new CameraPreviewListener() {
			@Override public void onCreate(CameraPreview preview) {
				btnFlash.setChecked(getInitialFlashEnabled()); // calls setOnCheckedChangeListener
			}
			@Override public void onResume(CameraPreview preview) {
				cameraControls.setVisibility(View.VISIBLE);
			}
			@Override public void onShutter(CameraPreview preview) {
				final View flashView = mSelection;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
					ObjectAnimator whiteFlashIn = ObjectAnimator.ofObject(flashView,
							"backgroundColor", new ArgbEvaluator(), 0x00FFFFFF, 0xAAFFFFFF);
					ObjectAnimator whiteFlashOut = ObjectAnimator.ofObject(flashView,
							"backgroundColor", new ArgbEvaluator(), 0xAAFFFFFF, 0x00000000);
					whiteFlashIn.setDuration(200);
					whiteFlashOut.setDuration(300);
					AnimatorSet whiteFlash = new AnimatorSet();
					whiteFlash.playSequentially(whiteFlashIn, whiteFlashOut);
					whiteFlash.addListener(new AnimatorListenerAdapter() {
						@Override public void onAnimationEnd(Animator animation) {
							flashView.setBackgroundDrawable(null);
						}
					});
					whiteFlash.start();
				}
			}
			@Override public void onPause(CameraPreview preview) {
				cameraControls.setVisibility(View.INVISIBLE);
			}
			@Override public void onDestroy(CameraPreview preview) {
				// no op
			}
		});

		mSelection.setKeepAspectRatio(getIntent().getBooleanExtra(EXTRA_ASPECT, false));
		if (getIntent().getBooleanExtra(EXTRA_SQUARE, false)) {
			mSelection.setSelectionMarginSquare(DEFAULT_MARGIN);
		} else {
			mSelection.setSelectionMargin(DEFAULT_MARGIN);
		}

		btnFlash.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mPreview.setFlash(isChecked);
				getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_FLASH, isChecked).apply();
			}
		});

		btnCapture.setOnClickListener(new CaptureClickListener());
		btnPick.setOnClickListener(new PickClickListener());
		btnCrop.setOnClickListener(new CropClickListener());

		if (getIntent().getBooleanExtra(EXTRA_PICK, false) && savedInstanceState == null) {
			btnPick.performClick();
		}
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != Activity.RESULT_OK) {
			mSelection.setSelectionStatus(SelectionStatus.BLURRY);
			enableControls();
			return;
		}
		Uri fallback = Uri.fromFile(mTargetFile);
		Uri result = ImageTools.getPictureUriFromResult(requestCode, resultCode, data, fallback);
		if (!fallback.equals(result)) {
			try {
				InputStream stream = getContentResolver().openInputStream(result);
				IOTools.copyStream(stream, new FileOutputStream(mTargetFile));
			} catch (IOException ex) {
				LOG.error("Cannot grab data from {} into {}", result, mTargetFile, ex);
			}
		}
		mSavedFile = mTargetFile;
		mSelection.setSelectionStatus(SelectionStatus.FOCUSED);
		prepareCrop();
		enableControls();
	}

	private void prepareCrop() {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			mImage.post(new Runnable() {
				@Override public void run() {
					prepareCrop();
				}
			});
			return;
		}
		LOG.trace("Loading taken image to crop: {}", mSavedFile);
		// Use a special target that will adjust the size of the ImageView to wrap the image (adjustViewBounds).
		// The selection view's size will match this hence the user can only select part of the image. 
		ThumbWrapViewTarget<Bitmap> target = new ThumbWrapViewTarget<>(new BitmapImageViewTarget(mImage) {
			@Override public void setDrawable(Drawable drawable) {
				if (drawable instanceof TransitionDrawable) {
					// TODEL see https://github.com/bumptech/glide/issues/943
					((TransitionDrawable)drawable).setCrossFadeEnabled(false);
				}
				super.setDrawable(drawable);
			}
		});

		BitmapRequestBuilder<File, Bitmap> image = Glide
				.with(this)
				.load(mSavedFile)
				.asBitmap() // no matter the format, just a single frame of bitmap
				.diskCacheStrategy(DiskCacheStrategy.NONE) // no need to cache, it's on disk already
				.skipMemoryCache(true) // won't ever be loaded again, or if it is, probably contains different bytes
				//.placeholder(new ColorDrawable(Color.BLACK)) // immediately hide the preview to prevent weird jump
				.fitCenter() // make sure full image is visible
				.listener(target) // for the target to know if it's the thumbnail or not, also relies on skipMemoryCache
				;
		image
				.format(DecodeFormat.PREFER_ARGB_8888) // don't lose quality (may be disabled to gain memory for crop)
				// need the special target/listener
				.thumbnail(image
						.clone() // inherit everything (including listener), but load lower quality
						.format(DecodeFormat.PREFER_RGB_565)
						.sizeMultiplier(0.1f)
						.animate(android.R.anim.fade_in) // fade thumbnail in (=crossFade from background)
				)
				.crossFade(150) // fade from thumb to image
				.into(target)
		;
	}

	private boolean getInitialFlashEnabled() {
		boolean flash;
		if (getIntent().hasExtra(EXTRA_FLASH)) {
			flash = getIntent().getBooleanExtra(EXTRA_FLASH, DEFAULT_FLASH);
		} else {
			flash = getPreferences(MODE_PRIVATE).getBoolean(PREF_FLASH, DEFAULT_FLASH);
		}
		return flash;
	}

	protected void doSave(byte... data) {
		mSavedFile = save(mTargetFile, data);
	}
	protected void doCrop() {
		mSavedFile = crop(mSavedFile);
	}
	protected void doRestartPreview() {
		LOG.trace("Restarting preview");
		mPreview.setVisibility(View.VISIBLE);
		mSavedFile = null;
		mSelection.setSelectionStatus(SelectionStatus.NORMAL);
		mPreview.cancelTakePicture();
		Glide.clear(mImage);
		mImage.setImageDrawable(null); // remove Glide placeholder for the view to be transparent
	}
	protected void doReturn() {
		if (mSavedFile != null) {
			Intent result = new Intent();
			result.setDataAndType(Uri.fromFile(mSavedFile), "image/jpeg");
			setResult(RESULT_OK, result);
		} else {
			setResult(RESULT_CANCELED);
		}
		finish();
	}

	protected @CheckResult boolean take(final Callback<byte[]> jpegCallback) {
		LOG.trace("Initiate taking picture {}", mPreview.isRunning());
		if (!mPreview.isRunning()) {
			return false;
		}
		mSelection.setSelectionStatus(SelectionStatus.FOCUSING);
		mPreview.takePicture(new CameraPictureListener() {
			@Override public boolean onFocus(final boolean success) {
				LOG.trace("Auto-focus result: {}", success);
				//noinspection ResourceType post should be safe to call from background
				mSelection.post(new Runnable() {
					public void run() {
						mSelection.setSelectionStatus(success? SelectionStatus.FOCUSED : SelectionStatus.BLURRY);
					}
				});
				return true; // take the picture even if not in focus
			}
			@Override public void onTaken(byte... image) {
				jpegCallback.call(image);
			}
		}, true);
		return true;
	}

	private static File save(File file, byte... data) {
		if (data == null) {
			return null;
		}
		LOG.trace("Saving {} bytes to {}", data.length, file);
		OutputStream out = null;
		try {
			//noinspection resource cannot use try-with-resources at this API level
			out = new FileOutputStream(file);
			out.write(data);
			out.flush();
			LOG.info("Raw image ({} bytes) saved at {}", data.length, file);
		} catch (FileNotFoundException ex) {
			LOG.error("Cannot find file {}", file, ex);
			file = null;
		} catch (IOException ex) {
			LOG.error("Cannot write file {}", file, ex);
			file = null;
		} finally {
			IOTools.ignorantClose(out);
		}
		return file;
	}

	private File crop(File file) {
		try {
			RectF sel = getPictureRect();
			if (file == null || sel.isEmpty()) {
				return null;
			}
			int[] originalSize = ImageTools.getSize(file);
			Bitmap bitmap = ImageTools.cropPicture(file, sel.left, sel.top, sel.right, sel.bottom);
			int[] croppedSize = new int[] {bitmap.getWidth(), bitmap.getHeight()};
			int maxSize = getIntent().getIntExtra(EXTRA_MAXSIZE, EXTRA_MAXSIZE_NO_MAX);
			if (maxSize != EXTRA_MAXSIZE_NO_MAX) {
				bitmap = ImageTools.downscale(bitmap, maxSize, maxSize);
			}
			CompressFormat format = (CompressFormat)getIntent().getSerializableExtra(EXTRA_FORMAT);
			if (format == null) {
				format = CompressFormat.JPEG;
			}
			int quality = getIntent().getIntExtra(EXTRA_QUALITY, 85);
			ImageTools.savePicture(bitmap, format, quality, true /* custom encoder */, file);

			int[] finalSize = new int[] {bitmap.getWidth(), bitmap.getHeight()};
			LOG.info("Cropped image ({}x{} -> {}x{} @ {},{} -> {}x{} (max {})) saved {}@{} at {}",
					originalSize[0], originalSize[1],
					croppedSize[0], croppedSize[1],
					(int)(sel.top * originalSize[0]), (int)(sel.left * originalSize[1]),
					finalSize[0], finalSize[1],
					maxSize,
					format, quality,
					file);
			return file;
		} catch (Exception ex) {
			LOG.error("Cannot crop image file {}", file, ex);
		}
		return null;
	}
	private RectF getPictureRect() {
		float width = mSelection.getWidth();
		float height = mSelection.getHeight();

		RectF selection = new RectF(mSelection.getSelection());
		selection.left = selection.left / width;
		selection.top = selection.top / height;
		selection.right = selection.right / width;
		selection.bottom = selection.bottom / height;
		return selection;
	}

	public static Intent saveTo(Context context, File targetFile, int maxSize, CompressFormat format, int quality) {
		Intent intent = saveTo(context, targetFile, maxSize);
		intent.putExtra(CaptureImage.EXTRA_FORMAT, format);
		intent.putExtra(CaptureImage.EXTRA_QUALITY, quality);
		return intent;
	}
	public static Intent saveTo(Context context, File targetFile, int maxSize) {
		assertFeatures(context);
		Intent intent = new Intent(context, CaptureImage.class);
		intent.putExtra(CaptureImage.EXTRA_OUTPUT, targetFile.getAbsolutePath());
		intent.putExtra(CaptureImage.EXTRA_MAXSIZE, maxSize);
		return intent;
	}
	private static void assertFeatures(Context context) {
		PackageManager pm = context.getPackageManager();
		if (!AndroidTools.hasPermission(context, Manifest.permission.CAMERA)) {
			throw new IllegalStateException("Camera permission is not granted, please add it to your manifest:\n"
					+ "<uses-permission android:name=\"android.permission.CAMERA\" />");
		}
		if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			throw new IllegalStateException("Sorry, this system doesn't have a camera.");
		}
	}

	private class PickClickListener implements OnClickListener {
		@Override public void onClick(View v) {
			mPreview.setVisibility(View.INVISIBLE);
			disableControls();
			mSelection.setSelectionStatus(SelectionStatus.FOCUSING);
			ImageTools.getPicture(CaptureImage.this, mTargetFile); // continues in onActivityResult
		}
	}

	private class CaptureClickListener implements OnClickListener {
		@Override public void onClick(View v) {
			if (!mPreview.isRunning()) { // picked gallery, camera button -> enable preview
				doRestartPreview();
				return;
			}
			disableControls();
			if (mSavedFile == null) {
				if (!take(new Callback<byte[]>() {
					@Override public void call(byte... data) {
						doSave(data);
						prepareCrop();
						enableControls();
					}
				})) {
					String message = "Please enable camera before taking a picture.";
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
				}
			} else {
				doRestartPreview();
				enableControls();
			}
		}
	}

	private void enableControls() {
		// post, so everything has time to set up
		controls.post(new Runnable() {
			@Override public void run() {
				controls.setVisibility(View.VISIBLE);
			}
		});
	}
	private void disableControls() {
		// CONSIDER a grayscale colorfilter on the preview?
		controls.setVisibility(View.INVISIBLE);
	}

	private class CropClickListener implements OnClickListener {
		@Override public void onClick(View v) {
			if (mSavedFile != null) {
				doCrop();
				doReturn();
			} else {
				if (!take(new Callback<byte[]>() {
					@Override public void call(byte... data) {
						doSave(data);
						doCrop();
						doReturn();
					}
				})) {
					String message = "Please select or take a picture before cropping.";
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
				}
			}
		}
	}

	private static class ThumbWrapViewTarget<Z> extends WrapViewTarget<Z> implements RequestListener<Object, Z> {
		private boolean isThumbnail;
		public ThumbWrapViewTarget(ImageViewTarget<? super Z> target) {
			super(target);
		}
		@Override public void onLoadStarted(Drawable placeholder) {
			super.onLoadStarted(placeholder);
			isThumbnail = false;
		}
		@Override public boolean onResourceReady(Z resource, Object model, Target<Z> target,
				boolean isFromMemoryCache, boolean isFirstResource) {
			this.isThumbnail = isFirstResource;
			return false; // normal route, just capture arguments
		}
		@Override public void onResourceReady(Z resource, GlideAnimation<? super Z> glideAnimation) {
			super.onResourceReady(resource, glideAnimation);
			if (isThumbnail) {
				update(LayoutParams.MATCH_PARENT);
			}
		}
		@Override public boolean onException(Exception e, Object model, Target<Z> target,
				boolean isFirstResource) {
			return false; // go for onLoadFailed
		}
	}
}
