package studio.interval.tests;

import android.app.*;import android.content.*;import android.os.*;import android.media.*;import android.graphics.*;
import java.io.*;import java.lang.reflect.*;import java.util.*;import studio.interval.*;

/** Full exported AT arrangement, primary-region reachability and actual SoundPool voices. */
public final class FlowSmoke extends Instrumentation {
    MainActivity activity;GameView game;int checks;String aspect;
    Object get(String name){try{Field f=GameView.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}catch(Exception ex){throw new RuntimeException(ex);}}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Condition {boolean yes();}
    void until(Condition condition,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] result={false};runOnMainSync(()->result[0]=condition.yes());if(result[0])return;SystemClock.sleep(10);}throw new AssertionError("Timed out: ready="+get("ready")+", state="+get("state")+", error="+get("error")+", SFX="+((HitSounds)get("hitSounds")).error);}
    @Override public void onCreate(Bundle args){super.onCreate(args);aspect=args.getString("aspect","16x9");start();}
    @Override public void onStart(){Bundle result=new Bundle();File imported=null;try{
        SongLibrary library=SongLibrary.scan(getTargetContext());SongLibrary.Song flow=library.songs.stream().filter(s->s.title.contains("AT Flow")).findFirst().get();
        check(flow.notes==711&&flow.holds==25,"freeform AT arrangement appears in the ordinary song library");
        try(InputStream in=getContext().getAssets().open("at-flow.interval")){imported=ChartPackage.importFile(getTargetContext(),in);}
        Chart chart=new Chart(getTargetContext(),imported);check(chart.engine.notes.size()==flow.notes&&chart.engine.lines.size()==10,"desktop export and bundled chart agree");
        for(double width:new double[]{(1066.6666667-96)/600,(800.-96)/600}){
            Engine engine=chart.engine;engine.fieldAspect=width;
            for(Engine.Note note:engine.notes){
                int count=Math.max(1,(int)Math.ceil(note.duration*100));
                for(int j=0;j<=count;j++){
                    double t=note.time+note.duration*j/count;Engine.Section section=engine.sectionAt(t);boolean reachable=false;
                    for(int k=0;k<4;k++)if(section.zones[k].touchY(note.hold()?note.line.x(t):note.line.x(note.time),t,width)!=null)reachable=true;
                    check(reachable,"the primary-window union supports every note and Hold without decorative windows");
                }
            }
            for(int fps:new int[]{30,60,120}){
                engine.reset();for(double t=0;t<chart.duration+1;t+=1./fps)engine.autoplay(t);engine.finishAll();
                check(engine.perfect==711&&engine.miss==0&&engine.score()==1000000,"whole AT chart full-combos at both ratios / all frame rates");
            }
            for(int i=1;i<engine.sections.size();i++){
                Engine.Section a=engine.sections.get(i-1),b=engine.sections.get(i);
                for(int k=0;k<4;k++){float[] p=a.zones[k].polygon(a.end,width),q=b.zones[k].polygon(b.start,width);check(Arrays.equals(p,q),"every primary window has no jumps between phrases");}
            }
        }
        activity=(MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        File selected=imported;runOnMainSync(()->{activity.playSong(selected,true);try{Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Boolean)get("ready")&&((HitSounds)get("hitSounds")).ready(),10000);
        until(()->(Integer)get("state")==1&&(Double)get("time")>5,11000);
        runOnMainSync(()->{HitSounds sound=(HitSounds)get("hitSounds");check(sound.playedCount==1,"first real Hold head plays exactly once");game.pause();});
        int before=((HitSounds)get("hitSounds")).playedCount;SystemClock.sleep(180);check(((HitSounds)get("hitSounds")).playedCount==before,"pause does not create new strikes");
        runOnMainSync(()->{
            Engine engine=(Engine)get("engine");HitSounds sound=(HitSounds)get("hitSounds");engine.notes.clear();engine.sections.clear();
            engine.sections.add(new Engine.Section(0,10,"test",new Engine.Zone(0,0,1,1)));
            engine.notes.add(new Engine.Note(0,1,.2f,0));engine.notes.add(new Engine.Note(1,2,.6f,1));engine.notes.add(new Engine.Note(2,4,.8f,0));engine.reset();
            sound.enabled=true;int baseline=sound.playedCount;
            engine.down(1,.2f,.8f,1);engine.up(1,1.01);engine.down(2,.6f,.8f,2);engine.move(2,.6f,.8f,3);engine.down(3,.8f,.8f,4.14);
            check(sound.playedCount==baseline+2,"successful Tap and Hold head sound; no duplicate tail or MISS sound");
            sound.enabled=false;engine.reset();engine.down(1,.2f,.8f,1);check(sound.playedCount==baseline+2,"muted judgement is silent");sound.enabled=true;
        });
        // Screenshot the real arrangement at a moving-region / line-opacity phrase.
        runOnMainSync(()->activity.playSong(selected,true));until(()->(Boolean)get("ready"),10000);
        // Reload updated GameView reference after replacing the song.
        runOnMainSync(()->{try{Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Boolean)get("ready"),10000);
        until(()->(Integer)get("state")==1,6000);
        runOnMainSync(()->{((HitSounds)get("hitSounds")).enabled=false;((MediaPlayer)get("player")).seekTo(75000);});
        until(()->(Double)get("time")>74.8,6000);
        SystemClock.sleep(500);Bitmap image=getUiAutomation().takeScreenshot();
        try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"at-flow-"+aspect+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        result.putString("stream","PASS: "+checks+" AT chart / continuity / reachability / SoundPool checks at "+aspect);
    }catch(Throwable ex){StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);}
    finally{if(activity!=null)runOnMainSync(()->activity.finish());if(imported!=null){File folder=imported.getParentFile();for(File file:folder.listFiles())file.delete();folder.delete();}}
    finish(result.getString("stream","").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
