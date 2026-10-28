package studio.interval;

import android.graphics.*;
import android.view.*;
import java.util.*;

/** Landing and song selection share the game's safe 16:9 / 4:3 coordinate space. */
public final class MenuView extends View {
    private static final int BG=0xff10191b,PANEL=0xff182527,INK=0xffedf5eb,MUTED=0xff8da5a4,MINT=0xffa6f4cf,ORANGE=0xffffb682,GRID=0xff304244;
    private final MainActivity host;
    private final Paint p=new Paint(3);
    private final Typeface bold=Typeface.create("sans-serif",Typeface.BOLD),regular=Typeface.create("sans-serif",0),mono=Typeface.create("monospace",0);
    private final ArrayList<Button> buttons=new ArrayList<>();
    private SongLibrary library=new SongLibrary();
    private boolean songsPage=false,loading=true,importing=false;
    private String selected="";
    private int page=0;
    private float w=1066,scale=1,insetX=0,insetY=0,downX,downY;
    private Button pressed;
    private static final class Button {RectF bounds;Runnable action;Button(RectF bounds,Runnable action){this.bounds=bounds;this.action=action;}}
    public MenuView(MainActivity host){super(host);this.host=host;setFocusable(true);setContentDescription("INTERVAL 初始界面，进入选歌、玩法说明、设置。");}
    public boolean isSongsPage(){return songsPage;}
    public String selectedId(){return selected;}
    public void showTitle(){songsPage=false;invalidate();}
    public void showSongs(){songsPage=true;invalidate();}
    public void setImporting(boolean value){importing=value;invalidate();}
    public void setLibrary(SongLibrary value,String preferred){
        library=value;loading=false;selected=preferred;int index=selectedIndex();
        if(index<0&&!library.songs.isEmpty()){selected=library.songs.get(0).id;index=0;}
        page=Math.max(0,index)/4;invalidate();
    }
    private int selectedIndex(){for(int i=0;i<library.songs.size();i++)if(library.songs.get(i).id.equals(selected))return i;return -1;}
    private SongLibrary.Song selectedSong(){int i=selectedIndex();return i<0?null:library.songs.get(i);}
    private void fill(int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);}
    private void rect(Canvas c,float x,float y,float width,float height,int color){fill(color);c.drawRect(x,y,x+width,y+height,p);}
    private void round(Canvas c,float x,float y,float width,float height,int color){fill(color);c.drawRoundRect(new RectF(x,y,x+width,y+height),8,8,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface font){fill(color);p.setTypeface(font);p.setTextSize(size);c.drawText(s,x,y,p);}
    private void fit(Canvas c,String s,float x,float y,float size,float maxWidth,int color,Typeface font){
        p.setTypeface(font);p.setTextSize(size);String value=s;
        if(p.measureText(value)>maxWidth){int count=p.breakText(s,true,Math.max(0,maxWidth-p.measureText("…")),null);if(count>0&&Character.isHighSurrogate(s.charAt(count-1)))count--;value=s.substring(0,count)+"…";}
        text(c,value,x,y,size,color,font);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,Typeface font){p.setTypeface(font);p.setTextSize(size);text(c,s,x-p.measureText(s)/2,y,size,color,font);}
    private void button(Canvas c,String label,float x,float y,float width,float height,boolean primary,boolean enabled,Runnable action){
        round(c,x,y,width,height,enabled?(primary?MINT:PANEL):GRID);
        centered(c,label,x+width/2,y+height/2+6,16,enabled?(primary?BG:INK):MUTED,bold);
        if(enabled)buttons.add(new Button(new RectF(x,y,x+width,y+height),action));
    }
    private String duration(double seconds){return String.format(Locale.US,"%02d:%02d",(int)seconds/60,(int)seconds%60);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(BG);buttons.clear();
        float availW=getWidth(),availH=getHeight();insetX=0;
        WindowInsets ins=getRootWindowInsets();if(android.os.Build.VERSION.SDK_INT>=28&&ins!=null&&ins.getDisplayCutout()!=null){insetX=ins.getDisplayCutout().getSafeInsetLeft();availW-=insetX+ins.getDisplayCutout().getSafeInsetRight();}
        if(availW<=0||availH<=0)return;
        scale=Math.min(availH/600f,availW/800f);w=Math.min(1066.6667f,availW/scale);insetX+=(availW-w*scale)/2;insetY=(availH-600*scale)/2;
        canvas.save();canvas.translate(insetX,insetY);canvas.scale(scale,scale);
        if(songsPage)library(canvas);else title(canvas);
        canvas.restore();
    }
    private void title(Canvas c){
        text(c,"I / INTERVAL",48,49,18,MINT,bold);text(c,"OFFLINE RHYTHM",w-200,47,11,MUTED,mono);rect(c,48,72,w-96,1,GRID);
        text(c,"FIND YOUR",50,156,15,MINT,mono);text(c,"INTERVAL.",45,230,w<900?63:76,INK,bold);
        text(c,"区 间",51,279,27,INK,bold);text(c,"让每一次触碰，都落在节奏里。",51,319,18,MUTED,regular);
        float cx=w-193,cy=242;
        fill(GRID);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);for(int i=0;i<4;i++)c.drawCircle(cx,cy,80+i*18,p);p.setStyle(Paint.Style.FILL);
        float[] lengths={58,120,86,151};
        for(int i=0;i<4;i++){float x=cx-91+i*48;round(c,x,cy+70-lengths[i],29,lengths[i],i%2==0?0xff294b43:0xff4c4032);round(c,x-3,cy+70-lengths[i],35,7,i%2==0?MINT:ORANGE);}
        rect(c,cx-117,cy+83,234,2,MINT);centered(c,"TOUCH WITHIN THE SIGNAL",cx,cy+112,10,MUTED,mono);
        button(c,"进入选歌   →",48,389,302,58,true,true,host::showSongs);
        button(c,"玩法说明",48,462,145,42,false,true,()->host.showUtility(false));
        button(c,"设置",205,462,145,42,false,true,()->host.showUtility(true));
        text(c,"TAP / HOLD   ·   二维判定区域",48,558,12,MUTED,regular);
        text(c,"16:9 + 4:3 / v"+Release.VERSION,w-256,558,11,MUTED,mono);
    }
    private void library(Canvas c){
        button(c,"← 首页",48,24,102,40,false,true,host::showTitle);
        text(c,"选择歌曲",169,51,22,INK,bold);
        button(c,importing?"正在导入…":"＋ 导入谱面",w-210,24,162,40,true,!importing,host::importChart);
        rect(c,48,82,w-96,1,GRID);
        float lw=(w-120)*.48f,dx=48+lw+24,dw=w-dx-48;
        text(c,"曲库 / "+library.songs.size()+" 首谱面",48,118,14,MUTED,regular);
        text(c,"TRACK DETAILS",dx,118,12,MINT,mono);
        if(loading)text(c,"正在读取曲库…",64,187,17,INK,regular);
        else if(library.songs.isEmpty())text(c,"暂无可用谱面，请导入 .interval",64,187,14,INK,regular);
        for(int row=0;row<4;row++){
            int index=page*4+row;if(index>=library.songs.size())break;
            SongLibrary.Song song=library.songs.get(index);float y=140+row*86;boolean active=song.id.equals(selected);
            round(c,48,y,lw,76,active?0xff29423b:PANEL);if(active)rect(c,48,y+10,3,56,MINT);
            fit(c,song.title,64,y+27,18,lw-32,INK,bold);
            fit(c,song.artist+"  /  "+song.difficulty,64,y+49,12,lw-32,MUTED,regular);
            text(c,(song.file==null?"内置":"导入")+"  ·  "+duration(song.duration)+"  ·  "+String.format(Locale.US,"%.0f BPM",song.bpm),64,y+66,10,active?MINT:MUTED,regular);
            buttons.add(new Button(new RectF(48,y,48+lw,y+76),()->{selected=song.id;host.rememberSong(selected);invalidate();}));
        }
        int pages=Math.max(1,(library.songs.size()+3)/4);
        button(c,"←",48,498,62,39,false,page>0,()->{page--;invalidate();});
        centered(c,(page+1)+" / "+pages,48+lw/2,523,13,MUTED,mono);
        button(c,"→",48+lw-62,498,62,39,false,page+1<pages,()->{page++;invalidate();});
        SongLibrary.Song song=selectedSong();
        if(song!=null){
            round(c,dx,140,dw,309,PANEL);
            // Geometric artwork works for both built-in and user-created charts.
            rect(c,dx+20,160,5,53,MINT);rect(c,dx+32,175,5,38,ORANGE);rect(c,dx+44,153,5,60,MINT);
            text(c,song.file==null?"BUILT-IN / 内置示例":"CUSTOM / 导入谱面",dx+66,178,11,MINT,regular);
            fit(c,song.difficulty,dx+66,205,22,dw-86,INK,bold);
            fit(c,song.title,dx+20,251,24,dw-40,INK,bold);fit(c,song.artist,dx+20,278,14,dw-40,MUTED,regular);
            text(c,String.format(Locale.US,"%.1f BPM   /   %s",song.bpm,duration(song.duration)),dx+20,310,14,MINT,mono);
            text(c,song.notes+" 音符   ·   "+song.holds+" HOLD",dx+20,337,13,INK,regular);
            text(c,song.areas+" 段 · "+song.fileVersion,dx+20,363,13,MUTED,regular);
            rect(c,dx+20,382,dw-40,1,GRID);
            int best=host.getSharedPreferences("interval",0).getInt(song.bestKey(),0);
            text(c,"BEST",dx+20,416,12,MUTED,mono);text(c,String.format(Locale.US,"%07d",best),dx+88,419,24,MINT,mono);
            button(c,"开始演奏  →",dx,462,dw,48,true,true,()->host.playSong(song.file,false));
            button(c,"自动演示",dx,521,(dw-12)/2,37,false,true,()->host.playSong(song.file,true));
            button(c,"设置",dx+(dw+12)/2,521,(dw-12)/2,37,false,true,()->host.showUtility(true));
        }
        fit(c,library.unavailable>0?library.unavailable+" 份谱面无法读取，已跳过":"支持 .interval · 歌曲与谱面一同导入",48,571,11,lw,library.unavailable>0?ORANGE:MUTED,regular);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        float x=(event.getX()-insetX)/scale,y=(event.getY()-insetY)/scale;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=x;downY=y;pressed=null;for(Button b:buttons)if(b.bounds.contains(x,y)){pressed=b;break;}return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            if(Math.abs(x-downX)<18&&Math.abs(y-downY)<18&&pressed!=null&&pressed.bounds.contains(x,y)){Runnable action=pressed.action;pressed=null;buttons.clear();action.run();performClick();}
            else if(songsPage&&downX<48+(w-120)*.48f&&Math.abs(x-downX)>60&&Math.abs(y-downY)<60){page=Math.max(0,Math.min(Math.max(0,(library.songs.size()-1)/4),page+(x<downX?1:-1)));invalidate();}
            pressed=null;return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)pressed=null;
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
