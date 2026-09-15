package com.xuvenz.portraitforcer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class OrientationService extends Service {
    public static final String ACTION_ENABLE="com.xuvenz.portraitforcer.ENABLE";
    public static final String ACTION_ENABLE_STRONG="com.xuvenz.portraitforcer.ENABLE_STRONG";
    public static final String ACTION_DISABLE="com.xuvenz.portraitforcer.DISABLE";
    private static final String CHANNEL_ID="portrait_forcer"; private static final int NOTIFICATION_ID=42;
    private WindowManager wm; private View overlay; private int oldAuto=1,oldRotation=0; private boolean saved=false,strong=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable reassert=new Runnable(){public void run(){if(strong){applySystemPortrait();refreshOverlay();handler.postDelayed(this,750);}}};

    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);createChannel();}
    @Override public int onStartCommand(Intent i,int flags,int id){String a=i==null?ACTION_ENABLE:i.getAction();if(ACTION_DISABLE.equals(a)){disable();stopForeground(true);stopSelf();return START_NOT_STICKY;} strong=ACTION_ENABLE_STRONG.equals(a);startForeground(NOTIFICATION_ID,notification());enable();return START_STICKY;}
    private void enable(){saveRotation();applySystemPortrait();addOverlay();handler.removeCallbacks(reassert);if(strong)handler.post(reassert);}
    private void saveRotation(){if(saved||!Settings.System.canWrite(this))return;try{oldAuto=Settings.System.getInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1);oldRotation=Settings.System.getInt(getContentResolver(),Settings.System.USER_ROTATION,0);saved=true;}catch(Exception ignored){}}
    private void applySystemPortrait(){if(Settings.System.canWrite(this))try{Settings.System.putInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0);Settings.System.putInt(getContentResolver(),Settings.System.USER_ROTATION,0);}catch(Exception ignored){}}
    private WindowManager.LayoutParams params(){int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;p.screenOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;return p;}
    private void addOverlay(){if(overlay!=null)return;overlay=new View(this);overlay.setBackgroundColor(0x00000000);try{wm.addView(overlay,params());}catch(Exception e){overlay=null;}}
    private void refreshOverlay(){if(overlay==null){addOverlay();return;}try{wm.updateViewLayout(overlay,params());}catch(Exception ignored){}}
    private void disable(){strong=false;handler.removeCallbacks(reassert);if(overlay!=null)try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;if(saved&&Settings.System.canWrite(this))try{Settings.System.putInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,oldAuto);Settings.System.putInt(getContentResolver(),Settings.System.USER_ROTATION,oldRotation);}catch(Exception ignored){}saved=false;}
    private Notification notification(){Intent open=new Intent(this,MainActivity.class);PendingIntent op=PendingIntent.getActivity(this,0,open,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);Intent off=new Intent(this,OrientationService.class);off.setAction(ACTION_DISABLE);PendingIntent dp=PendingIntent.getService(this,1,off,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle(strong?"True Portrait active":"Portrait Forcer active").setContentText(strong?"Reasserting portrait for Skylore":"Compatibility portrait active").setContentIntent(op).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_revert,"Restore display",dp).build()).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL_ID,"Portrait Forcer",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    @Override public void onDestroy(){disable();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
