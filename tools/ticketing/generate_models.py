from pathlib import Path
import json,uuid,math,copy,struct,argparse
parser=argparse.ArgumentParser(description='从原始方块模型及现有贴图生成低面数售票机 bbmodel。')
parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
roots=[parser.parse_args().root]
asset_folder=roots[0]/'common/src/main/resources/assets/fangsu/ticketing'
SIZE=2048
def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'fangsu/ticketing-realistic/'+name))
def finish(name,elements,textures):
    entries=[]
    for i,filename in enumerate(textures):
        w,h=struct.unpack('>II',(asset_folder/filename).read_bytes()[16:24])
        entries.append({'name':filename,'relative_path':filename,'id':str(i),'uuid':uid(filename),'width':w,'height':h,'uv_width':SIZE,'uv_height':SIZE})
    return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,'resolution':{'width':SIZE,'height':SIZE},'elements':elements,'outliner':[{'name':'main','origin':[0,0,0],'rotation':[0,0,0],'uuid':uid(name),'children':[e['uuid']for e in elements]}],'textures':entries}
def old_model(root):
    elements=[]
    for half,y in [('lower',0),('upper',16)]:
        data=json.loads((root/f'common/src/main/resources/assets/fangsu/models/block/ticket_machine_{half}.json').read_text())
        for i,source in enumerate(data['elements']):
            # bbmodel 导入器始终遍历六个方向；省略方向会使其在零厚度部件处中断导入。
            e={'type':'cube','name':f'原版_{half}_{i}','uuid':uid(f'old/{half}/{i}'),'box_uv':False,'autouv':0,'faces':{k:{'texture':None,'uv':[0,0,0,0]}for k in ['north','east','south','west','up','down']}}
            e['from']=[source['from'][0]-8,source['from'][1]+y,source['from'][2]-10.5]
            e['to']=[source['to'][0]-8,source['to'][1]+y,source['to'][2]-10.5]
            rotation=source.get('rotation',{});origin=rotation.get('origin',[0,0,0])
            e['origin']=[origin[0]-8,origin[1]+y,origin[2]-10.5];e['rotation']=[0,0,0]
            if rotation.get('axis') in 'xyz':e['rotation']['xyz'.index(rotation['axis'])]=rotation.get('angle',0)
            # 零厚度平面只保留有面积的面，去掉模型中无意义的白边。
            for face,info in source['faces'].items():
                if info['texture']=='#missing':continue
                if face in ['east','west'] and (e['from'][1]==e['to'][1] or e['from'][2]==e['to'][2]):continue
                if face in ['north','south'] and (e['from'][0]==e['to'][0] or e['from'][1]==e['to'][1]):continue
                if face in ['up','down'] and (e['from'][0]==e['to'][0] or e['from'][2]==e['to'][2]):continue
                uv=[v/16*SIZE for v in info['uv']]
                if info['texture']=='#2':
                    tex=2
                    uv=[0,0,SIZE/2,SIZE] if i==10 else [SIZE/2,0,SIZE,SIZE]
                elif info['texture']=='#0':
                    uv=[SIZE*.60+uv[0]*.40,uv[1],SIZE*.60+uv[2]*.40,uv[3]];tex=0
                else:
                    # 控制台和上部取票口使用原实拍印刷细节；柜门改用提亮后的中性银色。
                    tex=1 if half=='upper' and i in [3,4,8] or half=='lower' and i==0 else 0
                e['faces'][face]={'texture':tex,'uv':uv}
            elements.append(e)
    # 顶部说明条保留原印刷内容，背景金属由去绿偏色图集提供。
    uv=[0,.65*SIZE,.5325*SIZE,.72*SIZE]
    overlay_faces={k:{'texture':None,'uv':[0,0,0,0]}for k in ['north','east','south','west','up','down']}
    overlay_faces['north']={'texture':1,'uv':uv}
    elements.append({'type':'cube','name':'原版购票充值说明','uuid':uid('old/instructions'),'box_uv':False,'autouv':0,'from':[-8,26.798,-1.03],'to':[8,28.941,-1.03],'origin':[0,0,0],'rotation':[0,0,0],'faces':overlay_faces})
    return finish('原版几何优化售票机',elements,['legacy_atlas.png','legacy_print.png','machine_led_blank.png'])
def modern_model():
    elements=[]
    def uv_front(point):return [(point[0]+8)/16*(SIZE/2),(32-point[1])/32*SIZE]
    def metal_uvs(points):
        a=[points[1][i]-points[0][i]for i in range(3)];b=[points[2][i]-points[0][i]for i in range(3)]
        n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
        axis=max(range(3),key=lambda i:abs(n[i]))
        # 顶底面按X/Z展开，侧面按Z/Y展开，前后按X/Y展开；不会把顶面UV压成一条线。
        if axis==1:return [[SIZE*(.55+(p[0]+8)/16*.40),SIZE*(p[2]+5)/32]for p in points]
        if axis==0:return [[SIZE*(.55+(p[2]+5)/16*.40),SIZE*(32-p[1])/32]for p in points]
        return [[SIZE*(.55+(p[0]+8)/16*.40),SIZE*(32-p[1])/32]for p in points]
    def face(name,points,metal=False,uv=None,texture=0):
        # 每个元素持有独立坐标。共享 profile/ring 列表会在后面的镜像循环中被重复翻转，导致壳体对角错接。
        vertices={f'v{i}':list(p) for i,p in enumerate(points)}
        projected=uv if uv is not None else metal_uvs(points) if metal else [uv_front(p)for p in points]
        mapping={key:projected[i]for i,key in enumerate(vertices)}
        # Blockbench 的面处理按三角/四边形实现；显式三角化避免六边形被当成四边形重排而对角相连。
        keys=list(vertices)
        faces={f't{i-1}':{'vertices':[keys[0],keys[i],keys[i+1]],'uv':{k:mapping[k]for k in [keys[0],keys[i],keys[i+1]]},'texture':texture}for i in range(1,len(keys)-1)}
        elements.append({'type':'mesh','name':name,'uuid':uid('modern/'+name),'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces})
    def front_z(y):return -5+(max(13,min(32,y))-13)/19*1.85
    def panel(name,x1,y1,x2,y2,z=None,metal=False):
        a=front_z(y1) if z is None else z;b=front_z(y2) if z is None else z
        face(name,[[x1,y2,b],[x2,y2,b],[x2,y1,a],[x1,y1,a]],metal)
    # 壳体侧板跟随倾斜前面板，顶部两个倒角，不堆叠看不见的整方块。
    profile=[[x,y,front_z(y)]for x,y in [(-8,0),(-8,13),(-8,31),(-7,32),(7,32),(8,31),(8,13),(8,0)]]
    for i,p in enumerate(profile):
        q=profile[(i+1)%len(profile)]
        face('壳体边_'+str(i),[p,[p[0],p[1],5.5],[q[0],q[1],5.5],q],True)
    # 背板分为矩形和顶端倒角两部分，避免n边形扇面产生无效三角形。
    face('拉丝背板',[[-8,0,5.5],[8,0,5.5],[8,31,5.5],[-8,31,5.5]],True)
    face('拉丝背板上沿',[[-8,31,5.5],[8,31,5.5],[7,32,5.5],[-7,32,5.5]],True)
    face('黑色倾斜操作面板',[[x,y,front_z(y)]for x,y in [(-7.8,13),(-7.8,30.95),(-6.95,31.8),(6.95,31.8),(7.8,30.95),(7.8,13)]])
    # 金属边框有独立窄面，所有朝向使用同一图集的拉丝金属区。
    panel('左前下金属边框',-8,0,-7.8,13,-5,True)
    panel('右前下金属边框',7.8,0,8,13,-5,True)
    for name,outline in [
        ('左前上金属边框',[(-8,31),(-7.8,30.95),(-7.8,13),(-8,13)]),
        ('右前上金属边框',[(7.8,30.95),(8,31),(8,13),(7.8,13)]),
        ('左上倒角封边',[(-8,31),(-7,32),(-6.95,31.8),(-7.8,30.95)]),
        ('右上倒角封边',[(8,31),(7.8,30.95),(6.95,31.8),(7,32)]),
        ('顶部金属包边',[(-7,32),(7,32),(6.95,31.8),(-6.95,31.8)])]:
        face(name,[[x,y,front_z(y)]for x,y in outline],True)
    # 下机身按两个取票/读卡孔拆分；孔后有退入的内壁及底托。
    panel('下柜门',-7.8,0,7.8,10.15,-5)
    panel('取票口上横梁',-7.8,12.35,7.8,13,-5)
    for name,x1,x2 in [('左侧',-7.8,-6.1),('中央',-.75,1.05),('右侧',6.2,7.8)]:panel('取票口立柱'+name,x1,10.15,x2,12.35,-5)
    for label,x1,x2 in [('读卡托盘',-6.1,-.75),('出票托盘',1.05,6.2)]:
        y1,y2,z1,z2=10.15,12.35,-5,-2.8
        panel(label+'内背板',x1,y1,x2,y2,z2)
        face(label+'底托',[[x1,y1,z2],[x2,y1,z2],[x2,y1,z1],[x1,y1,z1]],True)
        face(label+'顶壁',[[x1,y2,z1],[x2,y2,z1],[x2,y2,z2],[x1,y2,z2]],True)
        face(label+'左内壁',[[x1,y1,z1],[x1,y2,z1],[x1,y2,z2],[x1,y1,z2]],True)
        face(label+'右内壁',[[x2,y1,z2],[x2,y2,z2],[x2,y2,z1],[x2,y1,z1]],True)
        for suffix,a,b,c,d in [('底唇',x1-.15,y1-.15,x2+.15,y1),('上唇',x1-.15,y2,x2+.15,y2+.15),('左唇',x1-.15,y1,x1,y2),('右唇',x2,y1,x2+.15,y2)]:panel(label+suffix,a,b,c,d,-5.1,True)
    # 玻璃及胶条具有深度，主屏幕另画在最外一层。
    for label,x1,y1,x2,y2 in [('屏幕左胶条',-4.38,15.45,-4.27,29.62),('屏幕右胶条',3.87,15.45,3.98,29.62),('屏幕上胶条',-4.38,29.55,3.98,29.65),('屏幕下胶条',-4.38,15.42,3.98,15.54)]:
        panel(label,x1,y1,x2,y2)
    # 编辑器中的屏幕预览底图；游戏动态屏幕位于其前方，覆盖此底图。
    face('主屏幕预览底图',[[-4.27,29.55,front_z(29.55)-.025],[3.87,29.55,front_z(29.55)-.025],[3.87,15.54,front_z(15.54)-.025],[-4.27,15.54,front_z(15.54)-.025]],uv=[[0,0],[SIZE,0],[SIZE,SIZE],[0,SIZE]],texture=1)
    # 八边形求助按钮：正面6三角、侧壁16三角。
    cx,cy,r=6.03,29.2,.60;cz=front_z(cy)-.16
    ring=[[cx+math.cos(i*math.pi/4)*r,cy+math.sin(i*math.pi/4)*r,cz]for i in range(8)]
    face('八边形求助按钮面',list(reversed(ring)))
    for i,p in enumerate(ring):
        q=ring[(i+1)%8];face('按钮侧壁'+str(i),[p,q,[q[0],q[1],q[2]+.16],[p[0],p[1],p[2]+.16]],True)
    # 扫码器金属外缘、黑色扫描窗通过图集还原，窗口略微内陷。
    panel('扫码窗',2.6,13.4,4.65,15.03,front_z(14.2)-.11)
    panel('凭条口上唇',-5.5,7.10,-1.1,7.3,-5.14,True)
    panel('凭条口下唇',-5.5,6.85,-1.1,7.0,-5.10,True)
    # 北向正面观看时世界X与图像左右相反；镜像几何并反转绕序，保留UV与每个部件的对应关系。
    for element in elements:
        for point in element['vertices'].values():point[0]=-point[0]
        for entry in element['faces'].values():entry['vertices'].reverse()
    return finish('新版仿真票卡一体机',elements,['modern_atlas.png','machine_screen_preview.png'])
def triangles(model):
    return sum(sum(max(0,len(f['vertices'])-2)for f in e['faces'].values()) if e['type']=='mesh' else sum(f.get('texture') is not None for f in e['faces'].values())*2 for e in model['elements'])
for root in roots:
    folder=root/'common/src/main/resources/assets/fangsu/ticketing';old,new=old_model(root),modern_model();budget={}
    for filename,model in [('ticket_machine_old.bbmodel',old),('ticket_machine_new.bbmodel',new),('card_machine.bbmodel',new)]:
        (folder/filename).write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
        budget[filename]={'triangles':triangles(model),'dynamic_screen_triangles':4 if filename=='ticket_machine_old.bbmodel'else 2,'texture_max_dimension':2048}
    (folder/'model_budget.json').write_text(json.dumps(budget,indent=2),encoding='utf-8',newline='\n')
    print(root.name,budget)
