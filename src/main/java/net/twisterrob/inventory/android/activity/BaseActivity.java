package net.twisterrob.inventory.android.activity;

import org.slf4j.*;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.support.annotation.*;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.view.*;

import com.android.debug.hv.ViewServer;

import net.twisterrob.android.activity.BackPressAware;
import net.twisterrob.android.content.glide.*;
import net.twisterrob.android.utils.tools.AndroidTools;
import net.twisterrob.inventory.android.BuildConfig;
import net.twisterrob.inventory.android.Constants.Pic;
import net.twisterrob.inventory.android.content.Intents;

import static net.twisterrob.java.utils.CollectionTools.*;

public class BaseActivity extends VariantActivity {
	private static final Logger LOG = LoggerFactory.getLogger(BaseActivity.class);

	@Override protected void onCreate(Bundle savedInstanceState) {
		LOG.debug("Creating {}@{} {}\n{}",
				getClass().getSimpleName(),
				Integer.toHexString(System.identityHashCode(this)),
				AndroidTools.toLongString(getIntent().getExtras()),
				getIntent()
		);
		super.onCreate(savedInstanceState);
		if (BuildConfig.DEBUG) {
			updateScreenKeeping();
			ViewServer.get(this).addWindow(this);
		}
	}

	@Override protected void onResume() {
		super.onResume();
		if (BuildConfig.DEBUG) {
			updateScreenKeeping();
			ViewServer.get(this).setFocusedWindow(this);
		}
	}

	@Override protected void onDestroy() {
		if (BuildConfig.DEBUG) {
			ViewServer.get(this).removeWindow(this);
		}
		super.onDestroy();
	}

	/** @see <a href="http://stackoverflow.com/a/30037089/253468">How do I keep my screen unlocked during USB debugging?</a> */
	private void updateScreenKeeping() {
		if (Debug.isDebuggerConnected()) {
			//LOG.trace("Keeping screen on for debugging, detach debugger and force an onResume to turn it off.");
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			//LOG.trace("Keeping screen on for debugging is now deactivated.");
		}
	}

	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		AndroidTools.showActionBarOverflowIcons(menu, true);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override protected void onNewIntent(Intent intent) {
		LOG.trace("Refreshing {}@{} {}\n{}",
				getClass().getSimpleName(),
				Integer.toHexString(System.identityHashCode(this)),
				AndroidTools.toString(intent.getExtras()),
				intent
		);
		super.onNewIntent(intent);
	}

	@Override public void onBackPressed() {
		for (Fragment fragment : nonNull(getSupportFragmentManager().getFragments())) {
			if (fragment != null && fragment.isAdded()
					&& fragment instanceof BackPressAware && ((BackPressAware)fragment).onBackPressed()) {
				return;
			}
		}
		super.onBackPressed();
	}

	@SuppressWarnings("unchecked")
	protected <T extends Fragment> T getFragment(@IdRes int id) {
		return (T)getSupportFragmentManager().findFragmentById(id);
	}

	public void setActionBarSubtitle(CharSequence string) {
		getSupportActionBar().setSubtitle(string);
	}
	public void setActionBarTitle(CharSequence string) {
		getSupportActionBar().setTitle(string);
	}

	public void setIcon(Drawable iconDrawable) {
		getSupportActionBar().setIcon(iconDrawable);
	}
	public void setIcon(@RawRes int resourceId) {
		Pic.svg()
		   .load(resourceId)
		   .transform(new PaddingTransformation(this, AndroidTools.dipInt(this, 4)))
		   .into(new ActionBarIconTarget(getSupportActionBar()));
	}

	/** We kind of know that there's an action bar in all child classes*/
	@Override public @NonNull ActionBar getSupportActionBar() {
		return super.getSupportActionBar();
	}

	@Override public boolean onSupportNavigateUp() {
		if (Intents.isChildNav(getIntent())) {
			onBackPressed();
			return true;
		}
		return super.onSupportNavigateUp();
	}

	/** Workaround for broken up navigation post Jelly Bean?!
	 * @see <a href="http://stackoverflow.com/questions/14602283/up-navigation-broken-on-jellybean">Up navigation broken on JellyBean?</a> */
	@Override public void supportNavigateUpTo(Intent upIntent) {
		upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(upIntent);
		finish();
	}
}
