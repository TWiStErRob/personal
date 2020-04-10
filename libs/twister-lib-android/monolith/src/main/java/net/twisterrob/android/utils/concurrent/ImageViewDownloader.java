package net.twisterrob.android.utils.concurrent;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import net.twisterrob.android.utils.log.*;
import net.twisterrob.android.utils.tools.IOTools;

/** @deprecated use Glide */
@Deprecated @SuppressWarnings("deprecation")
public class ImageViewDownloader extends AsyncTask<String, Void, Bitmap> {
	private static final Log LOG = LogFactory.getLog(Tag.IO);
	@SuppressLint("StaticFieldLeak")
	private final ImageView m_view;
	private final Callback<ImageView> m_callback;

	public ImageViewDownloader(ImageView view, Callback<ImageView> callback) {
		m_view = view;
		m_callback = callback;
	}

	@Override
	protected Bitmap doInBackground(String... urls) {
		String url = urls[0];
		Bitmap bitmap = null;
		try {
			bitmap = IOTools.getImage(url, true);
		} catch (Exception ex) {
			LOG.error("Cannot download %s", ex, url);
		}
		return bitmap;
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		m_view.setImageBitmap(result);
		if (m_callback != null) {
			m_callback.call(m_view);
		}
	}
}