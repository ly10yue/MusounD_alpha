package studio.interval.tests;

import android.app.*;import android.content.*;import android.os.*;import android.graphics.*;import java.io.*;import java.lang.reflect.*;import java.util.zip.*;import studio.interval.*;

/** Cross-platform export -> native parser -> native audio -> actual game screen. */
public class ImportSmoke extends Instrumentation {
    private GameView game;private MainActivity activity;private int checks=0;
    Object field(String name)throws Exception{Field f=GameView.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        File imported;
        try(InputStream in=getContext().getAssets().open("roundtrip.interval")){imported=ChartPackage.importFile(getTargetContext(),in);}
        Chart chart=new Chart(getTargetContext(),imported);
        check(chart.title.equals("Editor round trip"),"title from desktop export");check(chart.engine.notes.size()==3,"desktop notes round trip");
        check(chart.engine.notes.get(1).hold(),"hold type preserved");check(Math.abs(chart.engine.notes.get(1).duration-120.0/202)<.0001,"hold beat-to-second conversion");
        check(!chart.engine.allowed(.18f,.2f,120.0/202),"upper area blocked");check(chart.engine.allowed(.18f,.8f,120.0/202),"lower area open");
        check(!chart.engine.allowed(.5f,.5f,13*60.0/202),"multi rectangle gap");
        for(double t=0;t<chart.duration+1;t+=1.0/60)chart.engine.autoplay(t);check(chart.engine.perfect==3,"imported chart autoplay");
        // Reject traversal and leave no partial import behind.
        ByteArrayOutputStream malicious=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(malicious)){z.putNextEntry(new ZipEntry("../bad.txt"));z.write(1);z.closeEntry();}
        boolean rejected=false;try{ChartPackage.importFile(getTargetContext(),new ByteArrayInputStream(malicious.toByteArray()));}catch(IOException expected){rejected=true;}check(rejected,"zip traversal rejected");
        activity=(MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        File source=imported;runOnMainSync(()->{try{Method load=MainActivity.class.getDeclaredMethod("load",File.class);load.setAccessible(true);load.invoke(activity,source);Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception e){throw new RuntimeException(e);}});
        long deadline=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<deadline && !(Boolean)field("ready"))SystemClock.sleep(30);
        check((Boolean)field("ready"),"native audio prepared from imported package");check(((String)field("error")).isEmpty(),"no load error");
        SystemClock.sleep(250);Bitmap image=getUiAutomation().takeScreenshot();File shot=new File(getTargetContext().getExternalFilesDir(null),"imported-chart.png");try(OutputStream out=new FileOutputStream(shot)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        runOnMainSync(()->{try{Method start=GameView.class.getDeclaredMethod("start",boolean.class);start.setAccessible(true);start.invoke(game,true);}catch(Exception e){throw new RuntimeException(e);}});
        SystemClock.sleep(6500);Engine engine=(Engine)field("engine");check(engine.perfect==3,"native playback and automatic judgement");
        runOnMainSync(()->activity.selectDemo());
        result.putString("stream","PASS: "+checks+" desktop-package -> Android import checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);finish(Activity.RESULT_CANCELED,result);}}
}
