package com.stackoverflow;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class MediaUtils {
	private static final String TAG = MediaUtils.class.getSimpleName();

	public static final boolean HAS_IMAGE_CAPTURE_BUG = hasImageCaptureBug();

	private static final String TMP_SD_LOCATION;

	static {
		String tmp;
		try {
			final File tmpfile = new File(Environment.getExternalStorageDirectory(),
					"/tmp/locast_new");
			tmpfile.mkdirs();
			tmp = tmpfile.getCanonicalPath();

		} catch (final IOException e) {
			e.printStackTrace();
			tmp = Environment.getExternalStorageDirectory() + "/tmp/locast_new";
		}
		TMP_SD_LOCATION = tmp;
	}

	private static final String TMP_SD_LOCATION_JPG = TMP_SD_LOCATION + ".jpg";
	private static final String TMP_SD_LOCATION_MP4 = TMP_SD_LOCATION + ".mp4";



	private static boolean hasImageCaptureBug() {
		final ArrayList<String> devices = new ArrayList<String>();
		// list of known devices that have the bug

		devices.add("android-devphone1/dream_devphone/dream");
		devices.add("generic/sdk/generic");
		devices.add("vodafone/vfpioneer/sapphire");
		devices.add("tmobile/kila/dream");
		devices.add("verizon/voles/sholes");
		devices.add("google_ion/google_ion/sapphire");

		final String devstring = android.os.Build.BRAND + "/" + android.os.Build.PRODUCT + "/"
				+ android.os.Build.DEVICE;
		Log.d(TAG, "device string: " + devstring);
		return devices.contains(devstring);
	}

	/**
	 * The result of this should be handled by {@link #handleImageCaptureResult(Context, Intent)} to
	 * return a URI pointing to the image.
	 *
	 * @return an intent that should be used with
	 *         {@link Activity#startActivityForResult(Intent, int)}.
	 */
	public static Intent getImageCaptureIntent(File destination) {
		final Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
		if (HAS_IMAGE_CAPTURE_BUG) {
			final File tmpfile = new File(TMP_SD_LOCATION_JPG);
			tmpfile.getParentFile().mkdirs();
			i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
					Uri.fromFile(new File(TMP_SD_LOCATION_JPG)));
		} else {
			i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
					Uri.fromFile(new File(destination, System.currentTimeMillis() + ".jpg")));
		}

		return i;
	}

	/**
	 * @param context
	 * @param intent
	 *            the onActivityResult intent
	 * @return a URI pointing to the image or null if there was an error. Unfortunately, if the
	 *         device is amongst those with the bug, the image size will be fairly small.
	 */
	public static Uri handleImageCaptureResult(Context context, Intent intent) {
		Uri u;
		if (HAS_IMAGE_CAPTURE_BUG) {
			final File fi = new File(TMP_SD_LOCATION_JPG);
			try {
				u = Uri.parse(android.provider.MediaStore.Images.Media.insertImage(
						context.getContentResolver(), fi.getAbsolutePath(), null, null));
				if (!fi.delete()) {
					Log.i(TAG, "Failed to delete " + fi);
				}
			} catch (final FileNotFoundException e) {
				u = null;
				e.printStackTrace();
			}
		} else {
			u = intent.getData();
		}
		return u;
	}
}
