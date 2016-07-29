package net.twisterrob.inventory.android.content.model;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.support.annotation.*;
import android.text.TextUtils;

import net.twisterrob.android.db.DatabaseOpenHelper;
import net.twisterrob.android.utils.tools.*;
import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.java.collections.RestoreStructureAlgo;

@WorkerThread
public class CategoryHelpBuilder {
	private static final String LEVEL_COLUMN = "level";

	private static final String STYLE = "<style>\n"
			+ "h2.category {\n"
			+ "    margin: 0;\n"
			+ "    margin-top: 0.25em;\n"
			+ "}\n"
			+ "h3.subcategory {\n"
			+ "    margin: 0;\n"
			+ "    margin-top: 0.5em;\n"
			+ "}\n"
			+ ".level {\n"
			+ "    margin-left: 1em;\n"
			+ "}\n"
			+ "p.keywords {\n"
			+ "    margin-top: .25em;\n"
			+ "    margin-bottom: 0;\n"
			+ "}\n"
			+ ".description {\n"
			+ "    font-style: italic;\n"
			+ "    font-size: 0.75em;\n"
			+ "    font-weight: normal;\n"
			+ "}\n"
			+ ".description:before {\n"
			+ "    content: '\\200A\\2014\\200A'; /* x200A=hairline space, x2014=emdash */ \n"
			+ "}\n"
			+ "a {\n"
			+ "    text-decoration: none;"
			+ "}\n"
			+ "\n"
			+ "#legend {\n"
			+ "     font-size: small;\n"
			+ "     margin: 0;\n"
			+ "}\n"
			+ "@media print {\n"
			+ "    #legend, #toc {\n"
			+ "        display: none;\n"
			+ "    }\n"
			+ "    .subcategory.collapsed + .keywords {\n"
			+ "        display: block;\n"
			+ "    }\n"
			+ "    body {\n"
			+ "        font-size: 0.9em;\n"
			+ "    }\n"
			+ "}\n"
			+ "@media screen {\n"
			+ "    .description {\n"
			+ "        color: #888;\n"
			+ "    }\n"
			+ "    .subcategory.has-keywords {\n"
			+ "        cursor: pointer;\n"
			+ "    }\n"
			+ "    .subcategory.collapsed + .keywords {\n"
			+ "        display: none;\n"
			+ "    }\n"
			+ "    .subcategory.collapsed:after {\n"
			+ "        content: '*';\n"
			+ "    }\n"
			+ "}\n"
			+ "</style>\n\n";

	private static final String HEADER = ""
			+ "<h1>%s Category Keywords</h1>\n";

	private static final String LEGEND = "<p id=\"legend\">\n"
			+ "     It is advised to view this document in landscape orientation.\n"
			+ "     <br/>\n"
			+ "     <button onclick=\"for (var i = 0; i < window.subcategories.length; ++i) { window.subcategories[i].classList.remove('collapsed'); }\">Expand All</button>\n"
			+ "     <button onclick=\"for (var i = 0; i < window.subcategories.length; ++i) { window.subcategories[i].classList.add('collapsed'); }\">Collapse All</button>\n"
			+ "     <br/>\n"
			+ "     <span>*</span> Click subcategories to toggle their keywords.<br/>\n"
			+ "     <span>^</span> Click the up arrows to go to the top of the page.<br/>\n"
			+ "</p>\n\n";

	private static final String FOOTER = "<script>\n"
			+ "var subcategories = document.querySelectorAll('.subcategory.has-keywords');\n"
			+ "for (var i = 0; i < subcategories.length; ++i) {\n"
			+ "    subcategories[i].onclick = function() {\n"
			+ "        this.classList.toggle('collapsed');\n"
			+ "    }\n"
			+ "}\n"
			+ "</script>\n\n";
	/** last measured size was 131k */
	private static final int EMPIRIC_SIZE = 150 * 1024;

	private final Context context;
	public CategoryHelpBuilder(Context context) {
		this.context = context;
	}

	public void export(File file) throws IOException {
		IOTools.writeAll(new FileOutputStream(file), buildHTML());
		AndroidTools.makeFileDiscoverable(context, file);
	}

	public String buildHTML() {
		StringBuilder sb = new StringBuilder(EMPIRIC_SIZE);
		write(sb);
		return sb.toString();
	}

	private void write(StringBuilder out) {
		Cursor cursor = App.db().listRelatedCategories(null);
		//noinspection TryFinallyCanBeTryWithResources
		try {
			out.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n");
			out.append(STYLE);
			out.append(String.format(Locale.ROOT, HEADER, context.getString(R.string.app_name)));
			new WriteCategoryTOC(out).run(cursor);
			out.append(LEGEND);
			new WriteCategoryTree(out).run(cursor);
			out.append(FOOTER);
		} finally {
			cursor.close();
		}
	}
	private void writeCat(StringBuilder out, Cursor cursor) {
		String categoryName = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.NAME));
		Long parentID = DatabaseTools.getOptionalLong(cursor, ParentColumns.PARENT_ID);
		CharSequence categoryTitle = AndroidTools.getText(context, categoryName);
		if (parentID == null) {
			CharSequence description = AndroidTools.getText(context, ResourceNames.getDescriptionName(categoryName));
			out.append(String.format(Locale.ROOT,
					"<h2 class=\"category\" id=\"%s\">%s<a href=\"#toc\">^</a><span class=\"description\">%s</span></h2>\n",
					categoryName, categoryTitle, description));
		} else {
			CharSequence keywords;
			try {
				keywords = AndroidTools.getText(context, ResourceNames.getKeywordsName(categoryName));
			} catch (NotFoundException ignore) {
				keywords = null;
			}
			String toc = getLevel(cursor) <= 1?
					"<a href=\"#toc\" onclick=\"arguments[0].stopPropagation()\">^</a>" : "";
			out.append(String.format(Locale.ROOT, "<h3 class=\"subcategory%s\" id=\"%s\">%s%s</h3>\n",
					!TextUtils.isEmpty(keywords)? " has-keywords" : "", categoryName, categoryTitle, toc));
			out.append("<p class=\"keywords level\">\n");
			if (!TextUtils.isEmpty(keywords)) {
				appendKeywords(out, keywords);
			} else {
				out.append("<span class=\"error\">No keywords for ").append(categoryName).append("</span>");
			}
			out.append("\n</p>");
		}
	}
	private final Pattern keywordSplitter = Pattern.compile("(?m)\\s*([^,]+?)\\s*([,;]|\\z)");
	private void appendKeywords(StringBuilder out, CharSequence keywords) {
		Matcher matcher = keywordSplitter.matcher(keywords);
		while (matcher.find()) {
			out.append("<span class=\"keyword\">");
			out.append(keywords, matcher.start(1), matcher.end(1));
			out.append("</span>");
			out.append(keywords, matcher.start(2), matcher.end(2));
			out.append(' ');
		}
	}

	private int getLevel(Cursor cursor) {
		int level = 0;
		if (cursor != null) {
			int levelColumn = cursor.getColumnIndex(LEVEL_COLUMN);
			if (levelColumn != DatabaseOpenHelper.CURSOR_NO_COLUMN) {
				level = cursor.getInt(levelColumn);
			}
		}
		return level;
	}

	private class WriteCategoryTOC extends RestoreStructureAlgo<Cursor, Cursor, Void> {
		private final StringBuilder out;
		// FIXME not implemented for deeper levels
		private final int maxLevel = 1;
		WriteCategoryTOC(StringBuilder out) {
			this.out = out;
		}

		@Override protected @NonNull Iterator<Cursor> start(@NonNull Cursor data) {
			out.append("<div id=\"toc\">\n");
			out.append("<h2>Table of Contents</h2>\n");
			out.append("<ul>\n");
			data.moveToPosition(-1);
			return DatabaseTools.iterate(data).iterator();
		}
		@Override protected int getLevel(Cursor cursor) {
			return CategoryHelpBuilder.this.getLevel(cursor);
		}
		@Override protected void onIncrementLevel(int level, @Nullable Cursor cursor) {
			if (level < maxLevel) {
				out.append("<ul>\n");
			}
		}
		@Override protected void onEntity(int level, @NonNull Cursor cursor) {
			if (level < maxLevel) {
				String categoryName = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.NAME));
				CharSequence categoryTitle = AndroidTools.getText(context, categoryName);
				out.append(String.format(Locale.ROOT, "\t<li><a href=\"#%s\">%s</a></li>\n",
						categoryName, categoryTitle));
			}
		}
		@Override protected void onDecrementLevel(int level) {
			if (level < maxLevel) {
				out.append("</ul>\n");
			}
		}
		@Override protected Void finish() {
			out.append("</ul>\n");
			out.append("</div>\n");
			return null;
		}
	}

	private class WriteCategoryTree extends RestoreStructureAlgo<Cursor, Cursor, Void> {
		private final StringBuilder out;
		WriteCategoryTree(StringBuilder out) {
			this.out = out;
		}

		@Override protected @NonNull Iterator<Cursor> start(@NonNull Cursor data) {
			data.moveToPosition(-1);
			return DatabaseTools.iterate(data).iterator();
		}
		@Override protected int getLevel(Cursor cursor) {
			return CategoryHelpBuilder.this.getLevel(cursor);
		}
		@Override protected void onIncrementLevel(int level, @Nullable Cursor cursor) {
			out.append("<div class=\"level\">\n");
		}
		@Override protected void onEntity(int level, @NonNull Cursor cursor) {
			writeCat(out, cursor);
		}
		@Override protected void onDecrementLevel(int level) {
			out.append("</div>\n");
		}
		@Override protected Void finish() {
			return null;
		}
	}
}

