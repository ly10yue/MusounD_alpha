import studio.interval.Engine;
import java.util.*;

public class TrackTest {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static Engine.Track track(){return new Engine.Track("a","A",new Engine.TrackKey(0,.2,0,"linear"),new Engine.TrackKey(2,.2,1,"linear"),new Engine.TrackKey(4,.8,1,"linear"));}
    static Engine engine(){return new Engine(new ArrayList<>(Arrays.asList(new Engine.Note(0,2,track(),2))),Arrays.asList(new Engine.Section(0,10,"",new Engine.Zone(0,0,1,1))));}
    public static void main(String[] args){
        Engine.Track t=track();check(Math.abs(t.pathX(4,4)-.8)<1e-6,"bottom position");check(Math.abs(t.pathX(1,3)-.5)<1e-6,"middle bend from absolute time");check(Math.abs(t.pathX(1,2)-.2)<1e-6,"old segment position");check(t.pathX(2,3)==t.pathX(1,3),"line points fall without x drift");
        check(t.samples(1,1,5,.13).stream().anyMatch(v->Math.abs(v[0]-4)<1e-9),"exact corner included");check(Math.abs(t.pose(1)[1]-.5)<1e-8,"opacity interpolation");
        List<double[]> earlier=t.samples(.5,1,5,.13),later=t.samples(1.125,1,5,.13);check(earlier.size()==later.size(),"same absolute samples across scroll");
        for(int i=0;i<earlier.size();i++)check(Arrays.equals(earlier.get(i),later.get(i)),"Hold/line geometry retains x while y falls "+i);
        Engine e=engine();e.down(1,.2f,.3f,2);for(double at=2;at<4.02;at+=.01)e.move(1,t.x(at),.3f,at);check(e.perfect==1&&e.miss==0,"bent hold follows its intersection with the fixed line");
        e=engine();e.down(1,.2f,.3f,2);for(double at=2;at<4.02;at+=.01)e.update(at);check(e.miss==1,"leaving bent hold intersection breaks it");
        Engine.Track fast=new Engine.Track("fast","",new Engine.TrackKey(1,.2,1,"linear"),new Engine.TrackKey(1.1,.8,1,"linear"));
        e=new Engine(new ArrayList<>(Arrays.asList(new Engine.Note(4,1,fast,0))),Arrays.asList(new Engine.Section(0,5,"",new Engine.Zone(0,0,1,1))));e.down(1,.2f,.5f,1.05);check(e.perfect==1,"late tap uses its own head x rather than current line x");
        for(int fps:new int[]{30,60,120}){e=engine();for(double at=0;at<4.2;at+=1./fps)e.autoplay(at);check(e.perfect==1,"moving autoplay "+fps);}
        e=engine();e.autoplay(2.5);e.suspend();e.autoplay(2.5);for(double at=2.51;at<4.2;at+=.01)e.autoplay(at);check(e.perfect==1,"pause reattach moving hold");
        Engine.Track hidden=new Engine.Track("hidden","",new Engine.TrackKey(0,.2,0,"linear"));e=new Engine(new ArrayList<>(Arrays.asList(new Engine.Note(1,1,hidden,0))),Arrays.asList(new Engine.Section(0,5,"",new Engine.Zone(0,0,1,1))));e.down(1,.2f,.5f,1);check(e.perfect==1,"opacity does not disable input");
        System.out.println("PASS: "+checks+" vertical line/Hold bends and fixed Tap judgement checks");
    }
}
