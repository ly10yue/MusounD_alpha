package studio.interval;

import android.content.Context;
import java.io.File;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Lightweight song metadata; gameplay gets a fresh engine when a song starts. */
public final class SongLibrary {
    public static final class Song {
        public final File file;
        public final String id,title,artist,difficulty,fileVersion;
        public final double bpm,duration;
        public final int notes,holds,areas;
        Song(File file,Chart chart){
            this.file=file;fileVersion=chart.fileVersion;id=file==null?"":file.getAbsolutePath();
            title=chart.title;artist=chart.artist;difficulty=chart.difficulty;bpm=chart.bpm;duration=chart.duration;
            notes=chart.engine.notes.size();int count=0;for(Engine.Note n:chart.engine.notes)if(n.hold())count++;holds=count;
            areas=chart.engine.sections.size();
        }
        public String bestKey(){return file==null?"best":"best_"+id.hashCode();}
    }
    public final List<Song> songs=new ArrayList<>();
    public int unavailable;
    public static SongLibrary scan(Context context){
        SongLibrary result=new SongLibrary();
        result.add(context,null);
        try{result.add(context,bundledFlow(context));}catch(Exception ex){result.unavailable++;}
        File[] folders=new File(context.getFilesDir(),"charts").listFiles(File::isDirectory);
        if(folders!=null){
            Arrays.sort(folders,Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));
            for(File folder:folders)result.add(context,new File(folder,"chart.json"));
        }
        return result;
    }
    private void add(Context context,File file){try{songs.add(new Song(file,new Chart(context,file)));}catch(Exception ex){unavailable++;}}
    private static File bundledFlow(Context context)throws Exception{
        File folder=new File(context.getFilesDir(),"bundled/at-flow");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot prepare bundled chart");
        String data;try(InputStream in=context.getAssets().open("at-flow.json")){data=Chart.read(in,4*1024*1024);}
        File chart=new File(folder,"chart.json");
        if(!chart.isFile()||!new String(Files.readAllBytes(chart.toPath()),StandardCharsets.UTF_8).equals(data)){
            File temporary=new File(folder,"chart.tmp");Files.write(temporary.toPath(),data.getBytes(StandardCharsets.UTF_8));
            if(!temporary.renameTo(chart))throw new IOException("Cannot update bundled chart");
        }
        File audio=new File(folder,"desultory-signals.ogg");
        if(!audio.isFile()){
            File temporary=new File(folder,"music.tmp");
            try(InputStream in=context.getAssets().open("desultory-signals.ogg");OutputStream out=new FileOutputStream(temporary)){byte[] bytes=new byte[32768];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);}
            if(!temporary.renameTo(audio))throw new IOException("Cannot prepare bundled music");
        }
        return chart;
    }
}
