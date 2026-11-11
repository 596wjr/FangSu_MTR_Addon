package com.fangsu.announcements.resolve;

import com.ibm.icu.text.Transliterator;
import java.text.Normalizer;
import java.util.*;

/** 区分字幕站名与实际读音。拼音英文名读中文，有独立译名时保留英文。 */
public final class StationNamePronunciation {
    public record Name(String display,String spoken) { }
    private static final ThreadLocal<Transliterator> HAN_LATIN=ThreadLocal.withInitial(()->Transliterator.getInstance("Han-Latin"));
    private static final Map<String,String> PINYIN=new LinkedHashMap<>(256,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>512;}
    };
    public static boolean containsChinese(String text){return text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN);}
    private static String canonical(String text){
        return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}'’·-]+","");
    }
    public static boolean isPinyin(String chinese,String english){
        if(!containsChinese(chinese))return false;
        String candidate=canonical(english);if(!candidate.matches("[a-z0-9]+"))return false;
        String pinyin;
        synchronized(PINYIN){pinyin=PINYIN.get(chinese);}
        if(pinyin==null){
            pinyin=canonical(HAN_LATIN.get().transliterate(chinese));
            synchronized(PINYIN){PINYIN.put(chinese,pinyin);}
        }
        return candidate.equals(pinyin);
    }
    public static Name select(String raw,String locale){
        if(raw==null)return new Name("","");
        String[] parts=raw.split("\\|\\|",2)[0].split("\\|",-1);
        String chinese=parts[0].trim(),language=Locale.forLanguageTag(locale).getLanguage();
        if(language.equals("zh"))return new Name(chinese,chinese);
        if(language.equals("en")){
            String english=parts.length>1?parts[1].trim():chinese;
            if(english.isEmpty()&&containsChinese(chinese))english=chinese;
            return new Name(english,isPinyin(chinese,english)?chinese:english);
        }
        String value=parts.length==1?chinese:"";
        return new Name(value,value);
    }
}
