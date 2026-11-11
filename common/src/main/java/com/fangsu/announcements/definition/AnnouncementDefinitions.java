package com.fangsu.announcements.definition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 不可变定义快照；凭据与连接地址绝不属于资源包定义。 */
public final class AnnouncementDefinitions {
    private AnnouncementDefinitions() { }
    public record Segment(String text, String when) { }
    public record LanguageVoice(String voice, String style) { }
    /** 一个变种是一整套语言配音；存在语言表时必须精确命中，不偷偷复用另一种语言音色。 */
    public record Variant(String id, String voice, String style, Map<String, LanguageVoice> languageVoices) {
        public LanguageVoice binding(String locale) {
            if (!languageVoices.isEmpty()) return languageVoices.get(locale);
            return voice.isEmpty() ? null : new LanguageVoice(voice, style);
        }
    }
    public record Template(String id, int revision, String event, String defaultVariant,
                           List<Variant> variants, Map<String, List<Segment>> locales,Set<String> requiredVariables) { }
    public record ReferenceSample(String resource,String text,String chineseNamesResource,String chineseNamesText) { }
    public record Voice(String id, int revision, String mode, Map<String, ReferenceSample> samples,
                        Map<String, String> presetVoices) { }
    public record Source(String pack, String resource) { }
    public record Snapshot(Map<String, Template> templates, Map<String, Voice> voices,
                           Map<String, List<Source>> sources, long revision) {
        public static Snapshot empty() { return new Snapshot(Map.of(), Map.of(), Map.of(), 0); }
    }
    public record ResolvedUtterance(String templateId, int templateRevision, String variantId,
                                    Voice voice, String locale, String displayText,
                                    String spokenText, String style,List<SpeechPart> parts) {
        public ResolvedUtterance { parts=List.copyOf(parts); }
    }
    /** variable 为空时为固定语句；站名等变量保持独立片段，不能先替换进整句再请求云端。 */
    public record SpeechPart(String variable,String displayText,String spokenText) { }
}
