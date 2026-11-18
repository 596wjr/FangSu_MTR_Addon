Add-Type -AssemblyName System.Drawing
$modelFolder=Join-Path $PSScriptRoot '../../common/src/main/resources/assets/fangsu/ticketing'
$image=[System.Drawing.Bitmap]::new(1000,720);$canvas=[System.Drawing.Graphics]::FromImage($image)
$canvas.Clear([System.Drawing.Color]::FromArgb(226,230,232));$canvas.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$font=[System.Drawing.Font]::new('Microsoft YaHei',16,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
function Rotate-Vertex($p,$element) {
    $o=$element.origin;if(-not $o){$o=@(0,0,0)};$r=$element.rotation;if(-not $r){$r=@(0,0,0)}
    $x=$p[0]-$o[0];$y=$p[1]-$o[1];$z=$p[2]-$o[2]
    $angle=$r[0]*[Math]::PI/180;$ny=$y*[Math]::Cos($angle)-$z*[Math]::Sin($angle);$nz=$y*[Math]::Sin($angle)+$z*[Math]::Cos($angle);$y=$ny;$z=$nz
    $angle=$r[1]*[Math]::PI/180;$nx=$x*[Math]::Cos($angle)+$z*[Math]::Sin($angle);$nz=-$x*[Math]::Sin($angle)+$z*[Math]::Cos($angle);$x=$nx;$z=$nz
    $angle=$r[2]*[Math]::PI/180;$nx=$x*[Math]::Cos($angle)-$y*[Math]::Sin($angle);$ny=$x*[Math]::Sin($angle)+$y*[Math]::Cos($angle)
    return ,@(($nx+$o[0]),($ny+$o[1]),($z+$o[2]))
}
function Project-Vertex($p,$offset) {return [System.Drawing.PointF]::new([single]($offset-13*(.94*$p[0]+.342*$script:viewSign*$p[2])),[single](602-13*$p[1]+2*(.342*$script:viewSign*$p[0]-.94*$p[2])))}
function Add-Face($list,$points,$uv,$texture,$element) {
    $p=@($points|ForEach-Object {Rotate-Vertex $_ $element})
    if($p.Count -lt 3){return}
    $a=@(($p[1][0]-$p[0][0]),($p[1][1]-$p[0][1]),($p[1][2]-$p[0][2]));$b=@(($p[2][0]-$p[0][0]),($p[2][1]-$p[0][1]),($p[2][2]-$p[0][2]))
    $normal=@(($a[1]*$b[2]-$a[2]*$b[1]),($a[2]*$b[0]-$a[0]*$b[2]),($a[0]*$b[1]-$a[1]*$b[0]))
    if($normal[0]*.342*$script:viewSign+$normal[1]*.16-$normal[2]*.94 -le 0){return}
    $depth=0;foreach($v in $p){$depth+=.342*$script:viewSign*$v[0]-.94*$v[2]+.16*$v[1]}
    # 主屏底图与正面平行且处于其前方；按覆盖层绘制，避免画家排序在长斜面上错误地压住半块屏幕。
    if($element.name -eq '主屏幕预览底图'){$depth+=10000}
    $list.Add(@{points=$p;uv=$uv;texture=$texture;depth=$depth/$p.Count})
}
for($m=0;$m -lt 2;$m++) {
    $model=Get-Content -Raw -LiteralPath (Join-Path $modelFolder @('ticket_machine_new.bbmodel','ticket_machine_new.bbmodel')[$m]) | ConvertFrom-Json
    $polygons=[System.Collections.Generic.List[object]]::new();$offset=260+480*$m;$script:viewSign=if($m -eq 0){1}else{-1}
    foreach($e in $model.elements) {
        if($e.type -eq 'mesh') {
            foreach($property in $e.faces.PSObject.Properties) {
                $face=$property.Value;$points=@($face.vertices | ForEach-Object {,($e.vertices.$_)});$uv=@($face.vertices | ForEach-Object {,($face.uv.$_)})
                Add-Face $polygons $points $uv $face.texture $e
            }
        } else {
            $a=$e.from;$b=$e.to
            foreach($property in $e.faces.PSObject.Properties) {
                $face=$property.Value
                $p=switch($property.Name) {
                    'north' {@(@($a[0],$b[1],$a[2]),@($b[0],$b[1],$a[2]),@($b[0],$a[1],$a[2]),@($a[0],$a[1],$a[2]))}
                    'south' {@(@($b[0],$b[1],$b[2]),@($a[0],$b[1],$b[2]),@($a[0],$a[1],$b[2]),@($b[0],$a[1],$b[2]))}
                    'east' {@(@($b[0],$b[1],$b[2]),@($b[0],$a[1],$b[2]),@($b[0],$a[1],$a[2]),@($b[0],$b[1],$a[2]))}
                    'west' {@(@($a[0],$b[1],$a[2]),@($a[0],$a[1],$a[2]),@($a[0],$a[1],$b[2]),@($a[0],$b[1],$b[2]))}
                    'up' {@(@($a[0],$b[1],$a[2]),@($a[0],$b[1],$b[2]),@($b[0],$b[1],$b[2]),@($b[0],$b[1],$a[2]))}
                    'down' {@(@($a[0],$a[1],$b[2]),@($a[0],$a[1],$a[2]),@($b[0],$a[1],$a[2]),@($b[0],$a[1],$b[2]))}
                }
                $u=$face.uv
                if($property.Name -in @('north','south')){$uv=@(@($u[2],$u[1]),@($u[0],$u[1]),@($u[0],$u[3]),@($u[2],$u[3]))}
                else{$uv=@(@($u[0],$u[1]),@($u[0],$u[3]),@($u[2],$u[3]),@($u[2],$u[1]))}
                Add-Face $polygons $p $uv $face.texture $e
            }
        }
    }
    foreach($polygon in ($polygons | Sort-Object {$_.depth})) {
        $points=[System.Drawing.PointF[]]@($polygon.points|ForEach-Object {Project-Vertex $_ $offset})
        $texture=[System.Drawing.Image]::FromFile((Join-Path $modelFolder $model.textures[$polygon.texture].relative_path))
        $xs=@($polygon.uv|ForEach-Object {$_[0]});$ys=@($polygon.uv|ForEach-Object {$_[1]})
        $u1=($xs|Measure-Object -Minimum).Minimum;$u2=($xs|Measure-Object -Maximum).Maximum;$v1=($ys|Measure-Object -Minimum).Minimum;$v2=($ys|Measure-Object -Maximum).Maximum
        if($u2-$u1 -lt .001 -or $v2-$v1 -lt .001){$texture.Dispose();continue}
        $source=[System.Drawing.RectangleF]::new([single]($u1/2048*$texture.Width),[single]($v1/2048*$texture.Height),[single](($u2-$u1)/2048*$texture.Width),[single](($v2-$v1)/2048*$texture.Height))
        $path=[System.Drawing.Drawing2D.GraphicsPath]::new();$path.AddPolygon($points);$save=$canvas.Save();$canvas.SetClip($path)
        $uv0=$polygon.uv[0];$uv1=$polygon.uv[1];$uv2=$polygon.uv[2]
        $du1=$uv1[0]-$uv0[0];$dv1=$uv1[1]-$uv0[1];$du2=$uv2[0]-$uv0[0];$dv2=$uv2[1]-$uv0[1];$det=$du1*$dv2-$dv1*$du2
        if([Math]::Abs($det) -lt 0.0001){$canvas.Restore($save);$path.Dispose();$texture.Dispose();continue}
        $dest=[System.Drawing.PointF[]]@(
            foreach($corner in @(@($u1,$v1),@($u2,$v1),@($u1,$v2))) {
                $cu=$corner[0]-$uv0[0];$cv=$corner[1]-$uv0[1];$s=($cu*$dv2-$cv*$du2)/$det;$t=($du1*$cv-$dv1*$cu)/$det
                [System.Drawing.PointF]::new([single]($points[0].X+$s*($points[1].X-$points[0].X)+$t*($points[2].X-$points[0].X)),[single]($points[0].Y+$s*($points[1].Y-$points[0].Y)+$t*($points[2].Y-$points[0].Y)))
            }
        )
        $canvas.DrawImage($texture,$dest,$source,[System.Drawing.GraphicsUnit]::Pixel)
        $canvas.Restore($save);$path.Dispose();$texture.Dispose()
    }
    $canvas.DrawString(@('新版：左前视角 / 122三角面','新版：右前视角 / 122三角面')[$m],$font,[System.Drawing.Brushes]::DarkSlateGray,$offset-200,660)
}
$canvas.DrawString('新版售票机：修复两侧壳体 / 屏幕预览底图',$font,[System.Drawing.Brushes]::DarkSlateGray,280,35)
$image.Save((Join-Path $PSScriptRoot 'machine-new-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$font.Dispose();$canvas.Dispose();$image.Dispose()
