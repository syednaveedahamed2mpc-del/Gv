package com.nexus.sidekick;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Lightweight cyan waveform; no timers or animations when not listening. */
public class VoiceOrbView extends View {
    Paint p=new Paint(3);boolean listening=false;float amplitude=0;
    public VoiceOrbView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setListening(boolean on){listening=on;if(!on)amplitude=0;invalidate();}
    public void setLevel(float level){amplitude=Math.max(0,Math.min(1,level/12f));invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float r=Math.min(getWidth(),getHeight())*.31f;
        p.setStyle(Paint.Style.FILL);p.setColor(0xff102840);c.drawCircle(cx,cy,r*1.15f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(4,r*.12f));p.setColor(0xff20e6ee);
        c.drawCircle(cx,cy,r*(.64f+.08f*amplitude),p);
        if(listening){p.setColor(0xff77fbff);p.setStrokeWidth(Math.max(3,r*.055f));
            for(int i=-3;i<=3;i++){float h=r*(.24f+.7f*amplitude)*(1f-Math.abs(i)*.12f);
                float x=cx+i*r*.2f;c.drawLine(x,cy-h/2,x,cy+h/2,p);}
            p.setColor(0x6620e6ee);p.setStrokeWidth(Math.max(2,r*.06f));
            c.drawCircle(cx,cy,r*(.94f+.19f*amplitude),p);
        }else{p.setStyle(Paint.Style.FILL);p.setColor(0xff20e6ee);c.drawCircle(cx,cy,r*.2f,p);}
    }
}
