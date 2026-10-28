package studio.interval;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Portable .interval archive: chart.json plus a single basename audio file. */
public final class ChartPackage {
    public static File importFile(Context context,InputStream source)throws Exception {
        File folder=new File(context.getFilesDir(),"charts/"+UUID.randomUUID());
        if(!folder.mkdirs())throw new IOException("无法创建谱面目录");
        boolean success=false;
        try(ZipInputStream zip=new ZipInputStream(source)){
            ZipEntry entry;Set<String> names=new HashSet<>();long total=0;
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();
                if(entry.isDirectory()||!name.matches("[A-Za-z0-9_.-]+")||name.equals(".")||name.equals("..")||!names.add(name)||names.size()>8)throw new IOException("谱面包目录或文件名无效");
                long size=0;byte[] buffer=new byte[32768];int count;
                try(OutputStream out=new FileOutputStream(new File(folder,name))){
                    while((count=zip.read(buffer))!=-1){total+=count;size+=count;if(total>260L*1024*1024||size>(name.equals("chart.json")?4L:256L)*1024*1024)throw new IOException("谱面包过大");out.write(buffer,0,count);}
                }
                zip.closeEntry();
            }
            File chart=new File(folder,"chart.json");new Chart(context,chart);success=true;return chart;
        }finally{if(!success){File[] files=folder.listFiles();if(files!=null)for(File file:files)file.delete();folder.delete();}}
    }
}
