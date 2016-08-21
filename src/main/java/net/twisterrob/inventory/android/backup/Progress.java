package net.twisterrob.inventory.android.backup;

import java.util.*;

public final class Progress implements Cloneable {
	public Phase phase;
	/** number of images done from total */
	public int imagesDone;
	/** total number of images */
	public int imagesTotal;
	/** number of items done from total */
	public int done;
	/** total number of items */
	public int total;
	public boolean pending;
	public Throwable failure;
	public List<String> warnings = new ArrayList<>();

	public Progress() {
		phase = Phase.Init;
		pending = true;
	}

	public Progress(Throwable ex) {
		this();
		failure = ex;
	}

	@Override public Progress clone() {
		try {
			return (Progress)super.clone();
		} catch (CloneNotSupportedException ex) {
			throw new InternalError(ex.toString());
		}
	}

	@Override public String toString() {
		String header = String.format(Locale.ROOT, "%s: data=%d/%d images=%d/%d, %spending, %d warnings, %s",
				phase, done, total, imagesDone, imagesTotal,
				pending? "" : "not ", warnings.size(), failure != null? failure : "no error");
		if (warnings.isEmpty()) {
			return header;
		}
		StringBuilder sb = new StringBuilder(header);
		sb.append(":");
		for (String warning : warnings) {
			sb.append("\n").append(warning);
		}
		return sb.toString();
	}

	public enum Phase {
		Init,
		Data,
		Images
	}
}