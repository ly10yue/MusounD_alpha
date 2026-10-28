package studio.interval;

import java.util.*;

/** Platform-independent judgement. All times are seconds, all positions normalized. */
public final class Engine {
    public static final double PERFECT=.060, GOOD=.120, MISS=.160, TAIL=.080, LEAVE_GRACE=.085;
    public static final float HIT_RADIUS=.085f;
    public static final int WAIT=0, HELD=1, DONE=2, LOST=3;
    public static final class Note {
        public final int id; public final double time, duration; public final Track line;
        public int state=WAIT, pointer=-1; public double outsideSince=Double.NaN;
        public String grade="";
        public Note(int id,double time,float x,double duration){this(id,time,new Track("legacy_"+x,"",new TrackKey(0,x,1,"linear")),duration);}
        public Note(int id,double time,Track line,double duration){this.id=id;this.time=time;this.line=line;this.duration=duration;}
        public float hitX(double now){return line.x(hold()&&state==HELD?Math.max(time,Math.min(end(),now)):time);}
        public boolean hold(){return duration>0;}
        public double end(){return time+duration;}
    }
    public static final class TrackKey {
        public final double time,x,alpha; public final String ease;
        public TrackKey(double time,double x,double alpha,String ease){this.time=time;this.x=x;this.alpha=alpha;this.ease=ease;}
    }
    public static final class Event {
        public final double start,end,from,to;public final String ease;
        public Event(double start,double end,double from,double to,String ease){this.start=start;this.end=end;this.from=from;this.to=to;this.ease=ease;}
    }
    public static boolean eventEase(String ease){return Arrays.asList("linear","sine_in_out","sine_in","sine_out","smooth","hold").contains(ease);}
    public static double eased(double u,String ease){
        switch(ease){case "sine_in_out":return (1-Math.cos(Math.PI*u))/2;case "sine_in":return 1-Math.cos(Math.PI*u/2);case "sine_out":return Math.sin(Math.PI*u/2);case "smooth":return u*u*(3-2*u);case "hold":return 0;default:return u;}
    }
    public static double easeIntegral(double u,String ease){
        switch(ease){case "sine_in_out":return u/2-Math.sin(Math.PI*u)/(2*Math.PI);case "sine_in":return u-2/Math.PI*Math.sin(Math.PI*u/2);case "sine_out":return 2/Math.PI*(1-Math.cos(Math.PI*u/2));case "smooth":return u*u*u-u*u*u*u/2;case "hold":return 0;default:return u*u/2;}
    }
    public static final class Curve {
        public final double base;public final Event[] events;private final double[] prefix;
        public Curve(double base,Event[] events){
            this.base=base;this.events=events;prefix=new double[events.length];double total=0,at=0,value=base;
            for(int i=0;i<events.length;i++){Event e=events[i];total+=(e.start-at)*value;prefix[i]=total;total+=area(e,1);at=e.end;value=e.to;}
        }
        private int index(double t){int lo=0,hi=events.length;while(lo<hi){int mid=(lo+hi)/2;if(events[mid].start<=t)lo=mid+1;else hi=mid;}return lo-1;}
        private double area(Event e,double u){return (e.end-e.start)*(e.from*u+(e.to-e.from)*easeIntegral(u,e.ease));}
        public double value(double t){int i=index(t);if(i<0)return base;Event e=events[i];return t>=e.end?e.to:e.from+(e.to-e.from)*eased((t-e.start)/(e.end-e.start),e.ease);}
        public double primitive(double t){int i=index(t);if(i<0)return t*base;Event e=events[i];return prefix[i]+(t>=e.end?area(e,1)+(t-e.end)*e.to:area(e,(t-e.start)/(e.end-e.start)));}
        public double distance(double now,double arrival){return primitive(arrival)-primitive(now);}
        public double horizon(double now,double distance,double limit){
            if(events.length==0)return distance>=0?Math.min(limit,now+distance/base):Math.max(limit,now+distance/base);
            double target=primitive(now)+distance,lo=distance>=0?now:limit,hi=distance>=0?limit:now;
            if(target>=primitive(hi))return hi;if(target<=primitive(lo))return lo;
            for(int i=0;i<32;i++){double mid=(lo+hi)/2;if(primitive(mid)<target)lo=mid;else hi=mid;}return (lo+hi)/2;
        }
    }
    public static final class Track {
        public final String id,name;public final TrackKey[] frames;
        public Curve position,opacity,speed=new Curve(1,new Event[0]);
        public Track(String id,String name,TrackKey... frames){this.id=id;this.name=name;this.frames=frames;}
        public double[] legacyPose(double time){
            TrackKey a=frames[0];if(time<=a.time)return new double[]{a.x,a.alpha};
            for(int i=1;i<frames.length;i++){TrackKey b=frames[i];if(time<b.time){double u=(time-a.time)/(b.time-a.time);if(a.ease.equals("smooth"))u=u*u*(3-2*u);else if(a.ease.equals("hold"))u=0;return new double[]{a.x+(b.x-a.x)*u,a.alpha+(b.alpha-a.alpha)*u};}a=b;}
            return new double[]{a.x,a.alpha};
        }
        public double[] pose(double time){double[] result=legacyPose(time);if(position!=null)result[0]=position.value(time);if(opacity!=null)result[1]=opacity.value(time);return result;}
        public double distance(double now,double arrival){return speed.distance(now,arrival);}
        public double horizon(double now,double distance,double limit){return speed.horizon(now,distance,limit);}
        public float x(double time){return (float)pose(time)[0];}
        public float pathX(double now,double arrival){return x(arrival);}
        public List<double[]> samples(double now,double start,double end,double step){
            List<double[]> result=new ArrayList<>();if(end<start)return result;
            int count=Math.max(1,Math.min(512,(int)Math.ceil((end-start)/step)));TreeSet<Double> times=new TreeSet<>();
            for(int i=0;i<=count;i++)times.add(start+(end-start)*i/count);
            for(int i=0;i<frames.length;i++){double at=frames[i].time;if(start<at&&at<=end){times.add(at);if(i>0&&frames[i-1].ease.equals("hold"))times.add(Math.max(start,at-1e-7));}}
            for(Curve curve:new Curve[]{position,speed})if(curve!=null)for(Event e:curve.events)for(double at:new double[]{e.start,e.end})if(start<at&&at<=end){times.add(at);times.add(Math.max(start,at-1e-7));}
            for(double at:times)result.add(new double[]{at,pathX(now,at)});return result;
        }
    }
    public static final class Frame {
        public final double time,x,y,angle;public final String ease;
        public Frame(double time,double x,double y,double angle,String ease){this.time=time;this.x=x;this.y=y;this.angle=angle;this.ease=ease;}
    }
    public static final class Zone {
        public final float left,top,right,bottom;
        public final Frame[] frames;
        public Curve centerX,centerY,angleCurve,widthCurve,heightCurve;
        public Zone(float l,float t,float r,float b,Frame... frames){left=l;top=t;right=r;bottom=b;this.frames=frames;}
        public boolean contains(float x,float y){return x>=left && x<=right && y>=top && y<=bottom;}
        public double[] pose(double time){
            if(frames.length==0)return new double[]{0,0,0};
            Frame a=frames[0];if(time<=a.time)return new double[]{a.x,a.y,a.angle};
            for(int i=1;i<frames.length;i++){Frame b=frames[i];if(time<b.time){double u=(time-a.time)/(b.time-a.time);if(a.ease.equals("smooth"))u=u*u*(3-2*u);else if(a.ease.equals("hold"))u=0;return new double[]{a.x+(b.x-a.x)*u,a.y+(b.y-a.y)*u,a.angle+(b.angle-a.angle)*u};}a=b;}
            return new double[]{a.x,a.y,a.angle};
        }
        public double[] geometry(double time){
            double[] v=pose(time);
            return new double[]{centerX==null?(left+right)/2.+v[0]:centerX.value(time),centerY==null?(top+bottom)/2.+v[1]:centerY.value(time),angleCurve==null?v[2]:angleCurve.value(time),widthCurve==null?right-left:widthCurve.value(time),heightCurve==null?bottom-top:heightCurve.value(time)};
        }
        public boolean contains(float x,float y,double time,double aspect){
            double[] v=geometry(time);double dx=(x-v[0])*aspect,dy=y-v[1],a=Math.toRadians(v[2]),c=Math.cos(a),s=Math.sin(a);
            return Math.abs((c*dx+s*dy)/aspect)<=v[3]/2+1e-7&&Math.abs(-s*dx+c*dy)<=v[4]/2+1e-7;
        }
        public float[] polygon(double time,double aspect){
            double[] v=geometry(time);double a=Math.toRadians(v[2]),c=Math.cos(a),s=Math.sin(a);
            float[] result=new float[8];int i=0;
            for(int[] corner:new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}}){double dx=corner[0]*v[3]/2*aspect,dy=corner[1]*v[4]/2;result[i++]=(float)(v[0]+(c*dx-s*dy)/aspect);result[i++]=(float)(v[1]+s*dx+c*dy);}
            return result;
        }
        public Float touchY(float x,double time,double aspect){
            if(x<0||x>1)return null;
            float[] points=polygon(time,aspect);double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(int i=0;i<8;i+=2){int j=(i+2)%8;double ax=points[i],ay=points[i+1],bx=points[j],by=points[j+1];
                if(x<Math.min(ax,bx)-1e-7||x>Math.max(ax,bx)+1e-7)continue;
                if(Math.abs(bx-ax)<1e-9){if(Math.abs(x-ax)<1e-7){low=Math.min(low,Math.min(ay,by));high=Math.max(high,Math.max(ay,by));}}
                else{double y=ay+(by-ay)*(x-ax)/(bx-ax);low=Math.min(low,y);high=Math.max(high,y);}
            }
            low=Math.max(0,low);high=Math.min(1,high);return low<=high+1e-7?(float)((low+high)/2):null;
        }
    }
    public static final class Section {
        public final double start,end; public final String label; public final Zone[] zones;
        public Section(double s,double e,String l,Zone... z){start=s;end=e;label=l;zones=z;}
    }
    public interface Listener { void judged(Note n,String grade,double delta); }
    public final List<Note> notes;
    public final List<Track> lines;
    public final List<Section> sections;
    public final Map<Integer,float[]> pointers=new HashMap<>();
    public int combo,maxCombo,perfect,good,miss,finished; public double points,absoluteError; public int timedHits;
    public double time=-3; public Listener listener;
    public double fieldAspect=(1066.6666667-96)/600;
    public Engine(List<Note> n,List<Section> s){this(n,s,new ArrayList<Track>());for(Note note:n)if(lines.stream().noneMatch(l->l.id.equals(note.line.id)))lines.add(note.line);}
    public Engine(List<Note> n,List<Section> s,List<Track> lines){notes=n;sections=s;this.lines=lines;reset();}
    public void reset(){combo=maxCombo=perfect=good=miss=finished=timedHits=0;points=absoluteError=0;time=-3;pointers.clear();for(Note n:notes){n.state=WAIT;n.pointer=-1;n.outsideSince=Double.NaN;n.grade="";}}
    public Section sectionAt(double t){for(Section s:sections)if(t>=s.start && t<s.end)return s;return null;}
    public boolean allowed(float x,float y,double t){if(x<0||x>1||y<0||y>1)return false;Section s=sectionAt(t);if(s==null)return false;for(Zone z:s.zones)if(z.contains(x,y,t,fieldAspect))return true;return false;}
    public Float touchY(float x,double t){Section s=sectionAt(t);if(s!=null)for(Zone z:s.zones){Float y=z.touchY(x,t,fieldAspect);if(y!=null)return y;}return null;}
    public int score(){return notes.isEmpty()?0:(int)Math.round(1000000*points/notes.size());}
    public double accuracy(){return finished==0?100:100*points/finished;}
    private void finish(Note n,String grade,double delta){
        if(n.state==DONE||n.state==LOST)return;
        n.grade=grade;n.state=grade.equals("MISS")?LOST:DONE;finished++;
        if(n.state==LOST){miss++;combo=0;}else{combo++;maxCombo=Math.max(combo,maxCombo);if(grade.equals("PERFECT")){perfect++;points++;}else{good++;points+=.65;}}
        if(listener!=null)listener.judged(n,grade,delta);
    }
    public Note down(int pointer,float x,float y,double t){
        update(t);pointers.put(pointer,new float[]{x,y});
        if(!allowed(x,y,t))return null;
        // Reattach a held note after a pause before considering new notes.
        for(Note n:notes)if(n.state==HELD && n.pointer==-1 && Math.abs(x-n.hitX(t))<=HIT_RADIUS){n.pointer=pointer;n.outsideSince=Double.NaN;return n;}
        Note best=null;double delta=MISS+1;
        for(Note n:notes){if(n.time>t+MISS)break;if(n.state==WAIT && Math.abs(x-n.hitX(t))<=HIT_RADIUS && Math.abs(t-n.time)<=MISS && Math.abs(t-n.time)<delta){best=n;delta=Math.abs(t-n.time);}}
        if(best==null)return null;
        if(delta>GOOD){finish(best,"MISS",t-best.time);return best;}
        timedHits++;absoluteError+=delta;best.grade=delta<=PERFECT?"PERFECT":"GOOD";
        if(best.hold()){best.state=HELD;best.pointer=pointer;if(listener!=null)listener.judged(best,"HOLD",t-best.time);}
        else finish(best,best.grade,t-best.time);
        return best;
    }
    public void move(int pointer,float x,float y,double t){pointers.put(pointer,new float[]{x,y});update(t);}
    public void up(int pointer,double t){update(t);for(Note n:notes)if(n.state==HELD && n.pointer==pointer){finish(n,t>=n.end()-TAIL?n.grade:"MISS",0);}pointers.remove(pointer);}
    public void cancel(double t){update(t);for(Note n:notes)if(n.state==HELD)finish(n,"MISS",0);pointers.clear();}
    public void suspend(){pointers.clear();for(Note n:notes)if(n.state==HELD){n.pointer=-1;n.outsideSince=Double.NaN;}}
    public void update(double t){
        time=t;
        for(Note n:notes){
            if(n.time>t+MISS)break;
            if(n.state==WAIT && t>n.time+MISS)finish(n,"MISS",t-n.time);
            if(n.state!=HELD)continue;
            float[] p=pointers.get(n.pointer);
            boolean valid=p!=null && allowed(p[0],p[1],t) && Math.abs(p[0]-n.hitX(t))<=HIT_RADIUS;
            if(valid){n.outsideSince=Double.NaN;if(t>=n.end())finish(n,n.grade,0);}
            else {if(Double.isNaN(n.outsideSince))n.outsideSince=t;if(t-n.outsideSince>LEAVE_GRACE)finish(n,"MISS",0);}
        }
    }
    public void autoplay(double t){
        // Synthetic pointer events still pass through area and timing checks.
        for(Note n:notes){if(n.time>t)break;if(n.state!=WAIT)continue;Float y=touchY(n.hitX(n.time),n.time);if(y!=null){followAuto(n.time);int id=10000+n.id;down(id,n.hitX(n.time),y,n.time);if(!n.hold())pointers.remove(id);}}
        followAuto(t);
        update(t);
        pointers.entrySet().removeIf(e->e.getKey()>=10000 && notes.stream().noneMatch(n->n.pointer==e.getKey()&&n.state==HELD));
    }
    private void followAuto(double t){for(Note n:notes)if(n.state==HELD){Float y=touchY(n.hitX(t),t);n.pointer=10000+n.id;if(y==null)pointers.remove(n.pointer);else pointers.put(n.pointer,new float[]{n.hitX(t),y});}}
    public void finishAll(){for(Note n:notes)if(n.state==WAIT||n.state==HELD)finish(n,"MISS",0);pointers.clear();}
}
