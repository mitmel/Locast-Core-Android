package edu.mit.mel.locast.mobile.widget;

import java.util.Random;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class StaticView extends SurfaceView implements SurfaceHolder.Callback {
    final SurfaceHolder surfaceHolder;
    private StaticThread staticThread;

    public StaticView(Context context) {
    	this(context, null);
    }

	public StaticView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	public StaticView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		surfaceHolder = getHolder();
		surfaceHolder.addCallback(this);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		staticThread.updateDimensions(width, height);
		staticThread.start();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (staticThread == null || staticThread.getState() == Thread.State.TERMINATED){
			staticThread = new StaticThread(holder, getContext());
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		staticThread.requestStop();
		while (retry) {
			try {
				staticThread.join();
				staticThread = null;

				retry = false;
			} catch (final InterruptedException e) {
				// ignore
			}
		}
	}

	private class StaticThread extends Thread {
		private final SurfaceHolder surfaceHolder;
		private final Context context;
		private boolean running = true;
		private final Random rand;

		public StaticThread(SurfaceHolder surfaceHolder, Context context) {
			this.surfaceHolder = surfaceHolder;
			this.context = context;
			rand = new Random();
		}

		public void requestStop(){
			this.running = false;
		}

		private int x, y;
		private int val, color;
		private int width, height;
		private int[] colorBuff = new int[width+1];

		private void updateDimensions(int width, int height){
			this.width = width;
			this.height = height;
			colorBuff = new int[width+1];
		}

		private void doDraw(Canvas c){
			for (y = 0; y < height; y += 2){
				for (x = 0; x < width; x+= 2){
					val = rand.nextInt(256);
					color = Color.rgb(val, val, val);
					colorBuff[x] = color;
					colorBuff[x+1] = color;
				}
				c.drawBitmap(colorBuff, 0, width, 0, y, width, 1, false, null);
				c.drawBitmap(colorBuff, 0, width, 0, y+1, width, 1, false, null);
			}
		}

		@Override
		public void run() {
			Canvas c = null;

			while(running){
				try {
					c = surfaceHolder.lockCanvas();
					synchronized (surfaceHolder) {
						doDraw(c);
					}
				}finally {
					if (c != null){
						surfaceHolder.unlockCanvasAndPost(c);
					}
				}

				try {
					Thread.sleep(10);
				} catch (final InterruptedException e) {
					running = false;
				}

			}
		}
	}
}
