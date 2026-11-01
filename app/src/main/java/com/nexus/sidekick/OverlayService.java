package com.nexus.sidekick;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;

/** Overlay is drag-to-position, tap-to-open, long-hold-to-activate voice in foreground Activity. */
public class OverlayService extends Service {
    private WindowManager manager;
    private VoiceOrbView orb;
    private WindowManager.LayoutParams params;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable pendingHold;
    private boolean moved=false,longPressed=false;
    @Override public void onCreate(){super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("nexus_orb","NEXUS floating shortcut",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(this,"nexus_orb")
                .setContentTitle("NEXUS Sidekick").setContentText("Hold the orb to talk • tap to open")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).setContentIntent(pi).build();
        startForeground(42,notice);
        if(!Settings.canDrawOverlays(this)){Diagnostics.record(this,"OVERLAY","Missing overlay permission");stopSelf();return;}
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        orb=new VoiceOrbView(this);
        int size=(int)(getResources().getDisplayMetrics().density*68);
        params=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;params.x=0;params.y=(int)(getResources().getDisplayMetrics().density*250);
        orb.setOnTouchListener(new View.OnTouchListener(){
            float startX,startY;int originX,originY;
            final float dragThreshold=ViewConfiguration.get(OverlayService.this).getScaledTouchSlop()*2.5f;
            @Override public boolean onTouch(View view,MotionEvent event){
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        startX=event.getRawX();startY=event.getRawY();originX=params.x;originY=params.y;
                        moved=false;longPressed=false;
                        pendingHold=()->{
                            if(moved)return;
                            longPressed=true;orb.setListening(true);
                            Diagnostics.record(OverlayService.this,"OVERLAY","Long-press accepted; requesting speech UI");
                            launch(true);
                        };
                        handler.postDelayed(pendingHold,650);return true;
                    case MotionEvent.ACTION_MOVE:
                        if(!moved&&Math.hypot(event.getRawX()-startX,event.getRawY()-startY)>dragThreshold){
                            moved=true;handler.removeCallbacks(pendingHold);Diagnostics.record(OverlayService.this,"OVERLAY","Orb moved");
                        }
                        if(moved){params.x=originX+(int)(event.getRawX()-startX);params.y=originY+(int)(event.getRawY()-startY);
                            try{manager.updateViewLayout(orb,params);}catch(Exception ignored){}}
                        return true;
                    case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(pendingHold);
                        if(event.getActionMasked()==MotionEvent.ACTION_UP&&!moved&&!longPressed){Diagnostics.record(OverlayService.this,"OVERLAY","Tap accepted");launch(false);}
                        orb.setListening(false);return true;
                    default:return false;
                }
            }
        });
        try{manager.addView(orb,params);Diagnostics.record(this,"OVERLAY","Floating orb attached");}
        catch(Exception e){Diagnostics.record(this,"OVERLAY","Attach failed: "+e.getClass().getSimpleName());stopSelf();}
    }
    private void launch(boolean listen){
        Intent i=new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(listen)i.putExtra("start_listening",true);
        try{startActivity(i);}catch(Exception e){Diagnostics.record(this,"OVERLAY","Activity launch blocked: "+e.getClass().getSimpleName());}
    }
    @Override public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
    @Override public android.os.IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){if(pendingHold!=null)handler.removeCallbacks(pendingHold);
        if(manager!=null&&orb!=null)try{manager.removeView(orb);}catch(Exception ignored){}
        Diagnostics.record(this,"OVERLAY","Service stopped");super.onDestroy();}
}