package studio.interval;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Chart {
    public final String title,artist,audio,difficulty,fileVersion;
    public final double duration,offset,bpm;
    public final Engine engine;
    public final File audioFile;
    public Chart(Context context)throws Exception {this(context,null);}
    public Chart(Context context,File source)throws Exception {
        JSONObject j;
        try(InputStream in=source==null?context.getAssets().open("demo.json"):new FileInputStream(source)){j=new JSONObject(read(in,4*1024*1024));}
        fileVersion=j.optString("appVersion","0.11.0")+" / v"+j.getInt("version");Release.normalize(j);
        int version=j.getInt("version");if(version<1||version>5)throw new IOException("不支持的谱面版本，请更新游戏");
        title=j.getString("title");artist=j.getString("artist");audio=j.getString("audio");difficulty=j.getString("difficulty");
        duration=j.getDouble("duration");offset=j.optDouble("offset",0);bpm=j.getDouble("bpm");
        if(!finite(duration)||duration<=0||duration>86400||!finite(offset)||Math.abs(offset)>600||!finite(bpm)||bpm<20||bpm>400)throw new IOException("谱面时间参数无效");
        if(source!=null){
            if(!audio.matches("[A-Za-z0-9_.-]+")||audio.equals(".")||audio.equals("..")||audio.equals("chart.json"))throw new IOException("歌曲文件名无效");
            audioFile=new File(source.getParentFile(),audio);
            if(!audioFile.isFile())throw new IOException("谱面包中缺少歌曲");
        }else audioFile=null;
        List<Engine.Note> notes=new ArrayList<>();List<Engine.Section> sections=new ArrayList<>();
        JSONArray ns=j.getJSONArray("notes"),ss=j.getJSONArray("sections");
        if(ns.length()>20000||ss.length()==0||ss.length()>5000)throw new IOException("谱面规模超出支持范围");
        List<Engine.Track> lines=new ArrayList<>();Map<String,Engine.Track> lineMap=new HashMap<>();
        if(version>=3){
            JSONArray ls=j.getJSONArray("lines");if(ls.length()<1||ls.length()>64)throw new IOException("需要 1–64 条轨迹线");
            for(int i=0;i<ls.length();i++){
                JSONObject l=ls.getJSONObject(i);String id=l.getString("id");if(!id.matches("[A-Za-z0-9_-]{1,64}")||lineMap.containsKey(id))throw new IOException("轨迹 ID 无效或重复");
                JSONArray fs=l.getJSONArray("frames");if(fs.length()<1||fs.length()>256)throw new IOException("轨迹需要 1–256 个关键帧");
                Engine.TrackKey[] frames=new Engine.TrackKey[fs.length()];double last=-1;
                for(int k=0;k<frames.length;k++){
                    JSONObject f=fs.getJSONObject(k);double ft=f.getDouble("time"),x=f.getDouble("x"),alpha=f.getDouble("alpha");String ease=f.optString("ease","linear");
                    if(!finite(ft)||ft<0||ft<=last||ft*bpm/60>600000||!finite(x)||x<0||x>1||!finite(alpha)||alpha<0||alpha>1||!(ease.equals("linear")||ease.equals("smooth")||ease.equals("hold")))throw new IOException("轨迹关键帧数据无效");
                    frames[k]=new Engine.TrackKey(ft,x,alpha,ease);last=ft;
                }
                Engine.Track line=new Engine.Track(id,l.optString("name",id),frames);
                if(l.has("events")){
                    if(version<4)throw new IOException("轨迹区间事件需要 v4 格式");
                    JSONObject channels=l.getJSONObject("events");Iterator<String> names=channels.keys();
                    while(names.hasNext()){
                        String prop=names.next();if(!prop.equals("x")&&!prop.equals("alpha")&&!prop.equals("speed"))throw new IOException("无效轨迹变化栏");
                        JSONArray events=channels.getJSONArray(prop);if(events.length()>256)throw new IOException("每栏最多 256 个区间");Engine.Event[] parsed=new Engine.Event[events.length()];double previousEnd=0,max=prop.equals("speed")?8:1;
                        for(int k=0;k<parsed.length;k++){
                            JSONObject event=events.getJSONObject(k);double start=event.getDouble("start"),end=event.getDouble("end"),from=event.getDouble("from"),to=event.getDouble("to");String ease=event.optString("ease","linear");
                            if(!finite(start)||!finite(end)||!finite(from)||!finite(to)||start<0||end<=start||start<previousEnd-1e-8||end*bpm/60>600000||from<0||from>max||to<0||to>max||!Engine.eventEase(ease))throw new IOException("轨迹区间无效、重叠或超出范围");
                            parsed[k]=new Engine.Event(start,end,from,to,ease);previousEnd=end;
                        }
                        if(prop.equals("x"))line.position=new Engine.Curve(frames[0].x,parsed);else if(prop.equals("alpha"))line.opacity=new Engine.Curve(frames[0].alpha,parsed);else line.speed=new Engine.Curve(1,parsed);
                    }
                }
                lines.add(line);lineMap.put(id,line);
            }
        }
        for(int i=0;i<ns.length();i++){
            JSONObject n=ns.getJSONObject(i);String type=n.getString("type");if(!type.equals("tap")&&!type.equals("hold"))throw new IOException("仅支持 tap / hold");
            double t=n.getDouble("time"),d=type.equals("hold")?n.getDouble("duration"):0;
            Engine.Track line;
            if(version>=3){line=lineMap.get(n.getString("line"));if(line==null)throw new IOException("音符引用不存在的轨迹");}
            else{double oldX=n.getDouble("x");if(!finite(oldX)||oldX<0||oldX>1)throw new IOException("音符位置无效");String id="legacy_"+oldX;line=lineMap.get(id);if(line==null){line=new Engine.Track(id,id,new Engine.TrackKey(0,oldX,1,"linear"));lineMap.put(id,line);lines.add(line);}}
            double x=line.x(t);
            if(!finite(t)||!finite(d)||!finite(x)||t<0||x<0||x>1||d<0||(type.equals("hold")&&d<=0)||t+offset<-.001||t+d+offset>duration+.05)throw new IOException("音符数据无效或超出歌曲时长");
            notes.add(new Engine.Note(i,t,line,d));
        }
        notes.sort(Comparator.comparingDouble(n->n.time));double previous=0;
        int frameCount=0;
        for(int i=0;i<ss.length();i++){
            JSONObject s=ss.getJSONObject(i);double start=s.getDouble("start"),end=s.getDouble("end");
            if(!finite(start)||!finite(end)||Math.abs(start-previous)>.0001||end<=start)throw new IOException("区域段落必须连续且不重叠");
            previous=end;JSONArray rs=s.getJSONArray("rects");if(rs.length()>32)throw new IOException("每段最多 32 个矩形");Engine.Zone[] zones=new Engine.Zone[rs.length()];
            JSONArray motions=s.has("motions")?s.getJSONArray("motions"):null;
            if(motions!=null&&motions.length()!=0&&motions.length()!=rs.length())throw new IOException("运动列表必须与矩形对应");
            JSONArray regionEvents=s.has("events")?s.getJSONArray("events"):null;
            if(regionEvents!=null&&regionEvents.length()>0&&(version<5||regionEvents.length()!=rs.length()))throw new IOException("区域时间条需要 v5 格式且与矩形一一对应");
            for(int k=0;k<rs.length();k++){
                JSONArray r=rs.getJSONArray(k);if(r.length()!=4)throw new IOException("区域坐标无效");double l=r.getDouble(0),t=r.getDouble(1),right=r.getDouble(2),b=r.getDouble(3);
                if(!finite(l)||!finite(t)||!finite(right)||!finite(b)||l<0||t<0||right>1||b>1||right<=l||b<=t)throw new IOException("区域数据无效");
                JSONArray frames=motions!=null&&motions.length()>0?motions.getJSONArray(k):new JSONArray();
                if(frames.length()>256||(frameCount+=frames.length())>20000||(version==1&&frames.length()>0))throw new IOException("动画需要 v2 格式，每矩形最多 256 帧，全谱最多 20000 帧");
                Engine.Frame[] keys=new Engine.Frame[frames.length()];double last=-1;
                for(int m=0;m<keys.length;m++){
                    JSONObject f=frames.getJSONObject(m);double ft=f.getDouble("time"),x=f.getDouble("x"),y=f.getDouble("y"),angle=f.getDouble("angle");String ease=f.optString("ease","linear");
                    if(!finite(ft)||ft<0||ft<=last||ft*bpm/60>600000||!finite(x)||Math.abs(x)>2||!finite(y)||Math.abs(y)>2||!finite(angle)||Math.abs(angle)>36000||!(ease.equals("linear")||ease.equals("smooth")||ease.equals("hold")))throw new IOException("区域关键帧数据无效");
                    keys[m]=new Engine.Frame(ft,x,y,angle,ease);last=ft;
                }
                Engine.Zone zone=new Engine.Zone((float)l,(float)t,(float)right,(float)b,keys);zones[k]=zone;
                if(regionEvents!=null&&regionEvents.length()>0){
                    JSONObject channels=regionEvents.getJSONObject(k);Iterator<String> names=channels.keys();double[] initial=zone.geometry(0);
                    while(names.hasNext()){
                        String prop=names.next();if(!Arrays.asList("center","angle","width","height").contains(prop))throw new IOException("无效区域变化栏");
                        JSONArray events=channels.getJSONArray(prop);if(events.length()>256||(frameCount+=events.length())>20000)throw new IOException("区域时间条超过上限");
                        boolean center=prop.equals("center");Engine.Event[][] parsed=new Engine.Event[center?2:1][events.length()];double previousEnd=0;
                        double low=center?-2:prop.equals("angle")?-36000:.001,high=center?3:prop.equals("angle")?36000:4;
                        for(int m=0;m<events.length();m++){
                            JSONObject event=events.getJSONObject(m);double a=event.getDouble("start"),z=event.getDouble("end");String ease=event.optString("ease","linear");
                            if(!finite(a)||!finite(z)||a<0||z<=a||a<previousEnd-1e-8||z*bpm/60>600000||!Engine.eventEase(ease))throw new IOException("区域时间条时间无效或重叠");previousEnd=z;
                            if(center&&(event.getJSONArray("from").length()!=2||event.getJSONArray("to").length()!=2))throw new IOException("中心点需要 [X,Y]");
                            for(int component=0;component<parsed.length;component++){
                                double from=center?event.getJSONArray("from").getDouble(component):event.getDouble("from"),to=center?event.getJSONArray("to").getDouble(component):event.getDouble("to");
                                if(!finite(from)||!finite(to)||from<low||from>high||to<low||to>high)throw new IOException("区域起止值超出范围");
                                parsed[component][m]=new Engine.Event(a,z,from,to,ease);
                            }
                        }
                        if(center){zone.centerX=new Engine.Curve(initial[0],parsed[0]);zone.centerY=new Engine.Curve(initial[1],parsed[1]);}
                        else if(prop.equals("angle"))zone.angleCurve=new Engine.Curve(initial[2],parsed[0]);else if(prop.equals("width"))zone.widthCurve=new Engine.Curve(initial[3],parsed[0]);else zone.heightCurve=new Engine.Curve(initial[4],parsed[0]);
                    }
                }
            }
            sections.add(new Engine.Section(start,end,s.optString("label","区域"),zones));
        }
        if(previous<duration-offset)throw new IOException("区域未覆盖整首歌曲");
        engine=new Engine(notes,sections,lines);
    }
    private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
    public static String read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(bytes.size()+n>limit)throw new IOException("文件过大");bytes.write(buffer,0,n);}return bytes.toString("UTF-8");
    }
}
