package com.fangsu.train.connector;

/** 纯数值几何。输入 prev 原始四点、this 四点；不接触矩阵，不再叠加滚转。 */
public final class ConnectorGeometry {
    private ConnectorGeometry() {}
    public static final class Mesh {
        public double[][] rings;
        public double[][] centers;
        public final double[] a, b;
        public double[][] normals;
        public double[] axis;
        public int ringCount;
        private final int pointCount,edgeCount;
        public void ensure(int size) {
            ringCount=size;
            if(rings.length>=size) return;
            rings=new double[size][pointCount*3];centers=new double[size][3];normals=new double[size-1][edgeCount*3];
        }
        public boolean reverse;
        public Mesh(ConnectorDef d) {
            pointCount=d.points.length;edgeCount=d.closed?pointCount:pointCount-1;
            rings=new double[d.segments+1][d.points.length*3];
            centers=new double[d.segments+1][3];
            a=new double[d.points.length*3]; b=new double[a.length];
            normals=new double[d.segments][(d.closed?d.points.length:d.points.length-1)*3];
        }
    }
    /** corners 为 prev1..4,this1..4；末端环直接复制，避免端面浮点漂移。 */
    public static boolean build(ConnectorDef d, double[][] corners, float[] profile, double nominal, Mesh mesh) {
        for(double[] p:corners) for(double x:p) if(!Double.isFinite(x)) return false;
        mesh.axis=ConnectorFoldProfile.axis(d,profile);mesh.ensure(mesh.axis.length);
        int last=mesh.ringCount-1;
        double[][] p={corners[3],corners[2],corners[1],corners[0]};
        double[][] l={corners[4],corners[5],corners[6],corners[7]};
        double[] ca=center(p), cb=center(l), chord=sub(cb,ca);
        double distance=length(chord); if(distance<1e-4) return false;
        double[] n0=normal(p), n1=normal(l);
        if(length(n0)<1e-8 || length(n1)<1e-8) return false;
        // 镜像配对后两端法线可能同向；依据弦确定符号，不能无条件将 t1 取负。
        scale(n0,(dot(n0,chord)<0?-1:1)*distance/length(n0));
        scale(n1,(dot(n1,chord)<0?-1:1)*distance/length(n1));
        double area=0;
        for(int i=0;i<d.points.length;i++) { double[] x=d.points[i], y=d.points[(i+1)%d.points.length]; area+=x[0]*y[1]-y[0]*x[1]; }
        mesh.reverse=Math.abs(area)<1e-10?dot(normal(p),chord)>0:area*dot(normal(p),chord)<0;
        for(int i=0;i<d.points.length;i++) {
            map(p,d.points[i],mesh.a,i*3); map(l,d.points[i],mesh.b,i*3);
            for(int k=0;k<3;k++) {
                double inset=d.inset*chord[k]/distance;
                mesh.a[i*3+k]+=inset; mesh.b[i*3+k]-=inset;
            }
        }
        for(int j=0;j<=last;j++) {
            double t=mesh.axis[j], t2=t*t,t3=t2*t;
            double h00=2*t3-3*t2+1,h10=t3-2*t2+t,h01=-2*t3+3*t2,h11=t3-t2;
            double[] ring=mesh.rings[j], c=mesh.centers[j];
            c[0]=c[1]=c[2]=0;
            for(int i=0;i<d.points.length;i++) for(int k=0;k<3;k++) {
                int ix=i*3+k;
                ring[ix]=j==0?mesh.a[ix]:j==last?mesh.b[ix]:h00*mesh.a[ix]+h10*n0[k]+h01*mesh.b[ix]+h11*n1[k];
                c[k]+=ring[ix]/d.points.length;
            }
        }
        double amp=d.amplitude*(1+d.gain*(ConnectorDef.clamp(nominal/distance,1,3)-1));
        boolean folded=!d.source.equals("none") && (!d.source.equals("texture") || ConnectorFoldProfile.hasVariation(profile));
        for(int j=0;j<=last;j++) {
            double[] ring=mesh.rings[j], c=mesh.centers[j];
            double[] f=sub(mesh.centers[Math.min(last,j+1)],mesh.centers[Math.max(0,j-1)]); double fl=length(f);
            if(fl<1e-10) return false; scale(f,1/fl);
            double k=ConnectorFoldProfile.value(d,profile,mesh.axis[j]);
            // 每份配置独立确定径向位置和褶皱；内外层都不隐式依赖原版内壁。
            double fold=(j==0 || j==last || !folded)?0:amp*(k+1)*.5;
            fold+=d.offset;
            for(int i=0;i<d.points.length;i++) {
                int ix=i*3; double rx=ring[ix]-c[0],ry=ring[ix+1]-c[1],rz=ring[ix+2]-c[2];
                double projection=rx*f[0]+ry*f[1]+rz*f[2];
                rx-=projection*f[0]; ry-=projection*f[1]; rz-=projection*f[2];
                double len=Math.sqrt(rx*rx+ry*ry+rz*rz);
                if(len>1e-6) { ring[ix]+=rx*fold/len; ring[ix+1]+=ry*fold/len; ring[ix+2]+=rz*fold/len; }
            }
        }
        for(int j=0;j<=last;j++) for(double x:mesh.rings[j]) if(!Double.isFinite(x)) return false;
        // 每个四边形独立计算法线，折棱两侧不共享平均法线，保留折棚的硬折面。
        int count=d.closed?d.points.length:d.points.length-1;
        for(int j=0;j<last;j++) for(int i=0;i<count;i++) {
            int a=i*3,b=((i+1)%d.points.length)*3;
            double[] r0=mesh.rings[j],r1=mesh.rings[j+1],out=mesh.normals[j];
            double ax=r1[b]-r0[a],ay=r1[b+1]-r0[a+1],az=r1[b+2]-r0[a+2];
            double bx=r0[b]-r1[a],by=r0[b+1]-r1[a+1],bz=r0[b+2]-r1[a+2];
            double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(!Double.isFinite(len) || len<1e-12) return false;
            double sign=(mesh.reverse?-1:1)/len;
            out[a]=nx*sign;out[a+1]=ny*sign;out[a+2]=nz*sign;
        }
        return true;
    }
    public static double distance(double[][] c) {
        double x=0,y=0,z=0;
        for(int i=0;i<4;i++) { x+=(c[i+4][0]-c[i][0])/4; y+=(c[i+4][1]-c[i][1])/4; z+=(c[i+4][2]-c[i][2])/4; }
        return Math.sqrt(x*x+y*y+z*z);
    }
    public static double u(ConnectorDef d,int j,int i) {
        return u(d,j/(double)d.segments,i);
    }
    public static double u(ConnectorDef d,double t,int i) {
        return Double.isNaN(d.points[i][2]) ? d.uRange[0]+(d.uRange[1]-d.uRange[0])*t : d.points[i][2]+(d.uRange[1]-d.uRange[0])*t;
    }
    public static double v(ConnectorDef d,int i) {
        int point=i%d.points.length;
        return Double.isNaN(d.points[point][3]) ? d.vRange[0]+(d.vRange[1]-d.vRange[0])*d.perimeter[i] : d.points[point][3];
    }
    private static void map(double[][] r,double[] p,double[] out,int ix) {
        double u=p[0]+.5,v=p[1];
        for(int k=0;k<3;k++) {
            double bottom=r[0][k]+(r[3][k]-r[0][k])*u,top=r[1][k]+(r[2][k]-r[1][k])*u;
            out[ix+k]=bottom+(top-bottom)*v;
        }
    }
    private static double[] center(double[][] p) { double[] c=new double[3]; for(double[] v:p) for(int k=0;k<3;k++) c[k]+=v[k]/4; return c; }
    private static double[] normal(double[][] p) { double[] a=sub(p[1],p[0]),b=sub(p[3],p[0]); return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]}; }
    private static double[] sub(double[] a,double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double dot(double[] a,double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static double length(double[] a) { return Math.sqrt(dot(a,a)); }
    private static void scale(double[] a,double s) { for(int k=0;k<3;k++) a[k]*=s; }
}
