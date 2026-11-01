package com.nexus.sidekick;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/** Opt-in accessibility bridge: visible text only; no screenshots or secret fields. */
public class DeviceAccessibilityService extends AccessibilityService {
    private static WeakReference<DeviceAccessibilityService> instance=new WeakReference<>(null);
    public static DeviceAccessibilityService current(){return instance.get();}
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=new WeakReference<>(this);Diagnostics.record(this,"ACCESS","Permission enabled");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){instance.clear();Diagnostics.record(this,"ACCESS","Service disconnected");super.onDestroy();}
    public boolean global(int id){return performGlobalAction(id);}
    public boolean scroll(boolean forward){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try{
            ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);int seen=0;
            while(!nodes.isEmpty()&&seen++<400){AccessibilityNodeInfo n=nodes.removeFirst();
                if(n.isScrollable()&&n.performAction(forward?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;
                for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)nodes.addLast(c);}
            }return false;
        }finally{root.recycle();}
    }
    public boolean tapVisibleText(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try{
            ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);int seen=0;
            while(!nodes.isEmpty()&&seen++<400){AccessibilityNodeInfo n=nodes.removeFirst();
                CharSequence label=n.getText();CharSequence desc=n.getContentDescription();
                boolean match=(label!=null&&label.toString().equalsIgnoreCase(text))||(desc!=null&&desc.toString().equalsIgnoreCase(text));
                if(match&&!n.isPassword()){
                    AccessibilityNodeInfo target=n;
                    for(int depth=0;depth<5&&target!=null;depth++){
                        if(target.isClickable()&&target.isEnabled())return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        target=target.getParent();
                    }
                }
                for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)nodes.addLast(c);}
            }return false;
        }finally{root.recycle();}
    }
    public boolean typeFocused(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try{
            AccessibilityNodeInfo field=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if(field==null||!field.isEditable()||field.isPassword()||!field.isEnabled())return false;
            Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
            return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        }finally{root.recycle();}
    }
}
