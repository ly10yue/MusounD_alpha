extends RefCounted
static func eased(u: float, ease: String) -> float:
	match ease:
		"sine_in_out": return (1.0-cos(PI*u))/2.0
		"sine_in": return 1.0-cos(PI*u/2.0)
		"sine_out": return sin(PI*u/2.0)
		"smooth": return u*u*(3.0-2.0*u)
		"hold": return 0.0
	return u

static func ease_integral(u: float, ease: String) -> float:
	match ease:
		"sine_in_out": return u/2.0-sin(PI*u)/(2.0*PI)
		"sine_in": return u-2.0/PI*sin(PI*u/2.0)
		"sine_out": return 2.0/PI*(1.0-cos(PI*u/2.0))
		"smooth": return u*u*u-u*u*u*u/2.0
		"hold": return 0.0
	return u*u/2.0
