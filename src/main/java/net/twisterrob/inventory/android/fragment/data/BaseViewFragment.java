package net.twisterrob.inventory.android.fragment.data;

import java.io.File;

import org.slf4j.*;

import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.*;
import android.support.v4.content.Loader;
import android.support.v4.view.*;
import android.support.v4.widget.CursorAdapter;
import android.view.*;
import android.view.View.*;
import android.widget.*;

import static android.content.Context.*;

import net.twisterrob.android.utils.tools.AndroidTools;
import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.activity.ImageActivity;
import net.twisterrob.inventory.android.content.Loaders;
import net.twisterrob.inventory.android.content.contract.CommonColumns;
import net.twisterrob.inventory.android.content.model.ImagedDTO;
import net.twisterrob.inventory.android.fragment.BaseSingleLoaderFragment;
import net.twisterrob.inventory.android.view.*;

public abstract class BaseViewFragment<DTO extends ImagedDTO, T> extends BaseSingleLoaderFragment<T> {
	private static final Logger LOG = LoggerFactory.getLogger(BaseViewFragment.class);

	protected static final String DYN_TypeLoader = "typeLoader";
	protected static final String DYN_TypeChangeTitle = "typeTitle";

	protected ViewPager pager;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_details, container, false);
	}

	@Override public void onViewCreated(View view, Bundle bundle) {
		super.onViewCreated(view, bundle);
		pager = (ViewPager)view.findViewById(R.id.pager);
	}

	public void onSingleRowLoaded(DTO entity) {
		pager.setAdapter(new ImageAndDescriptionAdapter(entity));
		pager.setCurrentItem(getDefaultPageIndex());
	}

	private int getDefaultPageIndex() {
		String key = getString(R.string.pref_defaultEntityDetailsPage);
		String defaultValue = getString(R.string.pref_defaultEntityDetailsPage_default);
		String defaultPage = App.getPrefs().getString(key, defaultValue);
		return AndroidTools.findIndexInResourceArray(getContext(),
				R.array.pref_defaultEntityDetailsPage_values, defaultPage);
	}
	protected abstract CharSequence getDetailsString(DTO entity, boolean DEBUG);

	private class ImageAndDescriptionAdapter extends PagerAdapter {
		private DTO entity;

		public ImageAndDescriptionAdapter(DTO imageData) {
			this.entity = imageData;
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return getResources().getTextArray(R.array.pref_defaultEntityDetailsPage_entries)[position];
		}

		@Override
		public int getItemPosition(Object object) {
			View view = (View)object;
			if (view.findViewById(R.id.image) != null) {
				return 0;
			} else if (view.findViewById(R.id.details) != null) {
				return 1;
			}
			return -1;
		}

		@Override
		public Object instantiateItem(final ViewGroup container, int position) {
			LayoutInflater inflater = (LayoutInflater)container.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
			View view;
			switch (position) {
				case 0: {
					view = inflater.inflate(R.layout.inc_details_image, container, false);
					ImageView image = (ImageView)view.findViewById(R.id.image);
					ImageView type = (ImageView)view.findViewById(R.id.type);
					image.setOnClickListener(new ImageOpenListener());
					image.setOnLongClickListener(new ImageChangeListener());
					type.setOnClickListener(new ChangeTypeListener());

					entity.loadInto(image, type, true);
					break;
				}
				case 1: {
					view = inflater.inflate(R.layout.inc_details_details, container, false);

					final boolean debug = App.getPrefs().getBoolean(getString(R.string.pref_displayDebugDetails),
							getResources().getBoolean(R.bool.pref_displayDebugDetails_default));

					TextView details = (TextView)view.findViewById(R.id.details);
					details.setText(getDetailsString(entity, debug));
					//details.setMovementMethod(ScrollingMovementMethod.getInstance());
					details.setOnTouchListener(new OnTouchListener() {
						@Override
						public boolean onTouch(View v, MotionEvent event) {
							// http://stackoverflow.com/questions/8121491/is-it-possible-to-add-a-scrollable-textview-to-a-listview
							container.getParent().requestDisallowInterceptTouchEvent(true);
							return false;
						}
					});
					break;
				}
				default:
					throw new UnsupportedOperationException("Position #" + position + " is not supported");
			}
			container.addView(view);
			return view;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			container.removeView((View)object);
		}

		@Override
		public boolean isViewFromObject(View view, Object obj) {
			return view == obj;
		}

		private class ImageOpenListener implements OnClickListener {
			@Override public void onClick(View v) {
				try {
					String path = entity.getImage(getContext());
					if (path != null) {
						showImage(path);
					} else {
						editImage();
					}
				} catch (Exception ex) {
					LOG.warn("Cannot start image viewer for {}", entity, ex);
				}
			}

			private void showImage(String path) {
				File file = new File(path);
				Uri uri = FileProvider.getUriForFile(getContext(), Constants.AUTHORITY_IMAGES, file);
				Intent intent = new Intent(Intent.ACTION_VIEW);
				if (App.getPrefs().getBoolean(getString(R.string.pref_internalImageViewer),
						getResources().getBoolean(R.bool.pref_internalImageViewer_default))) {
					intent.setComponent(new ComponentName(getContext(), ImageActivity.class));
				}
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				intent.setDataAndType(uri, "image/jpeg");
				getActivity().startActivity(intent);
			}
		}

		private class ImageChangeListener implements OnLongClickListener {
			@Override public boolean onLongClick(View v) {
				editImage();
				return true;
			}
		}

		private class ChangeTypeListener implements OnClickListener {
			@Override public void onClick(View v) {
				Loaders typeLoader = getDynamicResource(DYN_TypeLoader);
				getLoaderManager().initLoader(typeLoader.id(), null,
						new CursorSwapper(getContext(), new TypeAdapter(getContext())) {
							@Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
								super.onLoadFinished(loader, data);

								int position = AndroidTools.findItemPosition(adapter, entity.type);
								new AlertDialog.Builder(getContext())
										.setTitle((CharSequence)getDynamicResource(DYN_TypeChangeTitle))
										.setSingleChoiceItems(adapter, position, new TypeSelectedListener(adapter))
										.create()
										.show()
								;
							}
						});
			}
			private class TypeSelectedListener implements DialogInterface.OnClickListener {
				private final CursorAdapter adapter;
				public TypeSelectedListener(CursorAdapter adapter) {
					this.adapter = adapter;
				}

				@Override public void onClick(DialogInterface dialog, int which) {
					Cursor cursor = (Cursor)adapter.getItem(which);
					long newType = cursor.getLong(cursor.getColumnIndex(CommonColumns.ID));
					String newTypeName = cursor.getString(cursor.getColumnIndex(CommonColumns.NAME));
					CharSequence message = update(entity, newType, newTypeName); // FIXME DB on UI
					dialog.dismiss();
					refresh();

					App.toastUser(message);
				}
			}
		}
	}

	protected abstract CharSequence update(DTO cursor, long newType, String newTypeName);

	protected abstract void editImage();
}
