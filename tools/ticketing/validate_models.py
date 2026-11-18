"""验证保存后的 bbmodel，而非生成器中尚未序列化的坐标。"""
from pathlib import Path
import json, math, struct

assets=Path(__file__).resolve().parents[2]/'common/src/main/resources/assets/fangsu/ticketing'
model=json.loads((assets/'ticket_machine_new.bbmodel').read_text(encoding='utf-8'))
triangles=[]
def sub(a,b):return [a[i]-b[i]for i in range(3)]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
for e in model['elements']:
    # 排除覆盖在闭合壳体外的装饰件，避免它们遮住壳体错误。
    if e['name'].startswith(('屏幕','主屏幕','八边形','按钮','扫码','凭条')):continue
    points=list(e['vertices'].values())
    if e['name'].startswith('壳体边'):
        n=cross(sub(points[1],points[0]),sub(points[2],points[0]))
        distance=abs(sum(n[i]*(points[3][i]-points[0][i])for i in range(3)))/math.sqrt(sum(v*v for v in n))
        assert distance<1e-6,('壳体四角不共面，可能对角错接',e['name'],distance)
    for f in e['faces'].values():
        assert len(f['vertices'])==3,('非显式三角面',e['name'])
        a,b,c=[e['vertices'][k]for k in f['vertices']]
        n=cross(sub(b,a),sub(c,a))
        assert sum(v*v for v in n)>1e-12,('零面积面',e['name'])
        triangles.append((a,b,c,n))

def hit(axis,u,v,positive):
    other=[i for i in range(3)if i!=axis];i,j=other;hits=[]
    for a,b,c,n in triangles:
        if (n[axis] if positive else -n[axis])<1e-8:continue
        d=(b[i]-a[i])*(c[j]-a[j])-(b[j]-a[j])*(c[i]-a[i])
        if abs(d)<1e-9:continue
        s=((u-a[i])*(c[j]-a[j])-(v-a[j])*(c[i]-a[i]))/d
        t=((b[i]-a[i])*(v-a[j])-(b[j]-a[j])*(u-a[i]))/d
        if min(s,t,1-s-t)>=-1e-7:hits.append(a[axis]+s*(b[axis]-a[axis])+t*(c[axis]-a[axis]))
    return (max(hits)if positive else min(hits))if hits else None

samples=0
def check(axis,u,v,positive,expected):
    global samples
    got=hit(axis,u,v,positive);samples+=1
    assert got is not None and abs(got-expected)<1e-5,('壳体开口/错位/反面',axis,u,v,positive,got,expected)

for row in range(64):
    y=(row+.5)/2
    front=-5+max(0,y-13)/19*1.85
    width=min(8,39-y)
    for depth in range(12):
        z=front+.05+(5.4-front)*(depth+.5)/12
        check(0,y,z,True,width);check(0,y,z,False,-width)
    for col in range(24):
        x=-width+2*width*(col+.5)/24
        check(2,x,y,True,5.5)
for col in range(24):
    x=-7.95+15.9*(col+.5)/24
    for depth in range(12):
        z=-3.0+8.45*(depth+.5)/12
        check(1,x,z,True,min(32,39-abs(x)))
        check(1,x,z,False,0)

old=json.loads((assets/'ticket_machine_old.bbmodel').read_text(encoding='utf-8'))
assert len(old['elements'])==16
for e in old['elements']:
    assert set(e['faces'])==set(['north','south','east','west','up','down']),('Blockbench 导入会中断',e['name'])
assert old['textures'][2]['relative_path']=='machine_led_blank.png'
assert any(e['name']=='主屏幕预览底图'for e in model['elements'])
for m in [old,model]:
    for t in m['textures']:
        data=(assets/t['relative_path']).read_bytes();w,h=struct.unpack('>II',data[16:24])
        assert max(w,h)<=2048 and (t['width'],t['height'])==(w,h)
print(f'PASS: {samples} 双侧/顶部/底部/背部射线检查；壳体共面；旧版导入完整；LED 无字底图；新版屏幕预览；贴图 <=2048。')
