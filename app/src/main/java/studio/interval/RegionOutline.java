package studio.interval;
import android.graphics.Path;

/** One union for the visual opening and its outer contour; interior edges disappear. */
public final class RegionOutline {
    public static Path path(Engine.Section section,double time,double aspect,float left,float width,float height){
        Path union=new Path();
        for(Engine.Zone zone:section.zones){
            float[] points=zone.polygon(time,aspect);Path shape=new Path();shape.moveTo(left+points[0]*width,points[1]*height);
            for(int i=2;i<8;i+=2)shape.lineTo(left+points[i]*width,points[i+1]*height);
            shape.close();union.op(shape,Path.Op.UNION);
        }
        return union;
    }
}
