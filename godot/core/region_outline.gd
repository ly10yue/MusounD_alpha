extends RefCounted
## Exposed edges of a convex-polygon union; overlapping interiors never draw borders.
static func location(point: Vector2,polygon: PackedVector2Array) -> int:
	var sign_value=0; var boundary=false
	for i in range(polygon.size()):
		var a=polygon[i]; var b=polygon[(i+1)%polygon.size()]
		var cross=(b-a).cross(point-a)
		if absf(cross)<0.0000001: boundary=true; continue
		if sign_value!=0 and signf(cross)!=sign_value: return -1
		sign_value=signf(cross)
	return 0 if boundary else 1

static func edges(polygons: Array) -> Array:
	var result=[]
	for i in range(polygons.size()):
		var polygon=polygons[i]
		for k in range(polygon.size()):
			var a=polygon[k]; var b=polygon[(k+1)%polygon.size()]; var d=b-a; var cuts=[0.0,1.0]
			for j in range(polygons.size()):
				if j==i: continue
				for m in range(polygons[j].size()):
					var c=polygons[j][m]; var z=polygons[j][(m+1)%polygons[j].size()]; var e=z-c; var ca=c-a; var den=d.cross(e)
					if absf(den)>0.000000000001:
						var t=ca.cross(e)/den; var u=ca.cross(d)/den
						if t>0 and t<1 and u>=0 and u<=1: cuts.append(t)
					elif absf(ca.cross(d))<0.000000001:
						for point in [c,z]:
							var t=(point-a).dot(d)/d.length_squared()
							if t>0 and t<1: cuts.append(t)
			cuts.sort()
			for m in range(1,cuts.size()):
				var lo=cuts[m-1]; var hi=cuts[m]
				if hi-lo<0.000000001: continue
				var point=a+d*(lo+hi)/2.0; var hidden=false
				for j in range(polygons.size()):
					if j==i: continue
					var pos=location(point,polygons[j])
					if pos<0: continue
					if pos>0: hidden=true; break
					var normal=Vector2(-d.y,d.x).normalized()*0.00001
					if location(point-normal,polygons[j])>0 or j<i: hidden=true; break
				if not hidden: result.append([a+d*lo,a+d*hi])
	return result
