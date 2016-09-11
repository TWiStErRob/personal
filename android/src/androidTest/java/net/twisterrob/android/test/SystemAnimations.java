package net.twisterrob.android.test;

import java.lang.reflect.*;
import java.util.Arrays;

import org.slf4j.*;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.*;
import android.os.*;

/**
 * Equivalent to:
 * <code><pre>
 * adb shell settings put global window_animation_scale 0
 * adb shell settings put global transition_animation_scale 0
 * adb shell settings put global animator_duration_scale 0
 * </pre></code>
 * but requires
 * <code>adb shell pm grant app.package.id {@value #ANIMATION_PERMISSION}</code>
 *
 * Usage:
 * <code><pre>
 * SystemAnimations anims = new SystemAnimations();
 * try {
 *     anims.backup();
 *     anims.disableAll();
 *     // do stuff that requires animations to be disabled
 * } finally {
 *     anims.restore();
 * }
 * </pre></code>
 * @see <a href="https://product.reverb.com/2015/06/06/disabling-animations-in-espresso-for-android-testing/">Disabling Animations</a>
 * @see <a href="https://gist.github.com/danielgomezrico/9371a79a7222a156ddad">Gist</a>
 */
public class SystemAnimations {
	private static final Logger LOG = LoggerFactory.getLogger(SystemAnimations.class);
	private static final String ANIMATION_PERMISSION = "android.permission.SET_ANIMATION_SCALE";
	private static final float DISABLED = 0.0f;
	private static final float DEFAULT = 1.0f;

	private static final Class<?> windowManagerStubClass;
	private static final Method asInterface;
	private static final Class<?> serviceManagerClass;
	private static final Method getService;
	private static final Class<?> windowManagerClass;
	private static final Method setAnimationScales;
	private static final Method getAnimationScales;

	private static final Method getWindowSession;
	private static final Method getDurationScale;
	private static final Method setDurationScale;

	static {
		try {
			windowManagerStubClass = Class.forName("android.view.IWindowManager$Stub");
			asInterface = windowManagerStubClass.getDeclaredMethod("asInterface", IBinder.class);
			serviceManagerClass = Class.forName("android.os.ServiceManager");
			getService = serviceManagerClass.getDeclaredMethod("getService", String.class);
			windowManagerClass = Class.forName("android.view.IWindowManager");
			setAnimationScales = windowManagerClass.getDeclaredMethod("setAnimationScales", float[].class);
			getAnimationScales = windowManagerClass.getDeclaredMethod("getAnimationScales");
		} catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}
		try {
			if (VERSION_CODES.JELLY_BEAN_MR1 <= VERSION.SDK_INT) {
				Class<?> windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal");
				getWindowSession = windowManagerGlobalClass.getDeclaredMethod("getWindowSession");
			} else if (VERSION_CODES.JELLY_BEAN == VERSION.SDK_INT) {
				Class<?> viewRootImplClass = Class.forName("android.view.ViewRootImpl");
				getWindowSession = viewRootImplClass.getDeclaredMethod("getWindowSession");
			} else {
				getWindowSession = null;
			}
			if (VERSION_CODES.JELLY_BEAN <= VERSION.SDK_INT) {
				// ValueAnimator is HONEYCOMB, but scaling was introduced in JELLY_BEAN
				getDurationScale = ValueAnimator.class.getDeclaredMethod("getDurationScale");
				setDurationScale = ValueAnimator.class.getDeclaredMethod("setDurationScale", Float.TYPE);
			} else {
				getDurationScale = null;
				setDurationScale = null;
			}
		} catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}
	}

	private final Object windowManager;
	private final boolean canSetAnimationScales;
	private final boolean canSetDurationScale;
	private float[] previousScales;

	public SystemAnimations(Context context) {
		try {
			IBinder windowManagerBinder = (IBinder)getService.invoke(null, "window");
			windowManager = asInterface.invoke(null, windowManagerBinder);
		} catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}
		int permStatus = context.checkCallingOrSelfPermission(ANIMATION_PERMISSION);
		canSetAnimationScales = permStatus == PackageManager.PERMISSION_GRANTED;
		canSetDurationScale = getDurationScale != null && setDurationScale != null;
		if (!canSetAnimationScales) {
			String resolution = canSetDurationScale
					? "using ValueAnimator hack as fallback"
					: "all operations will be no-op";
			LOG.warn("Application doesn't have {}, {}.", ANIMATION_PERMISSION, resolution);
		}
	}

	public void backup() {
		previousScales = getScales();
	}

	public void restore() {
		setScales(previousScales);
	}

	public void disableAll() {
		setSystemAnimationsScale(DISABLED);
	}

	public void enableAll() {
		setSystemAnimationsScale(DEFAULT);
	}

	public void setSystemAnimationsScale(float animationScale) {
		try {
			float[] currentScales = getScales();
			Arrays.fill(currentScales, animationScale);
			setScales(currentScales);
		} catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}
	}
	public void setScales(float... currentScales) {
		if (canSetAnimationScales) {
			try {
				setAnimationScales.invoke(windowManager, new Object[] {currentScales});
			} catch (Throwable ex) {
				throw new IllegalStateException(ex);
			}
		} else if (canSetDurationScale) {
			try {
				preWindowSessionFix();
				setDurationScale.invoke(null, currentScales[0]);
			} catch (Throwable ex) {
				throw new IllegalStateException(ex);
			}
		}
	}
	public float[] getScales() {
		if (canSetAnimationScales) {
			try {
				return (float[])getAnimationScales.invoke(windowManager);
			} catch (Throwable ex) {
				throw new IllegalStateException(ex);
			}
		} else if (canSetDurationScale) {
			try {
				preWindowSessionFix();
				return new float[] {(Float)getDurationScale.invoke(null)};
			} catch (Throwable ex) {
				throw new IllegalStateException(ex);
			}
		} else {
			return new float[0];
		}
	}
	/**
	 * Calling {@code getWindowSession()} is required to ensure that the duration scale
	 * in {@link ValueAnimator} is read from the preferences first, so it doesn't overwrite our hacked value.
	 * If this method is not called before hacking the duration scale, the {@link android.view.ViewRootImpl} constructor
	 * will call it during {@link android.app.Activity#onResume onResume}.
	 * To see the exact call stack, add a field write breakpoint on {@code sDurationScale} and start an activity.
	 *
	 * @see android.view.WindowManagerGlobal#getWindowSession() how getWindowSession sets ValueAnimator.setDurationScale
	 * @see android.animation.ValueAnimator#sDurationScale
	 */
	@SuppressWarnings("JavadocReference")
	@TargetApi(VERSION_CODES.JELLY_BEAN_MR1)
	private void preWindowSessionFix() throws IllegalAccessException, InvocationTargetException {
		if (getWindowSession != null) {
			if (VERSION.SDK_INT <= VERSION_CODES.JELLY_BEAN_MR1) {
				getWindowSession.invoke(null, Looper.getMainLooper());
			} else {
				getWindowSession.invoke(null);
			}
		}
	}
}
