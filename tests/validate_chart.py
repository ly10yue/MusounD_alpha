import json,pathlib,sys
root=pathlib.Path(__file__).resolve().parents[1]
c=json.loads((root/'app/src/main/assets/demo.json').read_text(encoding='utf-8'))
sys.path.insert(0,str(root/'editor'))
from model import Document
from versioning import decode
assert c['version']==6 and c['coordinateScale']==100
doc=Document.from_chart(c,str(root/'app/src/main/assets'/c['audio']))
c=decode(c)
assert doc.validate()[0]==[]
assert c['version']==5 and all('x' not in n and n['line'] in {t.id for t in doc.lines} for n in c['notes'])
def note_x(n):return doc.track(n['line']).pose(n['time']*c['bpm']/60)[0]
ns=c['notes'];sections=c['sections'];assert ns==sorted(ns,key=lambda n:n['time'])
assert {n['type'] for n in ns}=={'tap','hold'}
for i,s in enumerate(sections):
    assert s['start']<s['end']
    if i: assert abs(sections[i-1]['end']-s['start'])<1e-6
    for l,t,r,b in s['rects']:assert 0<=l<r<=1 and 0<=t<b<=1
for i,n in enumerate(ns):
    assert n['id']==i and 0<=n['time']<c['duration'] and n['time']+n['duration']<c['duration']
    s=next(s for s in sections if s['start']<=n['time']<s['end'])
    assert any(l<=note_x(n)<=r for l,t,r,b in s['rects'])
    assert n['time']+n['duration']<s['end'], 'hold crosses area change'
    active=[p for p in ns if p['time']<=n['time'] and p['time']+max(p['duration'],.001)>n['time']]
    assert len(active)<=2, f'too many fingers at {n["time"]}'
    for p in ns[i+1:]:
        if p['time']>n['time']+n['duration']+.18:break
        assert p['line']!=n['line'], 'overlapping input in same column'
for aspect in (16/9,4/3):
    width=600*aspect;field=width-96
    for n in ns:
        px=48+note_x(n)*field
        assert abs((px-48)/field-note_x(n))<1e-6
assert (root/'app/src/main/assets'/c['audio']).stat().st_size>1000000
print(f'PASS: {len(ns)} notes, {len(sections)} sections, two-finger playability, full audio, 16:9 / 4:3 coordinates')
# Data-only fixture to exercise the entire real chart in the JVM engine.
out=root/'build/chart-fixture.txt';out.parent.mkdir(exist_ok=True)
with out.open('w',encoding='utf-8') as f:
    for s in sections:
        f.write('S '+str(s['start'])+' '+str(s['end'])+' '+' '.join(str(v) for rect in s['rects'] for v in rect)+'\n')
    for line in c['lines']:
        f.write('L '+line['id']+' '+' '.join(f"{k['time']} {k['x']} {k['alpha']} {k['ease']}" for k in line['frames'])+'\n')
    for n in ns:f.write(f'N {n["id"]} {n["time"]} {n["line"]} {n["duration"]}\n')
