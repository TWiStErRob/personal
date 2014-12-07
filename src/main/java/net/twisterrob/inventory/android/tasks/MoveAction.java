package net.twisterrob.inventory.android.tasks;

import java.util.Collection;

import android.content.res.Resources;
import android.support.annotation.PluralsRes;

import net.twisterrob.inventory.android.R;

import static net.twisterrob.inventory.android.utils.Plurals.*;

public abstract class MoveAction extends BaseAction {
	protected final long[] IDs;
	protected long targetID;
	private final int typeRes;
	private final int targetTypeRes;

	protected Collection<String> stuff;
	protected String source;
	protected String target;

	public MoveAction(long targetID, @PluralsRes int typeRes, int targetTypeRes, long... IDs) {
		this.IDs = IDs;
		this.targetID = targetID;

		this.typeRes = typeRes;
		this.targetTypeRes = targetTypeRes;
	}

	@Override protected void doPrepare() {
		if (IDs == null || IDs.length == 0) {
			throw new ValidationException(R.string.action_move_error_empty);
		}
	}

	private String buildPlural(Resources res, @PluralsRes int titleRes) {
		String type = res.getQuantityString(typeRes, stuff.size());
		String targetType = res.getQuantityString(targetTypeRes, 1);
		return res.getQuantityString(titleRes, stuff.size(), toPluralArgs(stuff, type, targetType, target));
	}

	@Override public String getConfirmationTitle(Resources res) {
		return buildPlural(res, R.plurals.action_move_title);
	}

	@Override public String getConfirmationMessage(Resources res) {
		return buildPlural(res, R.plurals.action_move_confirm);
	}

	@Override public String getSuccessMessage(Resources res) {
		return buildPlural(res, R.plurals.action_move_success);
	}

	@Override protected String getGenericFailureMessage(Resources res) {
		return buildPlural(res, R.plurals.action_move_failed);
	}
}
