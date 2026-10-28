import studio.interval.Engine;
import java.util.*;

public final class EngineTest {
    static int checks=0;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Engine game(Engine.Note... notes){return new Engine(Arrays.asList(notes),Arrays.asList(new Engine.Section(0,10,"lower",new Engine.Zone(0,.5f,.45f,1),new Engine.Zone(.55f,.5f,1,1))));}
    static Engine.Note tap(int id,double t,float x){return new Engine.Note(id,t,x,0);}
    public static void main(String[] args){
        Engine e=game(tap(0,1,.2f));check(e.down(1,.2f,.1f,1)==null,"blocked upper half");check(e.notes.get(0).state==Engine.WAIT,"blocked tap must not consume note");e.down(2,.2f,.9f,1);check(e.perfect==1,"any allowed height judges");check(e.score()==1000000,"full score");
        e=game(tap(0,1,.5f));check(e.down(1,.5f,.8f,1)==null,"gap between rectangles blocked");e.update(1.161);check(e.miss==1,"miss timeout");
        e=game(tap(0,1,.2f));e.down(1,.8f,.8f,1);check(e.finished==0,"must align horizontally");e.down(2,.2f,.8f,1.10);check(e.good==1&&e.score()==650000,"good timing");
        e=game(tap(0,1,.2f));e.down(1,.2f,.8f,1.14);check(e.miss==1,"late bad hit is miss");
        e=game(tap(0,1,.2f));e.down(1,.2f,.8f,.80);check(e.finished==0,"too early untouched");e.down(2,.2f,.8f,1);check(e.perfect==1,"later valid hit");
        e=game(tap(0,1,.2f),tap(1,1,.8f));e.down(1,.2f,.9f,1);e.down(2,.8f,.6f,1);check(e.perfect==2&&e.maxCombo==2,"two-finger chord");e.up(1,1.01);check(e.finished==2,"no double scoring");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);check(e.finished==0&&e.notes.get(0).state==Engine.HELD,"hold head pending");e.update(2.01);check(e.perfect==1,"held tail completes");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);e.up(1,1.5);check(e.miss==1,"early release");e.update(3);check(e.finished==1,"hold missed only once");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);e.up(1,1.94);check(e.perfect==1,"tail release tolerance");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);e.move(1,.2f,.1f,1.2);e.update(1.30);check(e.miss==1,"moving outside rectangle breaks hold");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);e.move(1,.2f,.1f,1.2);e.move(1,.2f,.8f,1.24);e.update(2);check(e.perfect==1,"brief touch jitter tolerated");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);e.update(1.3);e.suspend();e.down(2,.2f,.8f,1.3);e.update(2);check(e.perfect==1,"pause hold reattachment");
        e=game(new Engine.Note(0,1,.2f,1));e.down(1,.2f,.8f,1);e.suspend();e.update(1.3);e.update(1.4);check(e.miss==1,"unattached hold fails after resume");
        e=game(tap(0,1,.2f),new Engine.Note(1,1.5,.8f,1));e.autoplay(1.01);e.autoplay(1.51);e.suspend();e.autoplay(1.52);e.autoplay(2.6);check(e.perfect==2,"autoplay and resume");
        e=game(new Engine.Note(0,1,.2f,1),tap(1,3,.8f));e.down(1,.2f,.8f,1);e.cancel(1.2);check(e.miss==1,"touch cancellation");e.finishAll();check(e.miss==2&&e.finished==2,"end finalizes pending");
        e.reset();check(e.finished==0&&e.notes.get(0).state==Engine.WAIT&&e.pointers.isEmpty(),"restart clears all state");
        Engine.Section a=new Engine.Section(0,1,"left",new Engine.Zone(0,0,.5f,1));Engine.Section b=new Engine.Section(1,3,"right",new Engine.Zone(.5f,0,1,1));e=new Engine(Arrays.asList(tap(0,1,.8f)),Arrays.asList(a,b));check(!e.allowed(.8f,.2f,.999)&&e.allowed(.8f,.2f,1),"half-open section boundary");
        check(e.allowed(.5f,0,1)&&e.allowed(1,1,1),"inclusive rectangle edges");
        System.out.println("PASS: "+checks+" judgement checks");
    }
}
