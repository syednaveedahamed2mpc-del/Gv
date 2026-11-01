package com.nexus.sidekick;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.PixelFormat;import android.os.*;import android.view.*;import android.widget.*;
public class OverlayService extends Service{
 WindowManager wm;View bubble;WindowManager.LayoutParams p;
 public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("nexus","NEXUS floating shortcut",NotificationManager.IMPORTANCE_LOW));
 Notification.Builder nb=new Notification.Builder(this,"nexus").setContentTitle("NEXUS Sidekick").setContentText("Floating shortcut is active").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));startForeground(42,nb.build());
 wm=(WindowManager)getSystemService(WINDOW_SERVICE);TextView v=new TextView(this);v.setText("✦");v.setTextSize(28);v.setTextColor(Color.CYAN);v.setGravity(Gravity.CENTER);v.setBackgroundColor(Color.rgb(19,29,52));bubble=v;
 p=new WindowManager.LayoutParams(130,130,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;p.x=0;p.y=260;
 v.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;boolean moved;public boolean onTouch(View view,android.view.MotionEvent e){switch(e.getAction()){case 0:x=e.getRawX();y=e.getRawY();ox=p.x;oy=p.y;moved=false;return true;case 1:if(!moved){Intent i=new Intent(OverlayService.this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}return true;case 2:if(Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>12)moved=true;p.x=ox+(int)(e.getRawX()-x);p.y=oy+(int)(e.getRawY()-y);wm.updateViewLayout(bubble,p);return true;}return false;}});
 try{wm.addView(bubble,p);}catch(Exception ex){stopSelf();}}
 public int onStartCommand(Intent i,int f,int id){return START_STICKY;}public android.os.IBinder onBind(Intent i){return null;}public void onDestroy(){if(wm!=null&&bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}}super.onDestroy();}
}
