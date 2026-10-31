package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Integrated, standalone Chinese weather screen. Does not need addons/weather APK. */
public class AppleWeatherActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root, forecast;
    private TextView cityTitle, temp, details, status;
    private int dp(float p) { return Math.round(p * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int top, int bottom, int r) {
        GradientDrawable b = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top,bottom});
        b.setCornerRadius(dp(r)); b.setStroke(dp(1), 0x55FFFFFF); return b;
    }
    private TextView text(String val, int size, int color) {
        TextView v = new TextView(this); v.setText(val); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(16),dp(6),dp(16),dp(6)); return v;
    }
    private void action(LinearLayout target, String name, Runnable onClick) {
        TextView b = text(name, 17, Color.WHITE); b.setFocusable(true); b.setClickable(true);
        b.setBackground(bg(0x883A354F,0x88312E41,18));
        b.setOnClickListener(v -> onClick.run());
        b.setOnFocusChangeListener((v,on) -> { b.setBackground(bg(on ? 0xFF6C617A : 0x883A354F,
                on ? 0xFF51475F : 0x88312E41,18)); b.setScaleX(on ? 1.03f : 1f); b.setScaleY(on ? 1.03f : 1f); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(52)); lp.setMargins(dp(8),0,dp(8),0);
        target.addView(b,lp);
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF100B19);
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true);
        sc.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF170815,0xFF181D35,0xFF070910}));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(38),dp(16),dp(38),dp(28)); sc.addView(root); setContentView(sc);
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL);
        action(bar,"← 返回桌面",this::finish);
        action(bar,"⌕ 中文城市搜索",this::askCity);
        action(bar,"⟳ 刷新天气",this::refresh);
        root.addView(bar);
        cityTitle = text("天气 · 请选择城市",27,Color.WHITE);
        cityTitle.setTypeface(null,Typeface.BOLD); cityTitle.setPadding(dp(18),dp(20),dp(12),dp(8));root.addView(cityTitle);
        temp=text("—°",64,Color.WHITE); temp.setTypeface(null,Typeface.BOLD);root.addView(temp);
        details=text("中文城市名称、今日天气与未来七天预报",18,0xFFE9DAEB);root.addView(details);
        status=text("",13,0xFFCAC0CF);root.addView(status);
        TextView heading=text("未来 7 天",22,Color.WHITE); heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(dp(18),dp(20),dp(10),dp(14));root.addView(heading);
        forecast = new LinearLayout(this); forecast.setOrientation(LinearLayout.VERTICAL);
        root.addView(forecast);
        String name = getSharedPreferences("cm2012_apple_weather",MODE_PRIVATE).getString("city","");
        if(name.isEmpty()) { cityTitle.setText("天气 · 请选择城市");status.setText("请点击上方「中文城市搜索」，输入北京、上海等城市名称。"); }
        else refresh();
        bar.getChildAt(1).requestFocus();
    }
    private void askCity() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("北京、上海、成都、深圳……");
        input.setInputType(InputType.TYPE_CLASS_TEXT);input.setTextColor(Color.BLACK);input.setHintTextColor(0xFF666666);
        LinearLayout form=new LinearLayout(this);form.setPadding(dp(22),0,dp(22),0);form.addView(input,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("搜索中文城市").setView(form)
                .setNegativeButton("取消",null).setPositiveButton("搜索",(d,w) -> search(input.getText().toString().trim())).show();
    }
    private String alias(String name) {
        String[] zh={"北京","上海","广州","深圳","天津","重庆","成都","杭州","南京","武汉","西安","苏州","长沙","郑州","济南","青岛","厦门","福州","昆明","南宁","哈尔滨","沈阳","大连","长春","乌鲁木齐","拉萨","呼和浩特","银川","兰州","合肥","南昌","宁波","东莞","佛山","珠海","海口","三亚","石家庄","太原","贵阳","香港","澳门"};
        String[] en={"Beijing","Shanghai","Guangzhou","Shenzhen","Tianjin","Chongqing","Chengdu","Hangzhou","Nanjing","Wuhan","Xi'an","Suzhou","Changsha","Zhengzhou","Jinan","Qingdao","Xiamen","Fuzhou","Kunming","Nanning","Harbin","Shenyang","Dalian","Changchun","Urumqi","Lhasa","Hohhot","Yinchuan","Lanzhou","Hefei","Nanchang","Ningbo","Dongguan","Foshan","Zhuhai","Haikou","Sanya","Shijiazhuang","Taiyuan","Guiyang","Hong Kong","Macau"};
        String fixed=name.replace("市","").replace("特别行政区","").trim();
        for(int i=0;i<zh.length;i++) if(fixed.equals(zh[i])) return en[i];
        return name;
    }
    private static String fetch(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","CM2012-Apple-Weather/1.0");
        try {
            if(c.getResponseCode()!=200) throw new java.io.IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(out.size()>1500000)throw new java.io.IOException("response too long");out.write(block,0,n);}
                return out.toString("UTF-8");
            }
        }finally{c.disconnect();}
    }
    private JSONArray resultsFor(String query) throws Exception {
        String url="https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query,"UTF-8")+"&count=20&language=zh&format=json";
        return new JSONObject(fetch(url)).optJSONArray("results");
    }
    private void search(String typed) {
        if(typed.length()==0) { Toast.makeText(this,"请输入城市名称",Toast.LENGTH_SHORT).show();return; }
        status.setText("正在搜索「"+typed+"」……");
        io.execute(() -> {
            try {
                String q=alias(typed);JSONArray matches=resultsFor(q);
                if((matches==null || matches.length()==0) && !q.equals(typed)) matches=resultsFor(typed);
                if((matches==null || matches.length()==0) && typed.endsWith("市")) matches=resultsFor(typed.substring(0,typed.length()-1));
                if(matches==null || matches.length()==0) throw new Exception("没有找到城市，请尝试输入附近的地级市或拼音");
                final JSONArray found=matches;
                List<JSONObject> choices = new ArrayList<>();
                for(int i=0;i<found.length();i++) {JSONObject o=found.optJSONObject(i);if(o!=null && "CN".equals(o.optString("country_code"))) choices.add(o);}
                for(int i=0;i<found.length();i++) {JSONObject o=found.optJSONObject(i);if(o!=null && !"CN".equals(o.optString("country_code"))) choices.add(o);}
                int cnt=Math.min(10,choices.size());String[] options=new String[cnt];
                for(int i=0;i<cnt;i++){ JSONObject o=choices.get(i);options[i]=o.optString("name")+" · "+o.optString("admin1")+" · "+o.optString("country"); }
                runOnUiThread(() -> { status.setText("请选择正确的城市（中国城市优先显示）");
                    new AlertDialog.Builder(this).setTitle("搜索结果 · "+typed).setItems(options,(dialog,position)->{
                        JSONObject o=choices.get(position);
                        getSharedPreferences("cm2012_apple_weather",MODE_PRIVATE).edit().putString("city",o.optString("name"))
                                .putString("province",o.optString("admin1"))
                                .putString("lat",String.valueOf(o.optDouble("latitude")))
                                .putString("lon",String.valueOf(o.optDouble("longitude"))).apply();
                        refresh();
                    }).setNegativeButton("取消",null).show(); });
            }catch(Exception e){runOnUiThread(() -> status.setText("搜索失败："+e.getMessage()));}
        });
    }
    private static String condition(int c) {
        if(c==0) return "晴";if(c<=3)return "多云";if(c<=48)return "有雾";
        if(c<=57)return "毛毛雨";if(c<=67)return "降雨";if(c<=77)return "降雪";
        if(c<=82)return "阵雨";if(c<=86)return "阵雪";if(c>=95)return "雷暴";return "阴";
    }
    private void refresh() {
        String city=getSharedPreferences("cm2012_apple_weather",MODE_PRIVATE).getString("city","");
        String lat=getSharedPreferences("cm2012_apple_weather",MODE_PRIVATE).getString("lat","");
        String lon=getSharedPreferences("cm2012_apple_weather",MODE_PRIVATE).getString("lon","");
        if(city.isEmpty()||lat.isEmpty()||lon.isEmpty()) {status.setText("请先选择城市");return;}
        cityTitle.setText(city+" · "+getSharedPreferences("cm2012_apple_weather",MODE_PRIVATE).getString("province",""));
        status.setText("天气更新中……");
        io.execute(() -> {
            try {
                String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
                        +"&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                        +"&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=7&timezone=auto";
                JSONObject data=new JSONObject(fetch(url)); JSONObject current=data.getJSONObject("current");JSONObject daily=data.getJSONObject("daily");
                int t=(int)Math.round(current.optDouble("temperature_2m"));
                int feels=(int)Math.round(current.optDouble("apparent_temperature"));
                int hum=current.optInt("relative_humidity_2m");
                int wind=(int)Math.round(current.optDouble("wind_speed_10m"));
                String weather=condition(current.optInt("weather_code"));
                JSONArray times=daily.getJSONArray("time");JSONArray max=daily.getJSONArray("temperature_2m_max");
                JSONArray min=daily.getJSONArray("temperature_2m_min");JSONArray code=daily.getJSONArray("weather_code");
                String[] days=new String[Math.min(7,times.length())];
                for(int i=0;i<days.length;i++)days[i]=times.optString(i)+"     "+condition(code.optInt(i))
                        +"      "+Math.round(max.optDouble(i))+"°  /  "+Math.round(min.optDouble(i))+"°";
                String aqi="";
                try {
                    JSONObject air = new JSONObject(fetch("https://air-quality-api.open-meteo.com/v1/air-quality?latitude="+lat+"&longitude="+lon+"&current=us_aqi"));
                    JSONObject now=air.optJSONObject("current");if(now!=null && !now.isNull("us_aqi")) aqi=" · AQI "+now.optInt("us_aqi");
                }catch(Exception ignored) { }
                final String aqiText=aqi;
                runOnUiThread(() -> {
                    if(isFinishing())return;
                    temp.setText(t+"°  "+weather);
                    details.setText("体感 "+feels+"°  ·  湿度 "+hum+"%  ·  风速 "+wind+" km/h"+aqiText);
                    forecast.removeAllViews();
                    for(String one:days){TextView cell=text(one,19,Color.WHITE);
                        cell.setBackground(bg(0x5539324E,0x663C425C,13));
                        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(47));p.bottomMargin=dp(7);forecast.addView(cell,p);}
                    status.setText("数据：Open-Meteo · 最近一次刷新："+new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new java.util.Date()));
                });
            }catch(Exception e){runOnUiThread(() -> status.setText("天气获取失败："+e.getMessage()+"；请检查盒子互联网连接。"));}
        });
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
