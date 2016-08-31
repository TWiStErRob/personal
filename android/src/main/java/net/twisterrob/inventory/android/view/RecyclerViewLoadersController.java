package net.twisterrob.inventory.android.view;

import org.slf4j.*;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;

import net.twisterrob.android.adapter.CursorRecyclerAdapter;
import net.twisterrob.inventory.android.content.Loaders;
import net.twisterrob.inventory.android.content.Loaders.LoadersCallbacks;

public abstract class RecyclerViewLoadersController
		extends RecyclerViewLoaderController<CursorRecyclerAdapter<?>, Cursor> {
	private static final Logger LOG = LoggerFactory.getLogger(RecyclerViewLoadersController.class);

	private final Loaders loader;

	public RecyclerViewLoadersController(Fragment fragment, Loaders loader) {
		super(fragment);
		this.loader = loader;
	}

	public Loaders getLoader() {
		return loader;
	}

	@Override protected void setData(CursorRecyclerAdapter<?> adapter, Cursor data) {
		adapter.swapCursor(data);
	}

	private LoaderCallbacks<Cursor> createLoaderCallbacks() {
		return new LoadersCallbacks(getContext()) {
			@Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
				startLoading();
				return super.onCreateLoader(id, args);
			}
			@Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
				updateAdapter(data);
				super.onLoadFinished(loader, data);
			}
			@Override public void onLoaderReset(Loader<Cursor> loader) {
				updateAdapter(null);
				super.onLoaderReset(loader);
			}
		};
	}

	public void startLoad(Bundle args) {
		getLoaderManager().initLoader(loader.id(), args, createLoaderCallbacks());
	}

	public void refresh() {
		getLoaderManager().getLoader(loader.id()).onContentChanged();
	}
}