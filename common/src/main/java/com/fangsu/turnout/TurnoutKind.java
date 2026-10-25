package com.fangsu.turnout;
import java.util.Locale;
/** 真实轨道拓扑支持的七种道岔，类型判定与几何构建共用。 */
public enum TurnoutKind {
    SINGLE, DOUBLE, THREE_WAY, DIAMOND, SINGLE_SLIP, DOUBLE_SLIP, SCISSORS_CROSSOVER, INVALID;
    public String langKey(){return "fangsu.turnout.kind."+name().toLowerCase(Locale.ROOT);}
}
