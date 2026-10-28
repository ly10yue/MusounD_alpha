package studio.interval.tests;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.graphics.*;import android.media.*;
import java.lang.reflect.*;import java.io.*;import studio.interval.*;

/** Device-side smoke tests, kept out of the application APK. */
public class Smoke extends Instrumentation {
    Activity activity;GameView game;int checks=0;String suffix;
    Object get(String name){try{Field f=GameView.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}catch(Exception e){throw new RuntimeException(e);}}
    void call(String name,Class<?>[] types,Object...args){try{Method m=GameView.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(game,args);}catch(Exception e){throw new RuntimeException(e);}}
    void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    interface Condition { boolean yes(); }
    void until(Condition condition,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};runOnMainSync(()->ok[0]=condition.yes());if(ok[0])return;SystemClock.sleep(10);}throw new AssertionError("Timed out: state="+get("state")+", time="+get("time")+", error="+get("error"));}
    void screenshot(String name)throws Exception{SystemClock.sleep(180);Bitmap bitmap=getUiAutomation().takeScreenshot();File file=new File(getTargetContext().getExternalFilesDir(null),name+"-"+suffix+".png");try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    void touch(float logicalX,float logicalY,int action){runOnMainSync(()->{float scale=(Float)get("scale"),ix=(Float)get("insetX"),iy=(Float)get("insetY");long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,ix+logicalX*scale,iy+logicalY*scale,0);game.onTouchEvent(event);event.recycle();});}
    void tap(float x,float y){touch(x,y,MotionEvent.ACTION_DOWN);touch(x,y,MotionEvent.ACTION_UP);}
    double time(){return (Double)get("time");}
    void seek(double t)throws Exception{
        runOnMainSync(()->((MediaPlayer)get("player")).seekTo((long)(t*1000),MediaPlayer.SEEK_CLOSEST));
        until(()->Math.abs(time()-t)<.3,5000);
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);suffix=arguments.getString("aspect","16x9");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Intent intent=new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=startActivitySync(intent);waitForIdleSync();runOnMainSync(()->{try{Method load=MainActivity.class.getDeclaredMethod("load",File.class);load.setAccessible(true);load.invoke(activity,(File)null);Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception e){throw new RuntimeException(e);}});
        until(()->(Boolean)get("ready"),10000);check(((String)get("error")).isEmpty(),"audio prepared");
        screenshot("loading");float width=(Float)get("w");check(width>=799&&width<=1067,"supported aspect");
        runOnMainSync(()->game.showUtility(true));SystemClock.sleep(100);
        for(int i=0;i<22;i++)tap(width-302,209);
        runOnMainSync(()->{
            check(Math.abs((Double)get("approach")-.6)<1e-6,"fastest approach is 0.6s and cannot go lower");
            check(Math.abs(getTargetContext().getSharedPreferences("interval",Context.MODE_PRIVATE).getFloat("approach",0)-.6)<1e-6,"0.6s is saved for the next session");
        });
        tap(width-76,209);runOnMainSync(()->check(Math.abs((Double)get("approach")-.8)<1e-6,"speed step remains 0.2s"));
        tap(width-302,209);screenshot("speed-06");
        // Gameplay regression: menu button hit testing has its own NavigationSmoke suite.
        runOnMainSync(()->call("start",new Class[]{boolean.class},false));until(()->(Integer)get("state")==1,6000);
        Engine engine=(Engine)get("engine");Engine.Note first=engine.notes.stream().filter(n->!n.hold()).findFirst().get();
        until(()->time()>=first.time-.015,8000);tap(48+first.hitX(time())*(width-96),180);
        runOnMainSync(()->check(first.state==Engine.DONE,"touch far above line judges"));
        screenshot("play");
        tap(width-70,40);until(()->(Integer)get("state")==2,1000);
        double paused=time();SystemClock.sleep(250);check(Math.abs(time()-paused)<.001,"pause freezes chart");check(!((MediaPlayer)get("player")).isPlaying(),"pause freezes audio");screenshot("pause");
        tap(width/2,300);until(()->(Integer)get("state")==1,6000);check(((MediaPlayer)get("player")).isPlaying(),"resume audio");
        seek(36.0);screenshot("lower-area");
        runOnMainSync(()->{check(!engine.allowed(.39f,.2f,time()),"upper blocked in lower section");check(engine.allowed(.39f,.85f,time()),"lower open");});
        seek(75.3);screenshot("upper-area");runOnMainSync(()->{check(engine.allowed(.39f,.25f,time()),"upper open");check(!engine.allowed(.39f,.85f,time()),"lower blocked");});
        seek(90);screenshot("split-area");runOnMainSync(()->check(!engine.allowed(.5f,.5f,time()),"split central gap blocked"));
        runOnMainSync(()->game.pause());runOnMainSync(()->call("start",new Class[]{boolean.class},true));until(()->(Integer)get("state")==1,6000);until(()->time()>5.3,8000);
        runOnMainSync(()->check(engine.perfect>=2,"autoplay produces real judgements"));
        runOnMainSync(()->game.pause());runOnMainSync(()->game.showUtility(true));screenshot("settings");
        runOnMainSync(()->game.showUtility(false));screenshot("help");
        runOnMainSync(()->{engine.reset();engine.autoplay(0);for(double t=0;t<153;t+=1.0/60)engine.autoplay(t);call("complete",new Class[]{});});screenshot("result");check(engine.perfect==622,"complete full chart");
        result.putString("stream","PASS: "+checks+" Android runtime checks at "+suffix+"\nScreenshots: "+getTargetContext().getExternalFilesDir(null));finish(Activity.RESULT_OK,result);
    }catch(Throwable ex){StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);finish(Activity.RESULT_CANCELED,result);}}
}
