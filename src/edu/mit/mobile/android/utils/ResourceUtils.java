package edu.mit.mobile.android.utils;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;

public class ResourceUtils {

	/**
	 * Like {@link Context#getString(int, Object...)}, but supports styled text.
	 *
	 * Note: this routine converts from html back to html a few times, so it's
	 * not the most efficient way to format text. Only use it if you need styled
	 * text.
	 *
	 * @param context
	 * @param resID
	 *            the string text resource
	 * @param formatArgs
	 * @return formatted, potentially styled text
	 */
	public static CharSequence getText(Context context, int resID,
			Object... formatArgs) {
		final CharSequence text = context.getText(resID);
		if (text instanceof Spanned) {
			final String htmlFormatString = Html.toHtml((Spanned) text);
			return Html.fromHtml(String.format(htmlFormatString, formatArgs));
		} else {
			return context.getString(resID, formatArgs);
		}
	}
}
