package net.twisterrob.inventory.android.view;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.TextView;

import net.twisterrob.android.adapter.CursorRecyclerAdapter;
import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.content.contract.CommonColumns;
import net.twisterrob.inventory.android.view.ListAdapter.ViewHolder;

public class ListAdapter extends CursorRecyclerAdapter<ViewHolder> {
	public interface ListItemEvents {
		void removeFromList(long listID);
		void addToList(long listID);
	}

	private final ListItemEvents listener;

	public ListAdapter(Cursor cursor, ListItemEvents listener) {
		super(cursor);
		this.listener = listener;
	}

	@Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View view = inflater.inflate(R.layout.item_list, parent, false);
		return new ViewHolder(view);
	}

	@Override public void onBindViewHolder(ViewHolder holder, Cursor cursor) {
		holder.bind(cursor);
	}

	class ViewHolder extends RecyclerView.ViewHolder implements OnClickListener {
		TextView title;
		TextView count;

		public ViewHolder(View view) {
			super(view);

			title = (TextView)view.findViewById(R.id.title);
			count = (TextView)view.findViewById(R.id.count);

			view.setOnClickListener(this);
		}

		@Override public void onClick(View v) {
			itemView.setEnabled(false);
			Cursor cursor = getCursor();
			if (cursor.moveToPosition(getAdapterPosition())) {
				if (cursor.getShort(cursor.getColumnIndexOrThrow("exists")) != 0) {
					listener.removeFromList(getItemId());
				} else {
					listener.addToList(getItemId());
				}
			}
		}

		private void bind(Cursor cursor) {
			String name = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.NAME));
			int childCount = cursor.getInt(cursor.getColumnIndexOrThrow(CommonColumns.COUNT_CHILDREN_DIRECT));
			boolean exists = cursor.getShort(cursor.getColumnIndexOrThrow("exists")) != 0;

			title.setText(name);
			count.setText(String.valueOf(childCount));
			itemView.setSelected(exists);
			itemView.setEnabled(true);
		}
	}
}
