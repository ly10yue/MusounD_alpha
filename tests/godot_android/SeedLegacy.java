package studio.interval.godot.tests;
import android.app.*;import android.os.*;import android.content.*;import java.io.*;import org.json.*;
/** Runs inside the old application's UID, using its actual SharedPreferences and files API. */
public final class SeedLegacy extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context old=getTargetContext();new File(old.getFilesDir(),"profile.cfg").delete();File folder=new File(old.getFilesDir(),"charts/godot-migration-legacy");folder.mkdirs();
        byte[] chart;try(InputStream in=getContext().getAssets().open("demo.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);chart=out.toByteArray();}
        JSONObject json=new JSONObject(new String(chart,"UTF-8"));json.put("title","Legacy 0.12 migration");json.put("audio","music.ogg");
        try(OutputStream out=new FileOutputStream(new File(folder,"chart.json"))){out.write(json.toString().getBytes("UTF-8"));}
        try(InputStream in=getContext().getAssets().open("music.ogg");OutputStream out=new FileOutputStream(new File(folder,"music.ogg"))){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        old.getSharedPreferences("interval",0).edit().putInt("best",333333).putInt("best_"+new File(folder,"chart.json").getAbsolutePath().hashCode(),444444).putFloat("approach",1.4f).putInt("offset",20).putBoolean("hitSounds",true).putBoolean("zoneGuide",true).commit();
        old.getSharedPreferences("MainActivity",0).edit().putString("chart",new File(folder,"chart.json").getAbsolutePath()).commit();
        result.putString("stream","PASS: legacy client preferences and library seeded");finish(Activity.RESULT_OK,result);
    }catch(Exception ex){result.putString("stream","FAIL: "+ex);finish(Activity.RESULT_CANCELED,result);}}
}
