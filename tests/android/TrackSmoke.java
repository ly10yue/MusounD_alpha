package studio.interval.tests;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.*;import java.io.*;import java.lang.reflect.*;import org.json.*;import studio.interval.*;

public class TrackSmoke extends Instrumentation {
    MainActivity activity;GameView game;int checks;String aspect;File imported;
    Object get(String name){try{Field f=GameView.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}catch(Exception ex){throw new RuntimeException(ex);}}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Condition{boolean yes();}
    void until(Condition c,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};runOnMainSync(()->ok[0]=c.yes());if(ok[0])return;SystemClock.sleep(20);}throw new AssertionError("timeout "+get("error"));}
    void shot()throws Exception{Bitmap image=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"tracks-"+aspect+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    void write(JSONObject data)throws Exception{try(Writer writer=new OutputStreamWriter(new FileOutputStream(imported),"UTF-8")){writer.write(data.toString());}}
    void rejects(JSONObject bad,String message)throws Exception{write(bad);boolean rejected=false;try{new Chart(getTargetContext(),imported);}catch(Exception ex){rejected=true;}check(rejected,message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);aspect=args.getString("aspect","16x9");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        try(InputStream in=getContext().getAssets().open("tracks.interval")){imported=ChartPackage.importFile(getTargetContext(),in);}
        Chart chart=new Chart(getTargetContext(),imported);check(chart.engine.lines.size()==4,"four desktop trajectories parsed");
        check(chart.engine.notes.get(1).line==chart.engine.notes.get(2).line,"notes share one track object");
        JSONArray parity;try(InputStream in=getContext().getAssets().open("tracks-parity.json")){parity=new JSONArray(Chart.read(in,1024*1024));}
        for(int i=0;i<parity.length();i++){
            JSONObject sample=parity.getJSONObject(i);Engine.Track track=chart.engine.lines.stream().filter(l->l.id.equals(sample.optString("line"))).findFirst().get();double now=sample.getDouble("now");
            check(Math.abs(track.pathX(now,sample.getDouble("arrival"))-sample.getDouble("x"))<.000002,"Python/Java vertically scrolling line shapes agree");
            check(Math.abs(track.pose(now)[1]-sample.getDouble("alpha"))<.000002,"Python/Java opacity agrees");
            if(sample.has("speed"))check(Math.abs(track.speed.value(now)-sample.getDouble("speed"))<.000002,"Python/Java speed curves agree");
            if(sample.has("distance"))check(Math.abs(track.distance(now,sample.getDouble("arrival"))-sample.getDouble("distance"))<.000002,"Python/Java integrated vertical distance agrees");
        }
        Chart full=new Chart(getTargetContext());
        for(double a:new double[]{(1066.6666667-96)/600,(800.-96)/600})for(int fps:new int[]{30,60,120}){
            full.engine.reset();full.engine.fieldAspect=a;for(double t=0;t<full.duration+1;t+=1./fps)full.engine.autoplay(t);check(full.engine.perfect==622&&full.engine.miss==0,"full v3 sample at both aspects/all frame rates");
        }
        JSONObject original;try(InputStream in=new FileInputStream(imported)){original=new JSONObject(Chart.read(in,4*1024*1024));}
        JSONObject bad=new JSONObject(original.toString());bad.getJSONArray("notes").getJSONObject(0).put("line","missing");rejects(bad,"unknown binding rejected");
        bad=new JSONObject(original.toString());bad.getJSONArray("lines").getJSONObject(0).getJSONObject("events").getJSONArray("speed").getJSONObject(0).put("to",9);rejects(bad,"excessive speed rejected");
        bad=new JSONObject(original.toString());bad.getJSONArray("lines").getJSONObject(0).getJSONObject("events").getJSONArray("x").getJSONObject(1).put("start",.5);rejects(bad,"overlapping parameter intervals rejected");
        bad=new JSONObject(original.toString());bad.put("version",3);rejects(bad,"old reader cannot silently discard v4 speed events");
        bad=new JSONObject(original.toString());bad.getJSONArray("lines").getJSONObject(1).put("id","line1");rejects(bad,"duplicate id rejected");
        bad=new JSONObject(original.toString());bad.getJSONArray("lines").getJSONObject(0).put("frames",new JSONArray());rejects(bad,"empty track rejected");
        bad=new JSONObject(original.toString());bad.getJSONArray("lines").getJSONObject(0).getJSONArray("frames").getJSONObject(0).put("alpha",2);rejects(bad,"bad opacity rejected");
        bad=new JSONObject(original.toString());bad.getJSONArray("lines").getJSONObject(0).getJSONArray("frames").getJSONObject(1).put("time",0);rejects(bad,"duplicate key time rejected");
        for(int version:new int[]{1,2}){
            JSONObject legacy=new JSONObject(original.toString());Release.normalize(legacy);legacy.remove("coordinateScale");legacy.put("version",version);legacy.remove("lines");JSONArray notes=legacy.getJSONArray("notes");
            for(int i=0;i<notes.length();i++){notes.getJSONObject(i).remove("line");notes.getJSONObject(i).put("x",i==0?.82:.2);}
            write(legacy);Chart old=new Chart(getTargetContext(),imported);old.engine.autoplay(0);for(double t=0;t<7;t+=1./60)old.engine.autoplay(t);check(old.engine.perfect==3,"v"+version+" legacy migration still playable");
        }
        write(original);
        activity=(MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{activity.playSong(imported,true);try{Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Integer)get("state")==1,8000);until(()->(Double)get("time")>=2.75,5000);shot();
        runOnMainSync(()->game.pause());double frozen=(Double)get("time");SystemClock.sleep(200);check((Double)get("time")==frozen,"pause freezes trajectory");
        runOnMainSync(()->{try{Method m=GameView.class.getDeclaredMethod("resume");m.setAccessible(true);m.invoke(game);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Integer)get("state")==1,6000);until(()->(Double)get("time")>6.3,6000);check(((Engine)get("engine")).perfect==3&&((Engine)get("engine")).miss==0,"native audio, vertically falling bent hold and pause reattach");
        result.putString("stream","PASS: "+checks+" trajectory parser / parity / native playback checks at "+aspect);
    }catch(Throwable ex){StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);}
    finally{if(activity!=null)runOnMainSync(()->activity.finish());if(imported!=null){File folder=imported.getParentFile();for(File file:folder.listFiles())file.delete();folder.delete();}}
    finish(result.getString("stream","").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
