package studio.interval.tests;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.graphics.*;import android.media.*;
import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;import studio.interval.*;

/** Real menu touches, pagination, persistence and playback return paths on device. */
public final class NavigationSmoke extends Instrumentation {
    private MainActivity activity;private int checks;private String aspect;
    private final List<File> fixtures=new ArrayList<>();
    private Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception ex){throw new RuntimeException(ex);}}
    private void invoke(Object object,String name,Class<?>[] types,Object...args){try{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(object,args);}catch(Exception ex){throw new RuntimeException(ex);}}
    private void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private MenuView menu(){return (MenuView)field(activity,"menu");}
    private GameView game(){return (GameView)field(activity,"game");}
    interface Condition{boolean yes();}
    private void until(Condition condition,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] yes={false};runOnMainSync(()->yes[0]=condition.yes());if(yes[0])return;SystemClock.sleep(20);}throw new AssertionError("Timed out");}
    private void tap(View view,float x,float y){SystemClock.sleep(120);runOnMainSync(()->{float scale=(Float)field(view,"scale"),ix=(Float)field(view,"insetX"),iy=(Float)field(view,"insetY");long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(now,now,action,ix+x*scale,iy+y*scale,0);view.onTouchEvent(event);event.recycle();}});waitForIdleSync();SystemClock.sleep(100);}
    private void shot(String name)throws Exception{SystemClock.sleep(200);Bitmap image=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+"-"+aspect+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private MainActivity open(){return (MainActivity)startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("studio.interval","studio.interval.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @Override public void onCreate(Bundle args){super.onCreate(args);aspect=args.getString("aspect","16x9");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        // Several independent packages ensure pagination and retained imports are exercised.
        for(int i=0;i<5;i++){
            File file;try(InputStream in=getContext().getAssets().open("roundtrip.interval")){file=ChartPackage.importFile(getTargetContext(),in);}fixtures.add(file);
            JSONObject json;try(InputStream in=new FileInputStream(file)){json=new JSONObject(Chart.read(in,4*1024*1024));}
            json.put("title",i==4?"长标题测试 / A very long song title that should fit the song selection card safely":"Navigation test "+(i+1));
            json.put("artist","制谱器导入 / Test artist");try(Writer out=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){out.write(json.toString());}
        }
        activity=open();waitForIdleSync();until(()->!(Boolean)field(menu(),"loading"),10000);
        check(game()==null,"cold start does not allocate music player");check(!menu().isSongsPage(),"cold start shows title");shot("title");
        tap(menu(),100,482);check(game()!=null&&(Integer)field(game(),"state")==5,"title opens help");
        runOnMainSync(()->activity.onBackPressed());check(game()==null&&!menu().isSongsPage(),"help back returns to title");
        tap(menu(),260,482);check((Integer)field(game(),"state")==6,"title opens settings");
        tap(game(),100,538);check(game()==null&&!menu().isSongsPage(),"settings button returns to title");
        tap(menu(),180,416);check(menu().isSongsPage(),"enter opens song list");
        SongLibrary library=(SongLibrary)field(menu(),"library");check(library.songs.size()>=6,"all imported charts retained with built-in");
        // The file picker may be cancelled without losing the song list.
        float menuWidth=(Float)field(menu(),"w");tap(menu(),menuWidth-130,44);SystemClock.sleep(600);
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);SystemClock.sleep(500);
        check(menu().isSongsPage()&&game()==null,"cancel import keeps song list");
        // Deliver a real portable file through the Activity's picker result path.
        File portable=new File(getTargetContext().getCacheDir(),"navigation.interval");
        try(InputStream in=getContext().getAssets().open("roundtrip.interval");OutputStream out=new FileOutputStream(portable)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
        int previousCount=library.songs.size();
        runOnMainSync(()->invoke(activity,"onActivityResult",new Class[]{int.class,int.class,Intent.class},91,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(portable))));
        until(()->((SongLibrary)field(menu(),"library")).songs.size()==previousCount+1,10000);
        fixtures.add(new File(menu().selectedId()));portable.delete();
        check(menu().isSongsPage()&&!menu().selectedId().isEmpty(),"import selects new track in song list");
        library=(SongLibrary)field(menu(),"library");
        float w=(Float)field(menu(),"w"),lw=(w-120)*.48f,dx=48+lw+24,dw=w-dx-48;
        // Choose the built-in track first, independent of last selection from previous runs.
        SongLibrary currentLibrary=library;runOnMainSync(()->menu().setLibrary(currentLibrary,""));waitForIdleSync();tap(menu(),100,170);
        check(menu().selectedId().isEmpty(),"built-in selected");shot("songs");
        tap(menu(),48+lw-30,516);check((Integer)field(menu(),"page")==1,"next page works");tap(menu(),100,170);
        String id=menu().selectedId();check(!id.isEmpty(),"imported track selected on second page");
        tap(menu(),80,516);check((Integer)field(menu(),"page")==0,"previous page works");
        tap(menu(),100,257);String longId=menu().selectedId();check(!longId.isEmpty(),"imported selection persists separately from page");shot("songs-imported");
        tap(menu(),dx+dw*.75f,540);check((Integer)field(game(),"state")==6,"song page opens settings");
        runOnMainSync(()->activity.onBackPressed());check(menu().isSongsPage()&&menu().selectedId().equals(longId),"settings preserves song selection");
        tap(menu(),dx+dw/2,487);until(()->game()!=null&&(Integer)field(game(),"state")==1,8000);
        check(((MediaPlayer)field(game(),"player")).isPlaying(),"selected song starts playback");
        check(((Chart)field(game(),"chart")).audioFile!=null,"selected imported music used");
        runOnMainSync(()->activity.onBackPressed());check((Integer)field(game(),"state")==2,"back during play pauses");
        GameView old=game();float gw=(Float)field(old,"w");tap(old,gw/2+100,359);
        check(game()==null&&menu().isSongsPage(),"pause returns to song list");check((Boolean)field(old,"disposed"),"return releases audio resources");check(menu().selectedId().equals(longId),"pause preserves selected song");
        tap(menu(),dx+dw*.25f,540);until(()->game()!=null&&(Integer)field(game(),"state")==1,8000);check((Boolean)field(game(),"auto"),"automatic demo selected");
        GameView demo=game();runOnMainSync(()->{Engine engine=(Engine)field(demo,"engine");engine.autoplay(999);invoke(demo,"complete",new Class[]{});});
        check((Integer)field(demo,"state")==3,"result screen reached");tap(demo,120,543);check(game()==null&&menu().isSongsPage(),"result returns to song list");
        runOnMainSync(()->activity.onBackPressed());check(!menu().isSongsPage(),"song list back returns to title");
        runOnMainSync(()->activity.finish());waitForIdleSync();activity=open();until(()->!(Boolean)field(menu(),"loading"),10000);
        check(!menu().isSongsPage(),"relaunch opens initial screen");check(menu().selectedId().equals(longId),"last chosen song restored after relaunch");
        result.putString("stream","PASS: "+checks+" title / song library / navigation checks at "+aspect);
    }catch(Throwable ex){StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);}
    finally{
        if(activity!=null)runOnMainSync(()->activity.finish());
        for(File file:fixtures){File folder=file.getParentFile();File[] files=folder.listFiles();if(files!=null)for(File child:files)child.delete();folder.delete();}
    }
    finish(result.getString("stream","").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
