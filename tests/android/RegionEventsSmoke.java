package studio.interval.tests;
import android.graphics.*;import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.lang.reflect.*;import org.json.*;import studio.interval.*;

/** The actual desktop v5 export through Android's parser, hit testing and playback. */
public class RegionEventsSmoke extends MotionSmoke {
    void rejects(JSONObject bad,String message)throws Exception{write(bad);boolean failed=false;try{new Chart(getTargetContext(),imported);}catch(Exception ex){failed=true;}check(failed,message);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        try(InputStream in=getContext().getAssets().open("regions.interval")){imported=ChartPackage.importFile(getTargetContext(),in);}
        Chart chart=new Chart(getTargetContext(),imported);Engine.Zone zone=chart.engine.sections.get(0).zones[0];
        check(zone.centerX!=null&&zone.centerY!=null&&zone.angleCurve!=null&&zone.widthCurve!=null&&zone.heightCurve!=null,"all four region channels parsed");
        File legacy;try(InputStream in=getContext().getAssets().open("regions-011.interval")){legacy=ChartPackage.importFile(getTargetContext(),in);}
        Chart oldChart=new Chart(getTargetContext(),legacy);Engine.Zone oldZone=oldChart.engine.sections.get(0).zones[0];
        for(double t:new double[]{0,.5,1,1.5,2,3})for(double a:new double[]{1.1733333333,1.6177777778}){
            float[] x=zone.polygon(t,a),y=oldZone.polygon(t,a);for(int k=0;k<8;k++)check(Math.abs(x[k]-y[k])<.000002,"0.11.0 and 0.12.0 geometry identical");
        }
        for(File file:legacy.getParentFile().listFiles())file.delete();legacy.getParentFile().delete();
        Bitmap bitmap=Bitmap.createBitmap(301,201,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);
        Engine.Section overlap=new Engine.Section(0,4,"union",new Engine.Zone(.1f,.2f,.6f,.7f),new Engine.Zone(.4f,.2f,.9f,.7f));
        Paint stroke=new Paint();stroke.setColor(Color.WHITE);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2);canvas.drawPath(RegionOutline.path(overlap,0,1.5,0,300,200),stroke);
        check(bitmap.getPixel(120,80)==Color.BLACK,"interior overlap edge hidden");check(bitmap.getPixel(30,80)==Color.WHITE,"outer edge retained");bitmap.recycle();
        JSONArray parity;try(InputStream in=getContext().getAssets().open("regions-parity.json")){parity=new JSONArray(Chart.read(in,1024*1024));}
        for(int i=0;i<parity.length();i++){
            JSONObject sample=parity.getJSONObject(i);double time=sample.getDouble("time"),aspect=sample.getDouble("aspect");float[] actual=zone.polygon(time,aspect);JSONArray expected=sample.getJSONArray("points");
            for(int k=0;k<8;k++)check(Math.abs(actual[k]-expected.getJSONArray(k/2).getDouble(k%2))<.000002,"Python/Java animated rectangle corners agree");
            double[] g=zone.geometry(time);check(zone.contains((float)g[0],(float)g[1],time,aspect),"animated center is hittable");
            check(!zone.contains((float)g[0]+4,(float)g[1],time,aspect),"outside transformed box is rejected");
            Float y=zone.touchY((float)g[0],time,aspect);check(y!=null&&zone.contains((float)g[0],y,time,aspect),"autoplay touch is inside current box");
        }
        for(double aspect:new double[]{1.1733333333,1.6177777778})for(int fps:new int[]{30,60,120}){
            chart.engine.reset();chart.engine.fieldAspect=aspect;for(double t=0;t<4;t+=1./fps)chart.engine.autoplay(t);check(chart.engine.perfect==2&&chart.engine.miss==0,"Tap and Hold in resizing region at both aspects and all frame rates");
        }
        JSONObject original;try(InputStream in=new FileInputStream(imported)){original=new JSONObject(Chart.read(in,4*1024*1024));}
        JSONObject bad=new JSONObject(original.toString());bad.put("version",7);rejects(bad,"old version cannot silently drop region events");
        bad=new JSONObject(original.toString());bad.getJSONArray("sections").getJSONObject(0).getJSONArray("events").getJSONObject(0).getJSONArray("width").getJSONObject(0).put("to",0);rejects(bad,"zero size rejected");
        bad=new JSONObject(original.toString());JSONArray events=bad.getJSONArray("sections").getJSONObject(0).getJSONArray("events").getJSONObject(0).getJSONArray("angle");events.put(new JSONObject(events.getJSONObject(0).toString()));rejects(bad,"overlap rejected");write(original);
        activity=(MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{activity.playSong(imported,true);try{Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);game=(GameView)f.get(activity);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Integer)get("state")==1,8000);until(()->(Double)get("time")>=1.1,5000);shot();
        runOnMainSync(()->game.pause());double frozen=(Double)get("time");SystemClock.sleep(200);check((Double)get("time")==frozen,"pause freezes region timeline");
        runOnMainSync(()->{try{Method m=GameView.class.getDeclaredMethod("resume");m.setAccessible(true);m.invoke(game);}catch(Exception ex){throw new RuntimeException(ex);}});
        until(()->(Integer)get("state")==1,6000);until(()->(Double)get("time")>2.1,5000);check(((Engine)get("engine")).perfect==2,"native playback and Hold reattachment");
        result.putString("stream","PASS: "+checks+" v6 migration / union / region event / geometry / native playback checks at "+aspect);
    }catch(Throwable ex){StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);}
    finally{if(activity!=null)runOnMainSync(()->activity.finish());if(imported!=null){File folder=imported.getParentFile();for(File file:folder.listFiles())file.delete();folder.delete();}}
    finish(result.getString("stream","").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
