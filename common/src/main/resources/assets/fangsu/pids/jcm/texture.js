/**
 * Texture 渲染对象，用于在 PIDS 中绘制贴图。
 *
 * 所有方法均返回 Texture 本身，支持链式调用。
 */
function Texture(comment) {
    this.type = "texture";
    this.comment = comment || "";

    this.x = 0;
    this.y = 0;
    this.w = 0;
    this.h = 0;

    this.textureId = null;
    this.colorValue = 0xffffff;

    this.u1 = 0;
    this.v1 = 0;
    this.u2 = 1;
    this.v2 = 1;

    this.matricesValue = null;
}

/**
 * 创建一个新的 Texture 对象
 */
Texture.create = function (comment) {
    return new Texture(comment);
};

/** 设置贴图左上角位置 */
Texture.prototype.pos = function (x, y) {
    this.x = x;
    this.y = y;
    return this;
};

/** 设置贴图显示大小 */
Texture.prototype.size = function (w, h) {
    this.w = w;
    this.h = h;
    return this;
};

/**
 * 设置贴图资源 ID
 *
 * ID 应指向 PNG 或 .mcmeta 文件
 */
Texture.prototype.texture = function (id) {
    this.textureId = id;
    return this;
};

/** 设置贴图颜色（RGB） */
Texture.prototype.color = function (color) {
    this.colorValue = color;
    return this;
};

/**
 * 设置 UV 坐标
 *
 * uv(u2, v2)
 * uv(u1, v1, u2, v2)
 */
Texture.prototype.uv = function (a, b, c, d) {
    if (arguments.length === 2) {
        this.u2 = a;
        this.v2 = b;
    } else if (arguments.length === 4) {
        this.u1 = a;
        this.v1 = b;
        this.u2 = c;
        this.v2 = d;
    }
    return this;
};

/** 应用矩阵变换 */
Texture.prototype.matrices = function (matrices) {
    this.matricesValue = matrices;
    return this;
};

/** 将贴图标记为需要渲染 */
Texture.prototype.draw = function (ctx) {
    if (ctx && typeof ctx.drawTexture === "function") {
        ctx.drawTexture(this);
    }
};
