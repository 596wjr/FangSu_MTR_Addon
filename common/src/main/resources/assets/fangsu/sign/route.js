function draw(g, x, y, unit, duiqi, content) {
    g.setColor(content.color);
    g.fillRoundRect(x - (duiqi == 2 ? unit : 0), y, unit, unit, unit * 0.1, unit * 0.1);
    g.drawImage(loadResource("img", content.img), x - (duiqi == 2 ? unit : 0), y, unit, unit, null);
}
function getWidth(g, unit, content) {
    return unit;
}
