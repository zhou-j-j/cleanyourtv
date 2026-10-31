package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** One tiny weather card under the home clock; same native app, city and preferences as AppleWeatherActivity. */
public final class AppleWeatherHomeChip {
    private AppleWeatherHomeChip() {}
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    public static void attach(MainActivity a,LinearLayout leftBlock){
        if(leftBlock.findViewWithTag("cm2012-weather-chip")!=null)return;
        TextView chip=new TextView(a); chip.setTag("cm2012-weather-chip");
        chip.setText("☀  天气 · 选择城市");chip.setTextColor(Color.WHITE);chip.setTextSize(14);
        chip.setPadding(dp(a,13),dp(a,7),dp(a,13),dp(a,7));
        chip.setFocusable(true);chip.setClickable(true);chip.setSingleLine(true);
        GradientDrawable fill=new GradientDrawable();fill.setColor(0x8B2F2938);fill.setCornerRadius(dp(a,15));fill.setStroke(dp(a,1),0x66FFFFFF);
        chip.setBackground(fill);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(a,38)); lp.topMargin=dp(a,4);
        leftBlock.addView(chip,lp);
        chip.setOnClickListener(v->a.startActivity(new Intent(a,AppleWeatherActivity.class)));
        chip.setOnFocusChangeListener((v,focused)->{chip.setAlpha(focused?1f:.88f);chip.setScaleX(focused?1.04f:1f);});
        update(a,chip);
        chip.setOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){update(a,chip);}
            @Override public void onViewDetachedFromWindow(View v){}
        });
        // Refresh when returning from the weather page, without running any timer in the HOME screen.
        leftBlock.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (hasFocus && chip.isAttachedToWindow()) update(a, chip);
        });
    }
    private static void update(Activity a,TextView chip){
        android.content.SharedPreferences p=a.getSharedPreferences("cm2012_apple_weather",Context.MODE_PRIVATE);
        String city=p.getString("city","");String lat=p.getString("lat","");String lon=p.getString("lon","");
        if(city.isEmpty()||lat.isEmpty()||lon.isEmpty()){chip.setText("☀  天气 · 点击选择城市");return;}
        chip.setText("☀  "+city+" · 获取天气中");
        new Thread(()->{
            try{
                String u="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,weather_code";
                HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(8000);
                try{try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[2048];int n;while((n=in.read(buf))>0){out.write(buf,0,n);if(out.size()>30000)break;}
                    JSONObject v=new JSONObject(out.toString("UTF-8")).getJSONObject("current");
                    String display="☀  "+city+"  "+Math.round(v.optDouble("temperature_2m"))+"°  ·  查看天气";
                    chip.post(()->chip.setText(display));
                }}finally{c.disconnect();}
            }catch(Exception ignored){chip.post(()->chip.setText("☁  "+city+" · 打开天气"));}
        },"CM2012WeatherChip").start();
    }
}
