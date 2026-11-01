package com.nexus.sidekick;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Drawn entirely on-device, with no animation/media assets or heavy dependencies. */
public class VoiceOrbView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean listening;
    private float level = .2f;
    private long started;
    public VoiceOrbView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void setListening(boolean value) { listening=value; started=System.currentTimeMillis();invalidate(); }
    public void setLevel(float rms) { level=Math.min(1f, Math.max(.12f,(rms+2f)/13f)); invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx=getWidth()/2f, cy=getHeight()/2f;
        float radius=Math.min(getWidth(),getHeight())*.28f;
        float oscillate=(float)Math.sin((System.currentTimeMillis()-started)/210.0);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));
        p.setColor(0x6629eefa);c.drawCircle(cx,cy,radius*1.34f+(listening?oscillate*dp(7):0),p);
        p.setColor(0xff1de9f2);p.setStrokeWidth(dp(3));c.drawCircle(cx,cy,radius,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xff102e49);c.drawCircle(cx,cy,radius-dp(4),p);
        if(listening){
            float width=dp(5),gap=dp(10);
            for(int i=0;i<5;i++){
                float wave=(float)Math.abs(Math.sin((System.currentTimeMillis()-started)/145.0+i*.9));
                float half=dp(9)+(wave*.75f+level*.65f)*dp(22);
                p.setColor(i==2?Color.WHITE:0xff1de9f2);c.drawRoundRect(cx+(i-2)*gap-width/2,cy-half,cx+(i-2)*gap+width/2,cy+half,width/2,width/2,p);
            }
            postInvalidateDelayed(55);
        }else{
            p.setColor(0xff1de9f2);
            c.drawCircle(cx,cy,dp(12),p);
            p.setColor(0xff173c59);c.drawCircle(cx,cy,dp(5),p);
        }
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
}