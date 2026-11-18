param([string]$AssetFolder = (Join-Path $PSScriptRoot '../../common/src/main/resources/assets/fangsu/ticketing'))
# 只生成编辑器预览界面和无字 LED 屏底，实际服务文本由 TicketMachineRenderer 动态绘制。
Add-Type -AssemblyName System.Drawing
$image=[System.Drawing.Bitmap]::new(512,896)
$g=[System.Drawing.Graphics]::FromImage($image)
$g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
function Fill($color,$x,$y,$w,$h) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillRectangle($brush,$x,$y,$w,$h);$brush.Dispose()
}
function Text($text,$size,$color,$x,$y,$bold=$false) {
    $style=if($bold){[System.Drawing.FontStyle]::Bold}else{[System.Drawing.FontStyle]::Regular}
    $font=[System.Drawing.Font]::new('Microsoft YaHei',$size,$style,[System.Drawing.GraphicsUnit]::Pixel)
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.DrawString($text,$font,$brush,$x,$y);$brush.Dispose();$font.Dispose()
}
Fill '#f1f4f7' 0 0 512 896
Fill '#142333' 0 0 512 156
Text '正常服务' 52 '#ffc143' 142 24 $true
Text 'In Service' 37 '#ffc143' 160 86
Fill '#e5edf3' 0 156 512 70
Text '北京地铁' 27 '#4596cc' 23 168 $true
Text 'BEIJING SUBWAY' 12 '#4596cc' 25 201 $true
Text '欢迎乘坐北京地铁' 16 '#8295a5' 260 182
Text '— 请选择您需要的服务 —' 23 '#51616f' 104 260
$labels=@('购票','充值','一卡通升级','票卡查询','网上取票','电子发票导引')
$colors=@('#ec343c','#8587e1','#3b99d5','#29bbc7','#7e9cce','#e9a30a')
for($i=0;$i -lt 6;$i++) {
    $x=90+($i%2)*173;$y=326+[Math]::Floor($i/2)*145
    Fill $colors[$i] $x $y 161 132
    Text $labels[$i] $(if($i -ge 4){21}else{25}) '#ffffff' ($x+12) ($y+25) $true
}
Text '单程票每人一票，本站当日有效' 13 '#c57474' 112 784
Text '购票支持现金、扫码及银行卡支付' 13 '#81909d' 110 815
Fill '#dce8f1' 393 843 94 30
Text 'English' 15 '#478ab7' 408 847
$image.Save((Join-Path $AssetFolder 'machine_screen_preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose();$image.Dispose()
$led=[System.Drawing.Bitmap]::new(128,16)
$lg=[System.Drawing.Graphics]::FromImage($led)
$lg.Clear([System.Drawing.Color]::FromArgb(14,21,17))
$dot=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(24,34,26))
for($x=1;$x -lt 128;$x+=2){for($y=1;$y -lt 16;$y+=2){$lg.FillRectangle($dot,$x,$y,1,1)}}
$led.Save((Join-Path $AssetFolder 'machine_led_blank.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$dot.Dispose();$lg.Dispose();$led.Dispose()
