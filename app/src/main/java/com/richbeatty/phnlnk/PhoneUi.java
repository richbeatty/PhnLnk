package com.richbeatty.phnlnk;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native, dependency-free visual system shared by host and companion views. */
final class PhoneUi {
    static final int BG = Color.rgb(246,247,244);
    static final int CARD = Color.WHITE;
    static final int INK = Color.rgb(29,44,39);
    static final int MUTED = Color.rgb(101,116,108);
    static final int FOREST = Color.rgb(34,94,68);
    static final int PALE = Color.rgb(223,237,225);
    static final int BORDER = Color.rgb(223,229,221);
    static final int RED = Color.rgb(146,53,54);
    static final int RED_PALE = Color.rgb(250,235,234);

    private PhoneUi() { }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(Context c, int color, int radius, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c, radius));
        if (border != Color.TRANSPARENT) shape.setStroke(dp(c,1), border);
        return shape;
    }

    static LinearLayout column(Context c) {
        LinearLayout view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    static LinearLayout row(Context c) {
        LinearLayout view = new LinearLayout(c);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    static LinearLayout card(Context c) {
        LinearLayout card = column(c);
        int p = dp(c,18);
        card.setPadding(p,p,p,p);
        card.setBackground(background(c,CARD,22,BORDER));
        card.setClipToPadding(false);
        return card;
    }

    static TextView label(Context c,String text,int size,int color,boolean bold) {
        TextView view=new TextView(c);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if(bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        view.setIncludeFontPadding(true);
        return view;
    }

    static Button button(Context c,String text,int color,int fill,int border) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(color);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(background(c,fill,15,border));
        b.setMinHeight(dp(c,48));
        b.setMinimumHeight(dp(c,48));
        b.setPadding(dp(c,12),dp(c,9),dp(c,12),dp(c,9));
        b.setElevation(0);
        return b;
    }

    static LinearLayout.LayoutParams margins(Context c,int top,int bottom) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(c,top),0,dp(c,bottom));
        return lp;
    }

    static LinearLayout.LayoutParams weight(Context c) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        p.setMargins(dp(c,3),0,dp(c,3),0);
        return p;
    }

    static void screen(Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        a.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    static void space(LinearLayout parent,Context c,int height) {
        View view = new View(c);
        parent.addView(view,new LinearLayout.LayoutParams(1,dp(c,height)));
    }
}
