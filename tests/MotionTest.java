import studio.interval.Engine;import java.util.*;
public class MotionTest {
    static int count=0;
    static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    static Engine.Frame key(double t,double x,double y,double angle,String ease){return new Engine.Frame(t,x,y,angle,ease);}
    static Engine engine(Engine.Zone zone,Engine.Note... notes){return new Engine(Arrays.asList(notes),Arrays.asList(new Engine.Section(0,10,"motion",zone)));}
    public static void main(String[] args){
        Engine.Zone spin=new Engine.Zone(.3f,.45f,.7f,.55f,key(0,0,0,0,"linear"),key(4,0,0,720,"linear"));
        check(spin.pose(2)[2]==360,"continuous full rotations");check(spin.pose(8)[2]==720,"last pose held");
        for(double aspect:new double[]{(1066.6666667-96)/600,(800.-96)/600}){
            Engine.Zone z=new Engine.Zone(.3f,.45f,.7f,.55f,key(0,0,0,90,"linear"));Engine e=engine(z,new Engine.Note(0,1,.5f,0));e.fieldAspect=aspect;
            check(e.allowed(.5f,.7f,1),"rotated long side becomes vertical");check(!e.allowed(.68f,.5f,1),"axis aligned old bounds no longer accepted");
            check(!e.allowed(.9f,.5f,1),"outside polygon rejected");Float y=e.touchY(.5f,1);check(y!=null&&e.allowed(.5f,y,1),"auto touch lies within polygon");
            e.down(1,.5f,.7f,1);check(e.perfect==1,"tap judges in rotated region");
            Engine.Zone moving=new Engine.Zone(.2f,.2f,.8f,.4f,key(0,0,0,0,"linear"),key(4,0,.4,45,"smooth"));
            e=engine(moving,new Engine.Note(0,1,.5f,2));e.fieldAspect=aspect;
            for(double t=0;t<=4;t+=1./120)e.autoplay(t);check(e.perfect==1&&e.miss==0,"auto hold follows moving rotating zone");
            e=engine(new Engine.Zone(.3f,.2f,.7f,.3f,key(0,0,0,0,"linear"),key(4,0,.6,0,"linear")),new Engine.Note(0,1,.5f,2));e.fieldAspect=aspect;
            e.down(1,.5f,.4f,1);e.update(2);e.update(2.1);check(e.miss==1,"stationary finger loses moving region");
            e=engine(new Engine.Zone(.3f,.2f,.7f,.3f,key(0,0,2,45,"linear")),new Engine.Note(0,1,.5f,0));e.fieldAspect=aspect;
            check(e.touchY(.5f,1)==null&&!e.allowed(.5f,2,1),"offscreen region does not provide touch");
        }
        Engine.Zone smooth=new Engine.Zone(0,0,1,1,key(0,0,0,0,"smooth"),key(4,1,0,0,"linear"));check(Math.abs(smooth.pose(1)[0]-.15625)<1e-9,"smoothstep");
        Engine.Zone hold=new Engine.Zone(0,0,1,1,key(0,0,0,0,"hold"),key(4,1,0,90,"linear"));check(hold.pose(3.99)[0]==0&&hold.pose(4)[0]==1,"hold switches exactly at key");
        System.out.println("PASS: "+count+" moving / rotated area checks");
    }
}
