package com.nexus.sidekick;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import java.util.List;

/** User-opt-in Android Accessibility skill; no screenshots, no taps by guessed coordinates. */
public class NexusAccessibilityService extends AccessibilityService {
    static volatile NexusAccessibilityService active;
    @Override protected void onServiceConnected() {active=this;Diagnostics.record(this,"SKILLS","Accessibility enabled");}
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* Deliberately no passive event collection. */ }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {if(active==this)active=null;super.onDestroy();}
    public static boolean isEnabled(){return active!=null;}
    public boolean global(String name) {
        int action;
        switch(name){case "go_home":action=GLOBAL_ACTION_HOME;break;
            case "go_back":action=GLOBAL_ACTION_BACK;break;
            case "recent_apps":action=GLOBAL_ACTION_RECENTS;break;
            case "open_notifications":action=GLOBAL_ACTION_NOTIFICATIONS;break;
            default:return false;}
        return performGlobalAction(action);
    }
    public boolean tapLabel(String label) {
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try {
            List<AccessibilityNodeInfo> list=root.findAccessibilityNodeInfosByText(label);
            for(AccessibilityNodeInfo node:list){
                String txt=node.getText()==null?"":node.getText().toString();
                String desc=node.getContentDescription()==null?"":node.getContentDescription().toString();
                if(!label.equalsIgnoreCase(txt)&&!label.equalsIgnoreCase(desc))continue;
                AccessibilityNodeInfo cur=node;
                for(int depth=0;cur!=null&&depth<4;depth++){
                    if(cur.isClickable()&&cur.isEnabled())return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    cur=cur.getParent();
                }
            }
        }finally{root.recycle();}
        return false;
    }
    public boolean typeText(String text) {
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try{
            AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if(focus==null||!focus.isEditable()||focus.isPassword()||!focus.isEnabled())return false;
            Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
            return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        }finally{root.recycle();}
    }
    public boolean scroll(boolean down) {
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try{return scrollRec(root,down,0);}finally{root.recycle();}
    }
    private boolean scrollRec(AccessibilityNodeInfo node,boolean down,int depth){
        if(depth>14||node==null)return false;
        if(node.isScrollable()){
            int action=down?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if(node.performAction(action))return true;
        }
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo c=node.getChild(i);
            if(c!=null){boolean worked=scrollRec(c,down,depth+1);c.recycle();if(worked)return true;}
        }
        return false;
    }
    public String screenSummary(){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return "No accessible screen available";
        StringBuilder text=new StringBuilder();int[] count={0};
        try{summarize(root,text,count,0);}finally{root.recycle();}
        return text.length()==0?"No accessible text on screen":ToolGuard.truncate(text.toString(),1100);
    }
    private void summarize(AccessibilityNodeInfo node,StringBuilder out,int[] count,int depth){
        if(node==null||depth>12||count[0]>=42||out.length()>1050)return;
        if(node.isVisibleToUser()&&!node.isPassword()){
            CharSequence text=node.getText();
            if(text==null||text.length()==0)text=node.getContentDescription();
            if(text!=null&&text.length()>0){out.append(ToolGuard.truncate(text.toString().replace('\n',' '),85)).append(" | ");count[0]++;}
        }
        for(int i=0;i<node.getChildCount()&&count[0]<42;i++){
            AccessibilityNodeInfo child=node.getChild(i);if(child!=null){summarize(child,out,count,depth+1);child.recycle();}
        }
    }
}
