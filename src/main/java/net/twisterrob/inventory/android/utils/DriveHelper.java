package net.twisterrob.inventory.android.utils;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnDismissListener;
import android.content.IntentSender.SendIntentException;
import android.os.*;

import com.google.android.gms.common.*;
import com.google.android.gms.common.api.*;
import com.google.android.gms.common.api.GoogleApiClient.Builder;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.drive.Drive;

import net.twisterrob.android.utils.tools.AndroidTools;

public class DriveHelper {
	private static final Logger LOG = LoggerFactory.getLogger(DriveHelper.class);

	public interface ConnectedTask {
		void execute(GoogleApiClient client) throws Exception;
	}

	private final Activity activity;
	private final int resolutionReqCode;
	private final List<ConnectedTask> delayed = new ArrayList<ConnectedTask>();

	private boolean connectable;
	private GoogleApiClient client;
	private final CountDownLatch latch = new CountDownLatch(1);

	public DriveHelper(Activity activity, int resolutionReqCode) {
		LOG.trace(activity.getClass().getSimpleName());
		this.activity = activity;
		this.resolutionReqCode = resolutionReqCode;
	}

	public synchronized void startConnect() {
		if (client == null) {
			LOG.trace("getConnectedClient({}) Lazy init in getGoogleApiClient", extracted());
			client = createClientBuilder().build();
		}
		client.connect();
	}

	public GoogleApiClient getConnectedClient() {
		LOG.trace("getConnectedClient({}) Getting connected client", extracted());
		synchronized (this) {
			if (client == null) {
				LOG.trace("getConnectedClient({}) Lazy init in getGoogleApiClient", extracted());
				client = createClientBuilder().build();
				LOG.trace("getConnectedClient({}) starting to connect", extracted());
				client.connect(); // TODO consider blockingConnect without a latch
				try {
					LOG.trace("getConnectedClient({}) waiting for response", extracted());
					latch.await(30, TimeUnit.SECONDS);
					LOG.trace("getConnectedClient({}) responded", extracted());
				} catch (InterruptedException ex) {
					LOG.trace("getConnectedClient({}) interrupted", extracted());
					return null;
				}
			}
		}
		if (client.isConnected() || (connectable && client.blockingConnect().isSuccess())) {
			LOG.trace("getConnectedClient({}) YAAY!", extracted());
			return client;
		} else {
			LOG.trace("getConnectedClient({}) not connected or can't connect again", extracted());
			return null;
		}
	}

	private static String extracted() {
		return Thread.currentThread().toString().replaceAll("(?:/)CAESHDBCNXFUTlBncV9sbDV|Yg5obA7aFR(?:,)", "");
	}
	public void onResume() {
		LOG.trace("onResume({})", client);
		if (client != null && !client.isConnected() && connectable) {
			client.connect();
		}
	}

	public void onPause() {
		LOG.trace("onPause({})", client);
		if (client != null) {
			client.disconnect();
		}
	}

	public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == resolutionReqCode) {
			if (resultCode == Activity.RESULT_OK) {
				LOG.trace("onActivityResult: everything ok, connecting");
				client.connect();
				return true;
			} else {
				result(false);
			}
		}
		return false;
	}

	public void addTaskAfterConnected(ConnectedTask runnable) {
		if (client != null && client.isConnected()) {
			throw new IllegalStateException("Already connected");
		} else {
			delayed.add(runnable);
		}
	}

	private Builder createClientBuilder() {
		LifeCycle lifeCycle = new LifeCycle();
		return new GoogleApiClient.Builder(activity) //
				.addApi(Drive.API) //
				.addScope(Drive.SCOPE_FILE) //
				.addScope(Drive.SCOPE_APPFOLDER) //
				.addConnectionCallbacks(lifeCycle) //
				.addOnConnectionFailedListener(lifeCycle) //
		;
	}

	private class LifeCycle implements ConnectionCallbacks, OnConnectionFailedListener {
		@Override
		public void onConnected(Bundle connectionHint) {
			LOG.info("GoogleApiClient connected");
			// connect() may be blocking an AsyncTask
			AndroidTools.executeParallel(new Task(), delayed.toArray(new ConnectedTask[delayed.size()]));
		}

		@Override
		public void onConnectionFailed(ConnectionResult result) {
			LOG.info("GoogleApiClient connection failed: {}", result);
			if (!result.hasResolution()) {
				Dialog dialog = GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), activity, 0);
				dialog.setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface dialog) {
						result(false);
					}
				});
				dialog.show();
				return;
			}
			try {
				result.startResolutionForResult(activity, resolutionReqCode);
			} catch (SendIntentException e) {
				LOG.error("Exception while starting resolution activity", e);
			}
		}

		@Override
		public void onConnectionSuspended(int reson) {
			LOG.info("GoogleApiClient disconnected");
		}
	}

	private class Task extends AsyncTask<ConnectedTask, Void, Void> {
		@Override
		protected Void doInBackground(ConnectedTask... params) {
			for (ConnectedTask run: params) {
				try {
					run.execute(client);
				} catch (Exception ex) {
					LOG.error("Task {} failed", run, ex);
					break;
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			result(true);
		}

	}

	private void result(boolean result) {
		connectable = result;
		latch.countDown();
	}
}
