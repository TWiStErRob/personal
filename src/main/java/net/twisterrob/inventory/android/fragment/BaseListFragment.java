package net.twisterrob.inventory.android.fragment;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.*;
import android.widget.*;

import net.twisterrob.inventory.R;
import net.twisterrob.inventory.android.view.*;

public class BaseListFragment<T> extends BaseFragment<T> {
	protected AbsListView list;
	protected CursorAdapter adapter;

	public BaseListFragment() {
		setDynamicResource(DYN_Layout, R.layout.gallery);
	}

	protected void setAdapter(CursorAdapter adapter) {
		this.adapter = adapter;
		((AdapterView<ListAdapter>)list).setAdapter(adapter); // AbsListView.setAdapter is API 11
	}

	protected void swapEmpty(View newEmptyView) {
		View current = list.getEmptyView();
		if (current != null) {
			current.setVisibility(View.GONE);
		}
		list.setEmptyView(newEmptyView);
	}

	protected LoaderCallbacks<Cursor> createListLoaderCallbacks() {
		return new CursorSwapper(getActivity(), adapter) {
			@Override
			public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
				super.onLoadFinished(loader, data);
				swapEmpty(getView().findViewById(android.R.id.empty));
			}
		};
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View root = super.onCreateView(inflater, container, savedInstanceState);

		this.list = (AbsListView)root.findViewById(android.R.id.list);
		swapEmpty(root.findViewById(android.R.id.progress));
		setAdapter(new GalleryAdapter(getActivity()));

		return root;
	}
}
