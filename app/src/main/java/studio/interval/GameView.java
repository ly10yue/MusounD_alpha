package studio.interval;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.*;
import android.os.SystemClock;
import android.view.*;
import java.util.*;
import java.io.File;

/** Native Canvas front end. No network, WebView or third-party runtime. */
public final class GameView extends View {
    private static final int BG=0xff10191b,PANEL=0xff182527,INK=0xffedf5eb,MUTED=0xff8da5a4,MINT=0xffa6f4cf,ORANGE=0xffffb682,GRID=0xff304244;
    private static final int HOME=0,PLAY=1,PAUSE=2,RESULT=3,COUNT=4,HELP=5,SETTINGS=6,SEEK=7;
    private final Paint p=new Paint(3);private final RectF box=new RectF();
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL),bold=Typeface.create("sans-serif",Typeface.BOLD),mono=Typeface.create("monospace",Typeface.NORMAL);
    private final ArrayList<Button> buttons=new ArrayList<>();private final ArrayList<Fx> effects=new ArrayList<>();
    private Chart chart;private Engine engine;private MediaPlayer player;private AudioManager audio;private AudioFocusRequest focus;
    private final SharedPreferences prefs;
    private final HitSounds hitSounds;
    private String bestKey="best";
    private Boolean pendingStart;private boolean hostActive=true;
    private int state=HOME,offsetMs=0,best=0;private boolean ready=false,auto=false,disposed=false,started=false,zoneGuide=true;
    private double approach=2.2,time=0,frozen=0;private long countEnd=0;private String error="",feedback="",timing="";private long feedbackUntil=0;
    private float w=1066,h=600,scale=1,insetX=0,insetY=0;private long lastFrame=0;
    private static final class Button {RectF bounds;Runnable action;Button(RectF r,Runnable a){bounds=r;action=a;}}
    private static final class Fx {float x;long born;int color;Fx(float x,int c){this.x=x;color=c;born=SystemClock.uptimeMillis();}}
    public GameView(Context context){this(context,null);}
    public GameView(Context context,File chartFile){
        super(context);setFocusable(true);setKeepScreenOn(true);
        prefs=context.getSharedPreferences("interval",Context.MODE_PRIVATE);offsetMs=prefs.getInt("offset",0);approach=Math.max(.6,Math.min(4,prefs.getFloat("approach",2.2f)));best=prefs.getInt("best",0);zoneGuide=prefs.getBoolean("zoneGuide",true);
        hitSounds=new HitSounds(context);hitSounds.enabled=prefs.getBoolean("hitSounds",true);
        try {
            chart=new Chart(context,chartFile);bestKey=chartFile==null?"best":"best_"+chartFile.getAbsolutePath().hashCode();best=prefs.getInt(bestKey,0);engine=chart.engine;engine.listener=(n,grade,delta)->{feedback=grade;feedbackUntil=SystemClock.uptimeMillis()+650;timing=grade.equals("MISS")||grade.equals("HOLD")?"":String.format(Locale.US,"%s %d ms",delta<0?"EARLY":"LATE",Math.round(Math.abs(delta)*1000));effects.add(new Fx(n.hitX(engine.time),grade.equals("MISS")?ORANGE:MINT));if(grade.equals("HOLD")||(!n.hold()&&!grade.equals("MISS")))hitSounds.play(n.hold());};
            audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            AudioAttributes attr=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attr).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(change->{if(change<0)pause();}).build();
            player=new MediaPlayer();player.setAudioAttributes(attr);
            if(chart.audioFile!=null)player.setDataSource(chart.audioFile.getAbsolutePath());
            else try(AssetFileDescriptor fd=context.getAssets().openFd(chart.audio)){player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
            player.setOnPreparedListener(mp->{ready=true;if(pendingStart!=null&&hostActive){boolean demo=pendingStart;pendingStart=null;start(demo);}invalidate();});
            player.setOnSeekCompleteListener(mp->{if(state==SEEK)countdown();});
            player.setOnCompletionListener(mp->{if(state==PLAY)complete();});
            player.setOnErrorListener((mp,what,extra)->{error="音频播放失败（"+what+" / "+extra+"），请重新打开应用";ready=false;state=HOME;return true;});
            player.prepareAsync();
        }catch(Exception ex){error="资源加载失败："+ex.getMessage();}
        setContentDescription("INTERVAL 区间音乐游戏。横屏演奏；返回选歌可切换歌曲。");
    }
    private void save(){prefs.edit().putInt("offset",offsetMs).putFloat("approach",(float)approach).putBoolean("zoneGuide",zoneGuide).putBoolean("hitSounds",hitSounds.enabled).apply();}
    private float left(){return 48;}private float field(){return w-96;}private float nx(float x){return left()+x*field();}private float line(){return h*.80f;}
    private void color(int color){p.setColor(color);p.setShader(null);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    private void rect(Canvas c,float x,float y,float width,float height,int color){color(color);c.drawRect(x,y,x+width,y+height,p);}
    private void round(Canvas c,float x,float y,float width,float height,float radius,int color){color(color);box.set(x,y,x+width,y+height);c.drawRoundRect(box,radius,radius,p);}
    private void stroke(Canvas c,float x,float y,float width,float height,int color){color(color);p.setStyle(Paint.Style.STROKE);box.set(x,y,x+width,y+height);c.drawRoundRect(box,5,5,p);p.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface font){color(color);p.setTypeface(font);p.setTextSize(size);c.drawText(s,x,y,p);}
    private void center(Canvas c,String s,float x,float y,float size,int color,Typeface font){p.setTypeface(font);p.setTextSize(size);float width=p.measureText(s);text(c,s,x-width/2,y,size,color,font);}
    private void fitText(Canvas c,String s,float x,float y,float size,float maxWidth,int color,Typeface font){p.setTypeface(font);p.setTextSize(size);while(s.length()>1&&p.measureText(s)>maxWidth)s=s.substring(0,s.length()-2)+"…";text(c,s,x,y,size,color,font);}
    private void button(Canvas c,String label,float x,float y,float width,float height,boolean primary,Runnable action){round(c,x,y,width,height,5,primary?MINT:PANEL);if(!primary)stroke(c,x,y,width,height,GRID);center(c,label,x+width/2,y+height/2+6,17,primary?BG:INK,bold);buttons.add(new Button(new RectF(x,y,x+width,y+height),action));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(BG);buttons.clear();
        // Cap the field at 16:9 on ultrawide screens; 4:3 uses its complete width.
        float availW=getWidth(),availH=getHeight();
        WindowInsets ins=getRootWindowInsets();if(android.os.Build.VERSION.SDK_INT>=28 && ins!=null && ins.getDisplayCutout()!=null){availW-=ins.getDisplayCutout().getSafeInsetLeft()+ins.getDisplayCutout().getSafeInsetRight();insetX=ins.getDisplayCutout().getSafeInsetLeft();}else insetX=0;
        scale=Math.min(availH/600f,availW/800f);h=600;w=Math.min(1066.6667f,availW/scale);insetX+=(availW-w*scale)/2;insetY=(availH-h*scale)/2;
        if(engine!=null)engine.fieldAspect=field()/h;
        canvas.save();canvas.translate(insetX,insetY);canvas.scale(scale,scale);
        rect(canvas,0,0,w,h,BG);
        if(state==HOME)home(canvas);else if(state==HELP)help(canvas);else if(state==SETTINGS)settings(canvas);else if(state==RESULT)results(canvas);else {
            if(state==PLAY){time=clock();if(auto)engine.autoplay(time);else engine.update(time);}
            if(state==COUNT){time=frozen;if(SystemClock.uptimeMillis()>=countEnd){if(audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();started=true;state=PLAY;}else {state=PAUSE;feedback="音频被其他应用占用";}}}
            playfield(canvas);
            if(state==PAUSE)pauseOverlay(canvas);
            if(state==COUNT||state==SEEK){round(canvas,w/2-115,150,230,138,8,0xe8182527);center(canvas,state==SEEK?"…":Integer.toString(Math.max(1,(int)Math.ceil((countEnd-SystemClock.uptimeMillis())/1000.0))),w/2,220,58,MINT,mono);center(canvas,started?"重新按住 Hold，准备继续":"跟随节奏，进入区间",w/2,260,15,INK,regular);}
        }
        if(!error.isEmpty()){round(canvas,48,h-100,w-96,64,5,0xff51382c);text(canvas,error,64,h-62,15,INK,regular);}
        canvas.restore();lastFrame=SystemClock.uptimeMillis();
        if(!disposed)postInvalidateOnAnimation();
    }
    private void header(Canvas c,String right){
        rect(c,48,31,5,22,MINT);rect(c,57,24,5,29,MINT);rect(c,66,36,5,17,MINT);
        text(c,"INTERVAL",85,48,20,INK,bold);text(c,"区 间",211,47,12,MUTED,regular);
        p.setTypeface(mono);p.setTextSize(11);text(c,right,w-48-p.measureText(right),44,11,MUTED,mono);
        rect(c,48,72,w-96,1,GRID);
    }
    private void home(Canvas c){
        header(c,"PREPARING YOUR SESSION");
        center(c,error.isEmpty()?"准备音乐…":"歌曲暂时无法播放",w/2,245,32,INK,bold);
        if(chart!=null)fitText(c,chart.title,48,310,22,w-96,MINT,bold);
        button(c,"返回选歌",48,515,156,45,false,this::goHome);
    }
    private void help(Canvas c){
        header(c,"HOW TO PLAY");text(c,"先找到区域，再跟上节奏。",48,128,30,INK,bold);
        String[] titles={"01 / TAP","02 / HOLD","03 / AREA"};
        String[][] details={{"音符到达下方固定判定线时点击。","横向对准轨迹交点；纵向不必贴线。"},{"跟随下方轨迹点，按住至尾部到线。","支持双指；移出区域或松手会断连。"},{"只有绿色框内可以触发判定。","区域可限制上下范围，切换前会预告。"}};
        float cw=(w-120)/3;
        for(int i=0;i<3;i++){float x=48+i*(cw+12);round(c,x,166,cw,238,7,PANEL);text(c,titles[i],x+18,198,14,MINT,mono);
            if(i==0){round(c,x+cw/2-35,240,70,9,3,MINT);rect(c,x+22,293,cw-44,2,INK);}
            if(i==1){round(c,x+cw/2-30,221,60,79,3,0xff655240);round(c,x+cw/2-34,291,68,9,3,ORANGE);}
            if(i==2){stroke(c,x+23,224,cw-46,77,MINT);rect(c,x+24,225,cw-48,75,0x153effac);center(c,"VALID",x+cw/2,270,15,MINT,mono);}
            text(c,details[i][0],x+15,344,12,INK,regular);text(c,details[i][1],x+15,372,12,MUTED,regular);
        }
        text(c,"判定窗口   PERFECT ±60ms   /   GOOD ±120ms   /   MISS ±160ms",48,444,13,MUTED,mono);
        text(c,"暂停恢复有 3 秒倒数；遇到未结束的 Hold，请在倒数内重新按住。",48,475,14,INK,regular);
        button(c,"返回",48,515,130,45,false,()->((MainActivity)getContext()).returnToMenu());button(c,"前往选歌 →",w-248,515,200,45,true,this::goHome);
    }
    private void settings(Canvas c){
        header(c,"TUNE YOUR SESSION");text(c,"找到舒服的手感。",48,136,34,INK,bold);
        text(c,"下落速度",48,207,20,INK,bold);text(c,"数字越小，下落越快；不会改变歌曲速度。",48,238,14,MUTED,regular);
        button(c,"−",w-330,185,55,48,false,()->{approach=Math.max(.6,Math.round((approach-.2)*10)/10.);save();});center(c,String.format(Locale.US,"%.1f s",approach),w-202,218,22,MINT,mono);button(c,"+",w-103,185,55,48,false,()->{approach=Math.min(4,Math.round((approach+.2)*10)/10.);save();});
        rect(c,48,265,w-96,1,GRID);text(c,"音符延迟补偿",48,309,20,INK,bold);text(c,"正值使音符更晚到线；建议从 0ms 开始调整。",48,340,14,MUTED,regular);
        button(c,"−",w-330,289,55,48,false,()->{offsetMs=Math.max(-300,offsetMs-10);save();});center(c,offsetMs+" ms",w-202,322,22,MINT,mono);button(c,"+",w-103,289,55,48,false,()->{offsetMs=Math.min(300,offsetMs+10);save();});
        rect(c,48,368,w-96,1,GRID);text(c,"区域辅助显示",48,412,20,INK,bold);text(c,"显示开放区域的淡色填充，边界始终保留。",48,443,14,MUTED,regular);
        button(c,zoneGuide?"开启":"关闭",w-204,392,156,48,false,()->{zoneGuide=!zoneGuide;save();});
        text(c,"打击音效",48,492,20,INK,bold);button(c,hitSounds.enabled?"开启":"关闭",w-204,465,156,38,false,()->{hitSounds.enabled=!hitSounds.enabled;if(!hitSounds.enabled)hitSounds.stop();save();});
        button(c,"返回",48,515,130,45,false,()->((MainActivity)getContext()).returnToMenu());text(c,"横屏自适应 · 16:9 / 4:3 · 多点触控",w-365,545,13,MUTED,regular);
    }
    private Path zonePath(Engine.Zone zone){float[] points=zone.polygon(Math.max(0,time),engine.fieldAspect);Path path=new Path();path.moveTo(nx(points[0]),points[1]*h);for(int i=2;i<8;i+=2)path.lineTo(nx(points[i]),points[i+1]*h);path.close();return path;}
    private void playfield(Canvas c){
        float ly=line();
        Engine.Section sec=engine.sectionAt(Math.max(0,time));

        if(sec!=null){
            c.save();c.clipRect(48,100,w-48,h-36);
            Path union=RegionOutline.path(sec,Math.max(0,time),engine.fieldAspect,left(),field(),h);
            // Cut the region union, then stroke only its external contour.
            int layer=c.saveLayer(48,100,w-48,h-36,null);rect(c,48,100,w-96,h-136,0x9005090b);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));c.drawPath(union,p);p.setXfermode(null);c.restoreToCount(layer);
            if(zoneGuide){color(0x0ca6f4cf);c.drawPath(union,p);}color(0xff608b76);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(union,p);p.setStyle(Paint.Style.FILL);
            c.restore();
        }
        float travel=(float)((ly-105)/approach);
        c.save();c.clipRect(47,100,w-47,h-35);
        Map<Engine.Track,double[]> visible=new HashMap<>();
        for(Engine.Track track:engine.lines){
            double lo=track.horizon(time,(ly-h)/travel,Math.min(0,time)),hi=track.horizon(time,(ly-95)/travel,chart.duration-chart.offset+3);visible.put(track,new double[]{lo,hi});
            Path path=new Path();boolean first=true;
            for(double[] v:track.samples(time,time,hi,.035)){
                float x=nx((float)v[1]),y=ly-(float)track.distance(time,v[0])*travel;if(first){path.moveTo(x,y);first=false;}else path.lineTo(x,y);
            }
            color(((int)(track.pose(time)[1]*140)<<24)|0x8fcebb);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
            c.drawCircle(nx(track.x(time)),ly,5,p);
        }
        for(Engine.Note n:engine.notes){
            double[] limits=visible.get(n.line);if(limits==null)limits=new double[]{n.line.horizon(time,(ly-h)/travel,Math.min(0,time)),n.line.horizon(time,(ly-95)/travel,chart.duration-chart.offset+3)};if(n.time>limits[1]||n.state==Engine.DONE||n.state==Engine.LOST)continue;
            double start=n.state==Engine.HELD?Math.max(n.time,time):n.time;
            float x=nx(n.line.pathX(time,start)),width=field()*.092f,head=ly-(float)n.line.distance(time,start)*travel;
            if(n.hold()){
                double lo=Math.max(start,limits[0]),hi=Math.min(n.end(),limits[1]);
                if(hi<lo)continue;
                List<double[]> points=n.line.samples(time,lo,hi,.035);Path body=new Path();
                for(int k=0;k<points.size();k++){double[] v=points.get(k);float px=nx((float)v[1])-width/2,py=ly-(float)n.line.distance(time,v[0])*travel;if(k==0)body.moveTo(px,py);else body.lineTo(px,py);}
                for(int k=points.size()-1;k>=0;k--){double[] v=points.get(k);body.lineTo(nx((float)v[1])+width/2,ly-(float)n.line.distance(time,v[0])*travel);}body.close();
                color(n.state==Engine.HELD?0xff786247:0xff4f4436);c.drawPath(body,p);color(ORANGE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(body,p);p.setStyle(Paint.Style.FILL);
                float tail=ly-(float)n.line.distance(time,n.end())*travel;
                round(c,nx(n.line.pathX(time,n.end()))-width/2-3,tail-2,width+6,5,2,ORANGE);
            }
            if(head>=95 && head<h){round(c,x-width/2-3,head-5,width+6,10,3,n.hold()?ORANGE:MINT);rect(c,x-width/2+5,head-2,width-10,2,0xaaffffff);}
        }
        c.restore();
        rect(c,48,ly,w-96,2,INK);text(c,"JUDGEMENT / FIXED",48,ly+24,9,MUTED,mono);
        long now=SystemClock.uptimeMillis();
        Iterator<Fx> it=effects.iterator();while(it.hasNext()){Fx f=it.next();float age=(now-f.born)/420f;if(age>1){it.remove();continue;}color((f.color&0xffffff)|((int)((1-age)*180)<<24));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(nx(f.x),ly,15+age*37,p);p.setStyle(Paint.Style.FILL);}
        for(float[] touch:engine.pointers.values()){color(0x50a6f4cf);c.drawCircle(nx(touch[0]),touch[1]*h,23,p);color(MINT);c.drawCircle(nx(touch[0]),touch[1]*h,4,p);}
        if(engine.combo>1){center(c,Integer.toString(engine.combo),w/2,183,42,INK,mono);center(c,"COMBO",w/2,204,10,MUTED,mono);}
        if(now<feedbackUntil){center(c,feedback,w/2,252,19,feedback.equals("MISS")?ORANGE:MINT,mono);center(c,timing,w/2,273,10,MUTED,mono);}
        rect(c,0,0,w,88,BG);fitText(c,chart.title,48,35,17,w*.36f,INK,bold);fitText(c,chart.artist+" / "+chart.difficulty,48,58,10,w*.36f,MUTED,mono);
        center(c,auto?"AUTO / 演示":"LIVE / 演奏",w/2,37,11,auto?ORANGE:MINT,mono);center(c,String.format(Locale.US,"%.2f%%",engine.accuracy()),w/2,59,12,MUTED,mono);
        text(c,String.format(Locale.US,"%07d",engine.score()),w-249,42,27,INK,mono);
        button(c,"Ⅱ",w-95,20,47,45,false,this::pause);
        rect(c,48,77,w-96,2,GRID);rect(c,48,77,(w-96)*(float)Math.max(0,Math.min(1,time/chart.duration)),2,MINT);
        String label=sec==null?"准备":sec.label;text(c,label,48,h-16,12,MINT,regular);
        text(c,formatTime(Math.max(0,time))+" / "+formatTime(chart.duration),w-177,h-16,11,MUTED,mono);
        if(sec!=null && sec.end-time<2.2 && sec.end-time>0){Engine.Section next=engine.sectionAt(sec.end+.001);if(next!=null){round(c,w/2-172,h-79,344,33,4,0xff3c3c2a);center(c,"即将切换  →  "+next.label,w/2,h-57,13,ORANGE,regular);}}
    }
    private String formatTime(double t){return String.format(Locale.US,"%02d:%02d",(int)t/60,(int)t%60);}
    private void pauseOverlay(Canvas c){
        rect(c,0,88,w,h-88,0xd010191b);round(c,w/2-238,158,476,290,10,PANEL);
        center(c,"节奏暂歇",w/2,212,33,INK,bold);center(c,"音乐与谱面已同步暂停",w/2,246,14,MUTED,regular);
        button(c,"继续演奏  →",w/2-194,275,388,49,true,this::resume);
        button(c,"重新开始",w/2-194,338,188,43,false,()->start(auto));button(c,"返回选曲",w/2+6,338,188,43,false,this::goHome);
        center(c,"继续前有 3 秒倒数，可重新接住 Hold",w/2,416,12,MUTED,regular);
    }
    private void results(Canvas c){
        header(c,auto?"AUTOPLAY / NOT RANKED":"SESSION COMPLETE");text(c,auto?"演示结束。":"这一次，节奏属于你。",48,136,32,INK,bold);
        fitText(c,chart.title+" / "+chart.artist,48,169,12,w-96,MUTED,mono);
        String rank=engine.miss==0&&engine.good==0?"AP":engine.miss==0?"FC":engine.accuracy()>=95?"S":engine.accuracy()>=85?"A":engine.accuracy()>=70?"B":"C";
        text(c,rank,48,303,100,MINT,bold);text(c,String.format(Locale.US,"%07d",engine.score()),230,280,54,INK,mono);text(c,"SCORE / 1,000,000",233,307,12,MUTED,mono);
        float cw=(w-120)/3;String[] labels={"PERFECT","GOOD","MISS"};int[] counts={engine.perfect,engine.good,engine.miss};
        for(int i=0;i<3;i++){float x=48+i*(cw+12);round(c,x,343,cw,86,5,PANEL);text(c,labels[i],x+18,370,11,i==2?ORANGE:MUTED,mono);text(c,""+counts[i],x+18,407,30,INK,mono);}
        text(c,String.format(Locale.US,"ACCURACY  %.2f%%     MAX COMBO  %d",engine.accuracy(),engine.maxCombo),48,467,14,MINT,mono);
        text(c,auto?"自动演示成绩不会保存为个人纪录。":"平均时差 "+(engine.timedHits==0?"—":Math.round(engine.absoluteError/engine.timedHits*1000)+" ms")+" · 继续练习，找到你的区间。",48,493,13,MUTED,regular);
        button(c,"返回选曲",48,522,156,43,false,this::goHome);button(c,"再来一次 →",w-248,522,200,43,true,()->start(auto));
    }
    private double clock(){
        if(player==null||!started)return frozen;
        MediaTimestamp stamp=player.getTimestamp();double sec;
        if(stamp!=null && stamp.getMediaClockRate()>0)sec=stamp.getAnchorMediaTimeUs()/1e6+(System.nanoTime()-stamp.getAnchorSytemNanoTime())/1e9*stamp.getMediaClockRate();else sec=player.getCurrentPosition()/1000.0;
        return sec-chart.offset-offsetMs/1000.0;
    }
    public void launch(boolean demo){if(ready&&hostActive)start(demo);else pendingStart=demo;}
    public void showUtility(boolean settings){pendingStart=null;state=settings?SETTINGS:HELP;}
    public void setHostActive(boolean active){hostActive=active;if(!active)pause();else if(ready&&pendingStart!=null){boolean demo=pendingStart;pendingStart=null;start(demo);}}
    private void start(boolean demo){
        if(!ready)return;auto=demo;if(player.isPlaying())player.pause();engine.reset();effects.clear();feedback="";started=false;frozen=-chart.offset-offsetMs/1000.0;time=frozen;state=SEEK;player.seekTo(0,MediaPlayer.SEEK_CLOSEST);
    }
    private void countdown(){countEnd=SystemClock.uptimeMillis()+3000;state=COUNT;}
    public void pause(){hitSounds.stop();if(state==PLAY||state==COUNT||state==SEEK){if(state==PLAY){frozen=clock();player.pause();}engine.suspend();time=frozen;state=PAUSE;audio.abandonAudioFocusRequest(focus);}}
    private void resume(){if(ready){countdown();}}
    private void goHome(){if(player!=null&&ready&&player.isPlaying())player.pause();if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);if(engine!=null)engine.suspend();pendingStart=null;((MainActivity)getContext()).showSongs();}
    private void complete(){engine.finishAll();state=RESULT;audio.abandonAudioFocusRequest(focus);if(!auto&&engine.score()>best){best=engine.score();prefs.edit().putInt(bestKey,best).apply();}}
    public void back(){if(state==PLAY||state==COUNT||state==SEEK)pause();else if(state==SETTINGS||state==HELP)((MainActivity)getContext()).returnToMenu();else goHome();}
    public void dispose(){disposed=true;hitSounds.release();if(player!=null){player.release();player=null;}if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();float x=(e.getX(index)-insetX)/scale,y=(e.getY(index)-insetY)/scale;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            // Snapshot actions: state may change immediately, before the next frame.
            if(action==MotionEvent.ACTION_DOWN){for(Button b:new ArrayList<>(buttons))if(b.bounds.contains(x,y)){buttons.clear();b.action.run();performClick();return true;}}
            if((state==PLAY||state==COUNT)&&!auto){double t=state==PLAY?clock()-(SystemClock.uptimeMillis()-e.getEventTime())/1000.0:frozen;Engine.Note n=engine.down(e.getPointerId(index),(x-left())/field(),y/h,t);if(n==null && !engine.allowed((x-left())/field(),y/h,t)){feedback="OUTSIDE AREA";feedbackUntil=SystemClock.uptimeMillis()+350;timing="请触碰开放区域";}}
        }else if(action==MotionEvent.ACTION_MOVE && (state==PLAY||state==COUNT)&&!auto){double t=state==PLAY?clock():frozen;for(int i=0;i<e.getPointerCount();i++)engine.move(e.getPointerId(i),((e.getX(i)-insetX)/scale-left())/field(),(e.getY(i)-insetY)/scale/h,t);
        }else if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&(state==PLAY||state==COUNT)&&!auto){engine.up(e.getPointerId(index),state==PLAY?clock():frozen);
        }else if(action==MotionEvent.ACTION_CANCEL&&engine!=null){if(state==PLAY)engine.cancel(clock());else engine.suspend();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
