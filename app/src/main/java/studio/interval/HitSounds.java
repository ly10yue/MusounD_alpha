package studio.interval;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.util.*;

/** Short, preloaded PCM voices. Music continues on its own MediaPlayer. */
public final class HitSounds {
    private final SoundPool pool;
    private final Set<Integer> ready=new HashSet<>();
    private final ArrayList<Integer> streams=new ArrayList<>();
    private int tap,hold;
    public boolean enabled=true;
    public float volume=.65f;
    public int playedCount;
    public String error="";
    public HitSounds(Context context){
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool=new SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attributes).build();
        pool.setOnLoadCompleteListener((p,id,status)->{if(status==0)ready.add(id);else error="SoundPool decode status "+status;});
        try{
            int tapResource=context.getResources().getIdentifier("hit_tap","raw",context.getPackageName());
            int holdResource=context.getResources().getIdentifier("hit_hold","raw",context.getPackageName());
            if(tapResource==0||holdResource==0)throw new IllegalStateException("Hit sound resources missing");
            tap=pool.load(context,tapResource,1);hold=pool.load(context,holdResource,1);
        }catch(Exception failure){error=failure.getClass().getSimpleName()+": "+failure.getMessage();}
    }
    public boolean ready(){return ready.contains(tap)&&ready.contains(hold);}
    public void play(boolean sustained){
        int id=sustained?hold:tap;if(!enabled||volume<=0||!ready.contains(id))return;
        int stream=pool.play(id,volume,volume,1,0,1);
        if(stream!=0){playedCount++;streams.add(stream);if(streams.size()>32)streams.remove(0);}
    }
    public void stop(){for(int id:streams)pool.stop(id);streams.clear();}
    public void release(){stop();pool.release();ready.clear();}
}
