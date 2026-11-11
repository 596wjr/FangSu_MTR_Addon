/* Copyright (c) 2026 Potato Curved Platform contributors. MIT; see META-INF/licenses/potato-platform/LICENSE. */
package dev.potato.curvedplatform.geometry;

import java.util.List;

/** 与 Potato 几何内核共用的设置，样式目录包含方速的扩展材质。 */
public record PlatformSettings(
        Style style, Profile profile, double edgeOffset, double rearCoverage, String rearBlock, String innerBlock,
        RearMode rearMode, int baseDepth, double endSupport, End end, boolean squareEnds, End squareEnd,
        LightMode lightMode, boolean closeSound, boolean openSound, int soundVolume,
        boolean customMaterial, String customBlock, RearMode customMode, SoundChoice closeChoice, SoundChoice openChoice,
        String firstEndBlock, String lastEndBlock, PreCloseAnnouncement preClose, ThroughAnnouncement through,
        ApproachDistances approach, ArrivalAnnouncement arrival
) {
    public static final String FOLLOW_PLATFORM = "potato_curved_platform:follow_platform";
    public static final int DEFAULT_VOLUME = 80;
    public static final PlatformSettings DEFAULT = new PlatformSettings(Style.HONG_KONG, Profile.FULL, 1.5, 1.0);
    public static final double EDGE_WIDTH = 1.0;
    public static final double SKIN_HEIGHT = 2.44140625E-4;

    public PlatformSettings(Style style, Profile profile, double offset, double coverage) {
        this(style, profile, offset, coverage, "", "");
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner) {
        this(style, profile, offset, coverage, rear, inner, RearMode.GRID, 0, 0.0, End.BOTH, true);
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner, RearMode mode, int depth, double support, End end, boolean square) {
        this(style, profile, offset, coverage, rear, inner, mode, depth, support, end, square, End.BOTH);
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner, RearMode mode, int depth, double support, End end, boolean square, End squareEnd) {
        this(style, profile, offset, coverage, rear, inner, mode, depth, support, end, square, squareEnd, false, false, false);
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner, RearMode mode, int depth, double support, End end, boolean square, End squareEnd, boolean lights, boolean close, boolean open) {
        this(style, profile, offset, coverage, rear, inner, mode, depth, support, end, square, squareEnd, lights, close, open, 80);
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner, RearMode mode, int depth, double support, End end, boolean square, End squareEnd, boolean lights, boolean close, boolean open, int volume) {
        this(style, profile, offset, coverage, rear, inner, mode, depth, support, end, square, squareEnd, lights ? LightMode.OPEN_PERIOD : LightMode.OFF, close, open, volume);
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner, RearMode mode, int depth, double support, End end, boolean square, End squareEnd, LightMode lighting, boolean close, boolean open, int volume) {
        this(style, profile, offset, coverage, rear, inner, mode, depth, support, end, square, squareEnd, lighting, close, open, volume, false, "minecraft:stone_bricks", RearMode.GRID);
    }

    public PlatformSettings(Style style, Profile profile, double offset, double coverage, String rear, String inner, RearMode mode, int depth, double support, End end, boolean square, End squareEnd, LightMode lighting, boolean close, boolean open, int volume, boolean custom, String block, RearMode customMode) {
        this(style, profile, offset, coverage, rear, inner, mode, depth, support, end, square, squareEnd, lighting, close, open, volume, custom, block, customMode, null, null);
    }

    public PlatformSettings(Style style, Profile profile, double edgeOffset, double rearCoverage, String rearBlock, String innerBlock, RearMode rearMode, int baseDepth, double endSupport, End end, boolean squareEnds, End squareEnd, LightMode lightMode, boolean closeSound, boolean openSound, int soundVolume, boolean customMaterial, String customBlock, RearMode customMode, SoundChoice closeChoice, SoundChoice openChoice) {
        this(style, profile, edgeOffset, rearCoverage, rearBlock, innerBlock, rearMode, baseDepth, endSupport, end, squareEnds, squareEnd, lightMode, closeSound, openSound, soundVolume, customMaterial, customBlock, customMode, closeChoice, openChoice, "", "");
    }

    public PlatformSettings(Style style, Profile profile, double edgeOffset, double rearCoverage, String rearBlock, String innerBlock, RearMode rearMode, int baseDepth, double endSupport, End end, boolean squareEnds, End squareEnd, LightMode lightMode, boolean closeSound, boolean openSound, int soundVolume, boolean customMaterial, String customBlock, RearMode customMode, SoundChoice closeChoice, SoundChoice openChoice, String firstEndBlock, String lastEndBlock) {
        this(style, profile, edgeOffset, rearCoverage, rearBlock, innerBlock, rearMode, baseDepth, endSupport, end, squareEnds, squareEnd, lightMode, closeSound, openSound, soundVolume, customMaterial, customBlock, customMode, closeChoice, openChoice, firstEndBlock, lastEndBlock, PreCloseAnnouncement.OFF);
    }

    public PlatformSettings(Style style, Profile profile, double edgeOffset, double rearCoverage, String rearBlock, String innerBlock, RearMode rearMode, int baseDepth, double endSupport, End end, boolean squareEnds, End squareEnd, LightMode lightMode, boolean closeSound, boolean openSound, int soundVolume, boolean customMaterial, String customBlock, RearMode customMode, SoundChoice closeChoice, SoundChoice openChoice, String firstEndBlock, String lastEndBlock, PreCloseAnnouncement preClose) {
        this(style, profile, edgeOffset, rearCoverage, rearBlock, innerBlock, rearMode, baseDepth, endSupport, end, squareEnds, squareEnd, lightMode, closeSound, openSound, soundVolume, customMaterial, customBlock, customMode, closeChoice, openChoice, firstEndBlock, lastEndBlock, preClose, ThroughAnnouncement.OFF, ApproachDistances.DEFAULT);
    }

    public PlatformSettings(Style style, Profile profile, double edgeOffset, double rearCoverage, String rearBlock, String innerBlock, RearMode rearMode, int baseDepth, double endSupport, End end, boolean squareEnds, End squareEnd, LightMode lightMode, boolean closeSound, boolean openSound, int soundVolume, boolean customMaterial, String customBlock, RearMode customMode, SoundChoice closeChoice, SoundChoice openChoice, String firstEndBlock, String lastEndBlock, PreCloseAnnouncement preClose, ThroughAnnouncement through, ApproachDistances approach) {
        this(style, profile, edgeOffset, rearCoverage, rearBlock, innerBlock, rearMode, baseDepth, endSupport, end, squareEnds, squareEnd, lightMode, closeSound, openSound, soundVolume, customMaterial, customBlock, customMode, closeChoice, openChoice, firstEndBlock, lastEndBlock, preClose, through, approach, ArrivalAnnouncement.OFF);
    }

    public PlatformSettings {
        if (preClose == null || through == null || approach == null || arrival == null) {
            throw new IllegalArgumentException("Missing pre-close announcement");
        }
        closeChoice = closeChoice == null ? SoundChoice.legacy(closeSound) : closeChoice;
        openChoice = openChoice == null ? SoundChoice.legacy(openSound) : openChoice;
        closeSound = closeChoice.mode() != SoundChoice.Mode.OFF;
        openSound = openChoice.mode() != SoundChoice.Mode.OFF;
        if (style == null || profile == null || !Double.isFinite(edgeOffset) || !Double.isFinite(rearCoverage) || edgeOffset < 0.5 || edgeOffset > 4.0 || rearCoverage < 0.0 || rearCoverage > 7.0 || rearMode == null || end == null || squareEnd == null || lightMode == null || customMode == null || baseDepth < 0 || baseDepth > 2 || !Double.isFinite(endSupport) || endSupport < 0.0 || endSupport > 7.0 || soundVolume < 0 || soundVolume > 100) {
            throw new IllegalArgumentException("Invalid platform settings");
        }
        rearBlock = normaliseId(rearBlock, style.defaultRearBlock());
        innerBlock = normaliseId(innerBlock, "");
        firstEndBlock = normaliseId(firstEndBlock, "");
        lastEndBlock = normaliseId(lastEndBlock, "");
        customBlock = customBlock == null ? "" : customBlock.trim();
        if (customBlock.length() > 192) {
            throw new IllegalArgumentException("Custom block ID too long");
        }
        if (!customBlock.isEmpty() && customBlock.indexOf(':') < 0 && customBlock.matches("[a-z0-9_./-]+")) {
            customBlock = "minecraft:" + customBlock;
        }
        if (customBlock.length() > 192) {
            throw new IllegalArgumentException("Custom block ID too long");
        }
        if (!customBlock.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            customBlock = "";
        }
    }

    public boolean followsPlatform(boolean first) {
        return FOLLOW_PLATFORM.equals(this.endBlock(first));
    }

    public PlatformSettings withSounds(SoundChoice close, SoundChoice open) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, close.mode() != SoundChoice.Mode.OFF, open.mode() != SoundChoice.Mode.OFF, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, close, open, this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, this.approach, this.arrival);
    }

    public boolean lights() {
        return this.lightMode != LightMode.OFF;
    }

    public PlatformSettings withLightMode(LightMode lighting) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, lighting, this.closeSound, this.openSound, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, this.approach, this.arrival);
    }

    public PlatformSettings withEffects(boolean lights, boolean close, boolean open) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, LightMode.legacy(lights), close, open, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, SoundChoice.legacy(close), SoundChoice.legacy(open), this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, this.approach, this.arrival);
    }

    public PlatformSettings withVolume(int volume) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, volume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, this.approach, this.arrival);
    }

    public PlatformSettings withCustomMaterial(boolean enabled, String block, RearMode mode) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, this.soundVolume, enabled, block, mode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, this.approach, this.arrival);
    }

    public PlatformSettings withEndMaterials(String first, String last) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, first, last, this.preClose, this.through, this.approach, this.arrival);
    }

    public PlatformSettings withPreClose(PreCloseAnnouncement value) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, value, this.through, this.approach, this.arrival);
    }

    public PlatformSettings withThrough(ThroughAnnouncement value) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, this.preClose, value, this.approach, this.arrival);
    }

    public PlatformSettings withApproach(ApproachDistances value) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, value, this.arrival);
    }

    public PlatformSettings withArrival(ArrivalAnnouncement value) {
        return new PlatformSettings(this.style, this.profile, this.edgeOffset, this.rearCoverage, this.rearBlock, this.innerBlock, this.rearMode, this.baseDepth, this.endSupport, this.end, this.squareEnds, this.squareEnd, this.lightMode, this.closeSound, this.openSound, this.soundVolume, this.customMaterial, this.customBlock, this.customMode, this.closeChoice, this.openChoice, this.firstEndBlock, this.lastEndBlock, this.preClose, this.through, this.approach, value);
    }

    public String endBlock(boolean first) {
        return first ? this.firstEndBlock : this.lastEndBlock;
    }

    public boolean sameGeometry(PlatformSettings other) {
        return this.withEffects(false, false, false).withVolume(100).withPreClose(PreCloseAnnouncement.OFF).withThrough(ThroughAnnouncement.OFF).withArrival(ArrivalAnnouncement.OFF).withApproach(ApproachDistances.DEFAULT).equals(other.withEffects(false, false, false).withVolume(100).withPreClose(PreCloseAnnouncement.OFF).withThrough(ThroughAnnouncement.OFF).withArrival(ArrivalAnnouncement.OFF).withApproach(ApproachDistances.DEFAULT));
    }

    private static String normaliseId(String id, String defaultId) {
        if (id == null || id.isBlank()) {
            return defaultId;
        }
        id = id.trim();
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (id.length() > 192 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid texture block ID");
        }
        return id;
    }

    public double top() {
        return this.profile == Profile.LOWER_SLAB ? 0.5 : 1.0;
    }

    public boolean slab() {
        return this.profile == Profile.SLAB || this.profile == Profile.LOWER_SLAB;
    }

    public enum Style {
        HONG_KONG("\u9999\u6e2f", "platform_hk_1"),
        UK("\u82f1\u56fd", "platform_uk_1"),
        NORTH_AMERICA_YELLOW("\u5317\u7f8e\u9ec4\u8272", "platform_na_1"),
        NORTH_AMERICA_BLACK("\u5317\u7f8e\u9ed1\u8272", "platform_na_2"),
        LONDON_TFL("\u4f26\u6566 TfL", "london_tfl"),
        LONDON_GAP("\u4f26\u6566 Mind the gap", "london_gap"),
        LONDON_ISLAND("\u4f26\u6566\u5c9b\u5f0f", "london_island"),
        LONDON_FLOOR("\u4f26\u6566\u5730\u677f", "london_floor"),
        LONDON_VARIANT("\u4f26\u6566\u5730\u677f\u53d8\u4f53", "london_variant"),
        TIANJIN_GRAY("\u5929\u6d25\u7070\u8272", "tianjin_gray"),
        TIANJIN_YELLOW("\u5929\u6d25\u9ec4\u8272", "tianjin_yellow"),
        TIANJIN_LINE11_GRAY("\u5929\u6d25 11 \u53f7\u7ebf\u7070\u8272", "tianjin_line11_gray"),
        TIANJIN_LINE11_YELLOW("\u5929\u6d25 11 \u53f7\u7ebf\u9ec4\u8272", "tianjin_line11_yellow"),
        METROPOLIS("Metropolis \u6807\u51c6", "metropolis"),
        METROPOLIS_SLIM("Metropolis \u7a84\u5f0f", "metropolis_slim"),
        MARBLE_BLUE("蓝色大理石", "marble_blue", "mtr:marble_blue"),
        MARBLE_SANDY("砂质大理石", "marble_sandy", "mtr:marble_sandy");

        public final String label;
        public final String texture;
        public final String mod;
        private final String blockMaterial;
        private final PlatformSource blockSource;

        Style(String label, String texture) {
            this(label, texture, "");
        }

        Style(String label, String texture, String blockMaterial) {
            this.blockMaterial = blockMaterial;
            this.blockSource = blockMaterial.isEmpty() ? null : new PlatformSource(0, 1, 1,
                    List.of(new PlatformSource.SourceBox(0, 0, 0, 1, 1, 1)), blockMaterial);
            this.label = label;
            this.texture = texture;
            this.mod = texture.startsWith("london_") ? "londonunderground" : (texture.startsWith("tianjin_") ? "tjmetro" : (texture.startsWith("metropolis") ? "metropolis" : "mtr"));
        }

        public PlatformSource source() {
            return blockSource == null ? PlatformSource.get(texture) : blockSource;
        }

        public String blockMaterial() {
            return blockMaterial;
        }

        public String defaultRearBlock() {
            return blockMaterial.isEmpty() ? "minecraft:stone" : blockMaterial;
        }
    }

    public enum Profile {
        SLAB("\u4e0a\u53f0\u9636"),
        FULL("\u5b8c\u6574\u65b9\u5757"),
        INDENTED("\u8fb9\u7f18\u7a81\u51fa / \u5185\u9677"),
        LOWER_SLAB("\u4e0b\u53f0\u9636"),
        SOURCE("\u539f\u6a21\u7ec4\u5f62\u5f0f");

        public final String label;

        private Profile(String label) {
            this.label = label;
        }
    }

    public enum RearMode {
        GRID("\u6807\u51c6"),
        CURVED("\u6cbf\u66f2\u7ebf");

        public final String label;

        private RearMode(String s) {
            this.label = s;
        }
    }

    public enum End {
        FIRST("\u9996\u7aef"),
        LAST("\u5c3e\u7aef"),
        BOTH("\u4e24\u7aef");

        public final String label;

        private End(String s) {
            this.label = s;
        }

        public boolean includes(boolean first) {
            return this == BOTH || this == (first ? FIRST : LAST);
        }
    }

    public enum LightMode {
        OFF("\u5173\u95ed", 1000, 500),
        MOVEMENT("\u5f00\u5173\u95e8\u65f6\u6fc0\u6d3b", 700, 400),
        OPEN_PERIOD("\u5f00\u95e8\u540e\u4e00\u76f4\u6fc0\u6d3b", 1000, 500),
        APPROACH("\u5217\u8f66\u63a5\u8fd1\u81f3\u9a76\u79bb", 1000, 500);

        public final String label;
        private final int on;
        private final int off;

        private LightMode(String label, int on, int off) {
            this.label = label;
            this.on = on;
            this.off = off;
        }

        public boolean flash(long elapsed) {
            return Math.floorMod(elapsed, this.on + this.off) < this.on;
        }

        public static LightMode legacy(boolean enabled) {
            return enabled ? OPEN_PERIOD : OFF;
        }
    }
}
