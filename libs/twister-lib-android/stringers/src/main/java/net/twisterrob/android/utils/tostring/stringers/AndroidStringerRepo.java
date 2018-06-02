package net.twisterrob.android.utils.tostring.stringers;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.location.Address;
import android.os.AsyncTask;
import android.os.Build.*;
import android.support.v4.app.*;
import android.support.v4.widget.DrawerLayoutStateStringer;
import android.support.v7.widget.*;
import android.view.AbsSavedState;

import net.twisterrob.android.utils.tostring.stringers.detailed.*;
import net.twisterrob.android.utils.tostring.stringers.detailed.activitymanager.*;
import net.twisterrob.android.utils.tostring.stringers.name.ResourceNameStringer;
import net.twisterrob.java.utils.tostring.StringerRepo;

@TargetApi(VERSION_CODES.HONEYCOMB_MR2)
public class AndroidStringerRepo {
	public static void init(StringerRepo repo, Context context) {
		ResourceNameStringer.INSTANCE = new ResourceNameStringer(context);
		repo.register(AbsSavedState.class, new AbsSavedStateStringer());
		repo.register(android.support.design.widget.NavigationView.SavedState.class,
				new NavigationViewSavedStateStringer());
		repo.register(android.support.v7.widget.LinearLayoutManager.SavedState.class,
				new LinearLayoutManagerSavedStateStringer());
		repo.register(android.support.v7.widget.StaggeredGridLayoutManager.SavedState.class,
				new StaggeredGridLayoutManagerSavedStateStringer());
		repo.register("android.support.v4.widget.DrawerLayout$SavedState", new DrawerLayoutStateStringer());
		repo.register(android.support.v4.app.Fragment.SavedState.class, new SupportFragmentSavedStateStringer());
		if (VERSION_CODES.HONEYCOMB_MR2 <= VERSION.SDK_INT) {
			repo.register(android.app.Fragment.SavedState.class, new FragmentSavedStateStringer());
		}
		repo.register("android.support.v4.app.FragmentManagerState", new SupportFragmentManagerStateStringer());
		repo.register(android.support.v4.content.Loader.class, new SupportLoaderStringer());
		if (VERSION_CODES.HONEYCOMB <= VERSION.SDK_INT) {
			repo.register(android.content.Loader.class, new LoaderStringer());
		}
		repo.register(android.content.Intent.class, new IntentStringer());
		repo.register(android.app.PendingIntent.class, new PendingIntentStringer());
		repo.register(android.os.Bundle.class, new BundleStringer());
		repo.register(android.util.SparseArray.class, new SparseArrayStringer(context));
		repo.register(android.support.v4.app.Fragment.class, new SupportFragmentStringer());
		repo.register(android.support.v7.widget.RecyclerView.SavedState.class, new RecyclerViewSavedStateStringer());

		repo.register(android.support.v7.widget.Toolbar.SavedState.class,
				new SupportToolbarSavedStateStringer());
		repo.register("android.support.v4.app.FragmentState", new SupportFragmentStateStringer());
		repo.register("android.support.v4.app.BackStackState", new SupportBackStackStateStringer());
		// Don't use this, it converts every integer and warns a lot, find out a better way
		//repo.register(Integer.class, new ResourceNameStringer(context));
		repo.register(android.support.v4.app.FragmentManager.class, new SupportFragmentManagerStringer());
		repo.register(android.support.v4.app.FragmentManager.BackStackEntry.class,
				new SupportBackStackEntryStringer(context));

		repo.register(AsyncTask.class, new AsyncTaskStringer());
		// FIXME figure out how to do this dynamically
//		repo.register(AsyncTaskResult.class, new AsyncTaskResultStringer());
		repo.register(Address.class, new AddressStringer());

		repo.register(ActivityManager.RunningAppProcessInfo.class, new RunningAppProcessInfoStringer());
		repo.register(ActivityManager.MemoryInfo.class, new MemoryInfoStringer());
		repo.register(ActivityManager.class, new ActivityManagerStringer());
		repo.register(Configuration.class, new ConfigurationStringer());
		repo.register(Bitmap.class, new BitmapStringer());
	}
}