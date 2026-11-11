package com.fangsu.announcements.resolve;

import com.fangsu.announcements.definition.AnnouncementDefinitions.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** 纯解析器，加载/预览定义不触发任何网络请求。 */
public final class AnnouncementResolver {
    public static final Set<String> VARIABLES = Set.of("tripDestination", "fullServiceDestination",
            "station", "nextStation", "route", "doorSide", "interchanges");
    public static final Set<String> CONDITIONS = Set.of("shortTurn", "allStops", "limitedService", "terminal", "hasInterchanges", "doorSideKnown",
            "firstStop","notTerminal","leftDoors","rightDoors","stationSuffixNeeded");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> STATION_NAMES=Set.of("tripDestination","fullServiceDestination","station","nextStation");

    public static String validateText(String text) {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("invalid_text");
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) if (!VARIABLES.contains(m.group(1))) throw new IllegalArgumentException("unknown_variable");
        if (PLACEHOLDER.matcher(text).replaceAll("").matches("(?s).*[{}].*")) throw new IllegalArgumentException("invalid_placeholder");
        return text;
    }

    public static Optional<ResolvedUtterance> resolve(Snapshot snapshot, String templateId, String variantId,
            String locale, Map<String, String> variables, Set<String> conditions) {
        return resolve(snapshot, templateId, variantId, locale, variables, conditions, null);
    }
    public static Optional<ResolvedUtterance> resolve(Snapshot snapshot, String templateId, String variantId,
            String locale, Map<String, String> variables, Set<String> conditions, String voiceOverride) {
        Template template = snapshot.templates().get(templateId);
        if (template == null) return Optional.empty();
        for(String variable:template.requiredVariables()){
            String value=variables.get(variable);
            if(value==null||value.isBlank()||value.length()>256||value.contains("{")||value.contains("}"))return Optional.empty();
            if(STATION_NAMES.contains(variable)&&StationNamePronunciation.select(value,locale).display().isBlank())return Optional.empty();
        }
        String selected = variantId == null ? template.defaultVariant() : variantId;
        Variant variant = template.variants().stream().filter(v -> v.id().equals(selected)).findFirst().orElse(null);
        if (variant == null) return Optional.empty();
        LanguageVoice binding = variant.binding(locale);
        if (binding == null && (voiceOverride == null || voiceOverride.isEmpty())) return Optional.empty();
        Voice voice = snapshot.voices().get(voiceOverride != null && !voiceOverride.isEmpty() ? voiceOverride : binding.voice());
        List<Segment> segments = template.locales().get(locale);
        if (voice == null || segments == null) return Optional.empty();
        StringJoiner text = new StringJoiner(" "), spoken = new StringJoiner(" ");
        List<SpeechPart> parts=new ArrayList<>();
        for (Segment segment : segments) {
            if (!segment.when().isEmpty() && !conditions.contains(segment.when())) continue;
            // 小交路附句必须有显式全程终点；缺变量时跳过整个句组。
            Matcher matcher = PLACEHOLDER.matcher(segment.text());
            StringBuilder expanded = new StringBuilder(), pronounced = new StringBuilder();int last=0;
            List<SpeechPart> segmentParts=new ArrayList<>();
            boolean missing = false;
            while (matcher.find()) {
                String value = variables.get(matcher.group(1));
                if (value == null || value.isBlank() || value.length() > 256 || value.contains("{") || value.contains("}")) { missing = true; break; }
                var name=STATION_NAMES.contains(matcher.group(1))?StationNamePronunciation.select(value,locale):new StationNamePronunciation.Name(value,value);
                if(name.display().isBlank()||name.spoken().isBlank()){missing=true;break;}
                addPart(segmentParts,"",segment.text().substring(last,matcher.start()),segment.text().substring(last,matcher.start()));
                if(matcher.group(1).equals("interchanges")){
                    // 对应旧 TRANS 逐线路片段，不能把不同换乘组合又合成为一条长变量。
                    String separator=Locale.forLanguageTag(locale).getLanguage().equals("zh")?"、":",\\s*";
                    for(String route:name.spoken().split(separator))addPart(segmentParts,matcher.group(1),route,route);
                }else addPart(segmentParts,matcher.group(1),name.display(),name.spoken());
                expanded.append(segment.text(),last,matcher.start()).append(name.display());
                pronounced.append(segment.text(),last,matcher.start()).append(name.spoken());last=matcher.end();
            }
            if (!missing) {
                String tail=segment.text().substring(last);addPart(segmentParts,"",tail,tail);
                expanded.append(tail);pronounced.append(tail);text.add(expanded.toString());spoken.add(pronounced.toString());parts.addAll(segmentParts);
            }
        }
        String resolved = Normalizer.normalize(text.toString(), Normalizer.Form.NFC).trim();
        String spokenText = Normalizer.normalize(spoken.toString(), Normalizer.Form.NFC).trim();
        if (resolved.isEmpty() || resolved.length() > 4096 || spokenText.isEmpty() || spokenText.length() > 4096 || parts.isEmpty() || parts.size()>128) return Optional.empty();
        return Optional.of(new ResolvedUtterance(template.id(), template.revision(), variant.id(), voice,
                locale, resolved, spokenText, binding == null ? "" : binding.style(),parts));
    }
    private static void addPart(List<SpeechPart> parts,String variable,String display,String spoken){
        // 句号等纯标点留在字幕里，不为一个标点创建付费请求。
        String speech=Normalizer.normalize(spoken,Normalizer.Form.NFC).strip().replaceFirst("^[\\p{P}\\s]+","");
        if(speech.codePoints().noneMatch(Character::isLetterOrDigit))return;
        parts.add(new SpeechPart(variable,display.strip(),speech));
    }
}
