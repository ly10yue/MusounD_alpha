package studio.interval.tests;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.*;import java.io.*;import java.lang.reflect.*;import org.json.*;import studio.interval.*;

/** Desktop-authored v2 archive, cross-language geometry and real Android playback. */
public class MotionSmoke extends Instrumentation {
    MainActivity activity;GameView game;int checks;String aspect;File imported;
    Object get(String name){try{Field f=GameView.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}catch(Exception ex){throw new RuntimeException(ex);}}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Condition{boolean yes();}
    void until(Condition c,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};runOnMainSync(()->ok[0]=c.yes());if(ok[0])return;SystemClock.sleep(20);}throw new AssertionError("timeout "+get("error"));}
    void shot()throws Exception{Bitmap image=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"motion-"+aspect+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    @Override public void onCreate(Bundle args){super.onCreate(args);aspect=args.getString("aspect","16x9");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        try(InputStream in=getContext().getAssets().open("motion.interval")){imported=ChartPackage.importFile(getTargetContext(),in);}
        Chart chart=new Chart(getTargetContext(),imported);Engine.Zone zone=chart.engine.sections.get(0).zones[0];check(zone.frames.length==2,"desktop keyframes parsed");
        check(Math.abs(zone.pose(2)[2]-22.5)<1e-7,"beat to seconds and smooth rotation");
        JSONArray parity;try(InputStream in=getContext().getAssets().open("motion-parity.json")){parity=new JSONArray(Chart.read(in,1024*1024));}
        for(int i=0;i<parity.length();i++){
            JSONObject sample=parity.getJSONObject(i);double t=sample.getDouble("time"),a=sample.getDouble("aspect");float[] polygon=zone.polygon(t,a);JSONArray expected=sample.getJSONArray("polygon");
            for(int k=0;k<8;k++)check(Math.abs(polygon[k]-expected.getDouble(k))<.000002,"Python/Java rendered corners agree");
            JSONArray touches=sample.getJSONArray("touches");for(int k=0;k<touches.length();k++){JSONObject hit=touches.getJSONObject(k);float x=(float)hit.getDouble("x");Float y=zone.touchY(x,t,a);
                if(hit.isNull("y"))check(y==null,"unreachable column parity");else {check(y!=null&&Math.abs(y-hit.getDouble("y"))<.000002,"auto touch parity");check(zone.contains(x,y,t,a),"point inside rotated polygon");}
            }
        }
        for(double a:new double[]{(1066.6666667-96)/600,(800.-96)/600}){chart.engine.reset();chart.engine.fieldAspect=a;for(double t=0;t<6;t+=1./120)chart.engine.autoplay(t);check(chart.engine.perfect==3&&chart.engine.miss==0,"moving hold and taps complete in both aspects");}
        File demo;
        try(InputStream in=getContext().getAssets().open("motion-demo.interval")){demo=ChartPackage.importFile(getTargetContext(),in);}
        try{
            Chart full=new Chart(getTargetContext(),demo);for(double a:new double[]{(1066.6666667-96)/600,(800.-96)/600}){full.engine.reset();full.engine.fieldAspect=a;for(double t=0;t<full.duration+1;t+=1./60)full.engine.autoplay(t);check(full.engine.perfect==622&&full.engine.miss==0,"full moving-area sample playable");}
        }finally{File folder=demo.getParentFile();for(File file:folder.listFiles())file.delete();folder.delete();}
        // A malformed version or easing cannot silently become a static region.
        JSONObject original;try(InputStream in=new FileInputStream(imported)){original=new JSONObject(Chart.read(in,4*1024*1024));}
        JSONObject bad=new JSONObject(original.toString());bad.put("version",1);write(bad);boolean rejected=false;try{new Chart(getTargetContext(),imported);}catch(Exception ex){rejected=true;}check(rejected,"v1 motion rejected");
        bad=new JSONObject(original.toString());bad.getJSONArray("sections").getJSONObject(0).getJSONArray("motions").getJSONArray(0).getJSONObject(0).put("ease","bad");write(bad);rejected=false;try{new Chart(getTargetContext(),imported);}catch(Exception ex){rejected=true;}check(rejected,"invalid easing rejected");write(original);
        activity=(MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{activity.playSong(imported,true);try{Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Integer)get("state")==1,8000);until(()->(Double)get("time")>=2.2,5000);shot();
        runOnMainSync(()->game.pause());double frozen=(Double)get("time");SystemClock.sleep(200);check((Double)get("time")==frozen,"pause freezes motion clock");
        runOnMainSync(()->{try{Method m=GameView.class.getDeclaredMethod("resume");m.setAccessible(true);m.invoke(game);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Integer)get("state")==1,6000);until(()->(Double)get("time")>4.3,5000);check(((Engine)get("engine")).perfect==3,"real native playback, pause, moving hold reattachment");
        result.putString("stream","PASS: "+checks+" v2 motion / geometry parity / native playback checks at "+aspect);
    }catch(Throwable ex){StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);}
    finally{if(activity!=null)runOnMainSync(()->activity.finish());if(imported!=null){File folder=imported.getParentFile();for(File file:folder.listFiles())file.delete();folder.delete();}}
    finish(result.getString("stream","").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    void write(JSONObject data)throws Exception{try(Writer writer=new OutputStreamWriter(new FileOutputStream(imported),"UTF-8")){writer.write(data.toString());}}
}
