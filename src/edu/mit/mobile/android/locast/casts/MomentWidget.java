package edu.mit.mobile.android.locast.casts;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Cast;

public class MomentWidget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		for (final int appWidgetId: appWidgetIds){

			final Intent intent = new Intent(Intent.ACTION_INSERT, Cast.CONTENT_URI, context, Moment.class);
			// XXX somehow make it so it doesn't open the existing task here
			final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

			final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.moment_widget);
			views.setOnClickPendingIntent(R.id.moment_widget, pendingIntent);

			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}
}
