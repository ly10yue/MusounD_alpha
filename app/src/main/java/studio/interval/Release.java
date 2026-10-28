package studio.interval;
import org.json.*;import java.io.IOException;

/** Versioned file boundary. Runtime geometry is normalized; public v6 coordinates are 0–100. */
public final class Release {
    public static final String VERSION="0.12.0";
    public static final int FORMAT=6;
    private static void number(JSONObject o,String k,double factor)throws Exception{o.put(k,o.getDouble(k)*factor);}
    private static void pair(JSONObject e,boolean vector)throws Exception{
        for(String key:new String[]{"from","to"})if(vector){JSONArray v=e.getJSONArray(key);for(int i=0;i<v.length();i++)v.put(i,v.getDouble(i)*.01);}else number(e,key,.01);
    }
    public static void normalize(JSONObject j)throws Exception{
        int format=j.getInt("version");
        if(format<1||format>FORMAT)throw new IOException("需要更新的游戏版本（谱面格式 v"+format+"）");
        if(format<6){if(j.optDouble("coordinateScale",1)!=1)throw new IOException("旧格式坐标单位冲突");return;}
        if(j.getDouble("coordinateScale")!=100)throw new IOException("v6 坐标范围应为 0–100");
        String minimum=j.getString("minReaderVersion");String[] requested=minimum.split("\\."),current=VERSION.split("\\.");
        if(requested.length!=3)throw new IOException("最低版本号无效");
        boolean equal=true;for(int i=0;i<3;i++){int r=Integer.parseInt(requested[i]),c=Integer.parseInt(current[i]);if(r<0)throw new IOException("版本号无效");if(equal&&r>c)throw new IOException("此谱面需要游戏 "+minimum+" 或以上");if(r<c)equal=false;}
        JSONArray lines=j.getJSONArray("lines");
        for(int i=0;i<lines.length();i++){JSONObject line=lines.getJSONObject(i);JSONArray frames=line.getJSONArray("frames");for(int k=0;k<frames.length();k++)number(frames.getJSONObject(k),"x",.01);
            JSONObject channels=line.optJSONObject("events");if(channels!=null&&channels.has("x")){JSONArray es=channels.getJSONArray("x");for(int k=0;k<es.length();k++)pair(es.getJSONObject(k),false);}}
        JSONArray sections=j.getJSONArray("sections");
        for(int i=0;i<sections.length();i++){JSONObject section=sections.getJSONObject(i);JSONArray rects=section.getJSONArray("rects");
            for(int k=0;k<rects.length();k++){JSONArray r=rects.getJSONArray(k);for(int n=0;n<r.length();n++)r.put(n,r.getDouble(n)*.01);}
            JSONArray motions=section.optJSONArray("motions");if(motions!=null)for(int k=0;k<motions.length();k++){JSONArray frames=motions.getJSONArray(k);for(int n=0;n<frames.length();n++){JSONObject f=frames.getJSONObject(n);number(f,"x",.01);number(f,"y",.01);}}
            JSONArray events=section.optJSONArray("events");if(events!=null)for(int k=0;k<events.length();k++){JSONObject channels=events.getJSONObject(k);for(String prop:new String[]{"center","width","height"})if(channels.has(prop)){JSONArray es=channels.getJSONArray(prop);for(int n=0;n<es.length();n++)pair(es.getJSONObject(n),prop.equals("center"));}}
        }
        j.put("version",5);
    }
}
