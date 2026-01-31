/**
 * Text 渲染对象，用于在 PIDS 中绘制文本。
 *
 * 所有方法均返回 Text 本身，支持链式调用。
 *
 * 示例：
 * Text.create()
 *   .pos(x, y)
 *   .size(w, h)
 *   .text("Hello")
 *   .draw(ctx);
 */
function Text(comment) {
    this.type = "text";
    this.comment = comment || "";

    this.x = 0;
    this.y = 0;
    this.w = 0;
    this.h = 0;

    this.content = "";
    this.scaleValue = 1;

    this.align = "left";
    this.shadow = false;
    this.italicValue = false;
    this.boldValue = false;

    this.overflow = "none";
    this.fontId = null;
    this.useMCFont = false;

    this.colorValue = 0xffffff;
    this.matricesValue = null;
}

/**
 * 创建一个新的 Text 对象
 * @param {string=} comment 可选的调试注释
 * @returns {Text}
 */
Text.create = function (comment) {
    return new Text(comment);
};

/**
 * 设置文本左上角位置
 */
Text.prototype.pos = function (x, y) {
    this.x = x;
    this.y = y;
    return this;
};

/**
 * 设置文本区域大小
 *
 * 注意：
 * scale() 会整体缩放文本组件，
 * 使用 scale 时应相应缩小 size。
 */
Text.prototype.size = function (w, h) {
    this.w = w;
    this.h = h;
    return this;
};

/**
 * 设置文本内容
 */
Text.prototype.text = function (str) {
    this.content = String(str);
    return this;
};

/**
 * 设置文本整体缩放
 * 默认值为 1
 */
Text.prototype.scale = function (i) {
    this.scaleValue = i;
    return this;
};

/** 文本左对齐（默认） */
Text.prototype.leftAlign = function () {
    this.align = "left";
    return this;
};

/** 文本居中对齐 */
Text.prototype.centerAlign = function () {
    this.align = "center";
    return this;
};

/** 文本右对齐 */
Text.prototype.rightAlign = function () {
    this.align = "right";
    return this;
};

/** 启用文本阴影 */
Text.prototype.shadowed = function () {
    this.shadow = true;
    return this;
};

/** 使用斜体样式 */
Text.prototype.italic = function () {
    this.italicValue = true;
    return this;
};

/** 使用粗体样式 */
Text.prototype.bold = function () {
    this.boldValue = true;
    return this;
};

/**
 * 文本溢出处理：
 * 拉伸溢出的轴以适应 size
 */
Text.prototype.stretchXY = function () {
    this.overflow = "stretchXY";
    return this;
};

/**
 * 文本溢出处理：
 * 等比缩放文本以适应 size
 */
Text.prototype.scaleXY = function () {
    this.overflow = "scaleXY";
    return this;
};

/**
 * 文本溢出处理：
 * 自动换行，不缩放
 */
Text.prototype.wrapText = function () {
    this.overflow = "wrap";
    return this;
};

/**
 * 文本溢出处理：
 * 跑马灯滚动显示
 */
Text.prototype.marquee = function () {
    this.overflow = "marquee";
    return this;
};

/** 使用原版 Minecraft 字体 */
Text.prototype.fontMC = function () {
    this.useMCFont = true;
    return this;
};

/**
 * 设置字体 ID
 *
 * 字体需通过 Minecraft 字体 JSON 加载。
 * 若 MTR 配置中关闭自定义字体，此设置无效。
 */
Text.prototype.font = function (id) {
    this.fontId = id;
    return this;
};

/**
 * 设置文本颜色（RGB）
 */
Text.prototype.color = function (color) {
    this.colorValue = color;
    return this;
};

/**
 * 应用矩阵变换
 */
Text.prototype.matrices = function (matrices) {
    this.matricesValue = matrices;
    return this;
};

/**
 * 将文本标记为需要渲染
 */
Text.prototype.draw = function (ctx) {
    if (ctx && typeof ctx.drawText === "function") {
        ctx.drawText(this);
    }
};
