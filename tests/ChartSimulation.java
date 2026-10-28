import studio.interval.Engine;
import java.nio.file.*;import java.util.*;
public class ChartSimulation {
    public static void main(String[] args)throws Exception{
        Map<String,Engine.Track> lines=new HashMap<>();
        List<Engine.Note> notes=new ArrayList<>();List<Engine.Section> sections=new ArrayList<>();
        for(String row:Files.readAllLines(Paths.get(args[0]))){String[] p=row.split(" ");if(p[0].equals("L")){Engine.TrackKey[] fs=new Engine.TrackKey[(p.length-2)/4];for(int k=0;k<fs.length;k++)fs[k]=new Engine.TrackKey(Double.parseDouble(p[2+k*4]),Double.parseDouble(p[3+k*4]),Double.parseDouble(p[4+k*4]),p[5+k*4]);lines.put(p[1],new Engine.Track(p[1],p[1],fs));}else if(p[0].equals("N"))notes.add(new Engine.Note(Integer.parseInt(p[1]),Double.parseDouble(p[2]),lines.get(p[3]),Double.parseDouble(p[4])));else{Engine.Zone[] zs=new Engine.Zone[(p.length-3)/4];for(int i=0;i<zs.length;i++)zs[i]=new Engine.Zone(Float.parseFloat(p[3+i*4]),Float.parseFloat(p[4+i*4]),Float.parseFloat(p[5+i*4]),Float.parseFloat(p[6+i*4]));sections.add(new Engine.Section(Double.parseDouble(p[1]),Double.parseDouble(p[2]),"",zs));}}
        for(int fps:new int[]{30,60,120}){Engine e=new Engine(notes,sections);for(double t=0;t<153;t+=1.0/fps)e.autoplay(t);e.finishAll();if(e.perfect!=622||e.miss!=0||e.score()!=1000000)throw new AssertionError("Autoplay failed at "+fps+"fps: "+e.perfect+" perfect, "+e.miss+" miss");System.out.println("PASS: full chart at "+fps+" fps: "+e.perfect+" perfect, 1,000,000 score");}
    }
}
