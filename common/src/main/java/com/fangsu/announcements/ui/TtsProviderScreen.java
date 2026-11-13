package com.fangsu.announcements.ui;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.provider.ClientTokens;
import com.fangsu.announcements.provider.CloudTtsProvider.Region;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.*;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** 服务商与凭据专页，保存仅操作本地文件/会话，不做在线测试或合成。 */
public final class TtsProviderScreen extends ThemedConfigScreen {
    private final Screen parent;
    private final AnnouncementClient.Settings draft;
    private String token="",status="ui.fangsu.menu.provider_hint";
    private boolean remember,show,advanced;
    private String accountInput;
    private final Map<String,String> tokenDrafts=new HashMap<>();
    private final Map<String,Boolean> rememberDrafts=new HashMap<>();
    public TtsProviderScreen(Screen parent){super(ComponentHelper.translatable("ui.fangsu.menu.provider"));this.parent=parent;draft=new Gson().fromJson(new Gson().toJson(AnnouncementClient.settings()),AnnouncementClient.Settings.class);accountInput=draft.accountNamespace;loadToken();}
    private String accountKey(){return draft.provider+"/"+draft.accountNamespace;}
    private void loadToken(){
        try{
            String scope=accountKey();
            token=tokenDrafts.containsKey(scope)?tokenDrafts.get(scope):ClientTokens.entered(draft.provider,draft.accountNamespace);
            remember=rememberDrafts.containsKey(scope)?rememberDrafts.get(scope):ClientTokens.remembered(draft.provider,draft.accountNamespace);
        }
        catch(Exception e){token="";remember=false;status="ui.fangsu.ann.failed";}
        show=false;
    }
    private void rememberDraft(){tokenDrafts.put(accountKey(),token);rememberDrafts.put(accountKey(),remember);}
    @Override protected void buildFixedWidgets(){int x=getPanelLeft(),w=getPanelRight()-x;
        addFixedWidget(themedButton(x,height-40,w/2-2,20,ComponentHelper.translatable("ui.fangsu.config.apply"),b->save()));
        closeButton=addFixedWidget(themedButton(x+w/2+2,height-40,w/2-2,20,ComponentHelper.translatable("gui.back"),b->onClose()));
    }
    @Override protected void buildScrollableContent(ContentLayout layout){int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
        y=addWrappedText(x,y,w,ComponentHelper.translatable(status),0x555555)+10;
        Region provider=Region.find(draft.provider);
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.selected_provider",providerLabel(provider)),b->{
            var choices=Arrays.stream(Region.values()).map(region->new ConfigChoiceScreen.Choice(region.id,providerLabel(region))).toList();
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,"ui.fangsu.menu.provider",choices,this::selectProvider));
        }),y);y+=24;
        String keyUrl=switch(provider){
            case MIMO -> "https://platform.xiaomimimo.com?ref=8DVP58";
            case ALIYUN -> "https://bailian.console.aliyun.com/cn-beijing/settings/api-key?userCode=jqijxw3p";
            default -> "";
        };
        if(!keyUrl.isEmpty()){
            addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.get_key"),b->net.minecraft.Util.getPlatform().openUri(keyUrl)),y);y+=22;
        }
        if(provider==Region.ALIYUN)
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.aliyun_hint"),0x555555)+6;
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.token"),0x303030)+4;
        EditBox key=new EditBox(font,x,y,w-66,20,ComponentHelper.translatable("ui.fangsu.menu.token"));key.setMaxLength(2048);
        key.setFormatter((text,index)->FormattedCharSequence.forward(show?text:"•".repeat(text.length()),Style.EMPTY));key.setValue(token);key.setResponder(value->token=value);addRenderableWidget(key);addEntry(key,y);
        addEntry(addButton(x+w-62,y,62,20,ComponentHelper.translatable(show?"ui.fangsu.menu.hide":"ui.fangsu.menu.show"),b->{show=!show;requestRebuild();}),y);y+=22;
        addEntry(addButton(x,y,w,20,toggle("ui.fangsu.menu.remember_token",remember),b->{remember=!remember;requestRebuild();}),y);y+=24;
        y=addWrappedText(x,y,w,ComponentHelper.translatable(remember?"ui.fangsu.menu.token_plaintext":"ui.fangsu.menu.token_session"),remember?0x996622:0x555555)+8;
        addEntry(addButton(x,y,w,20,toggle("ui.fangsu.menu.cloud_enabled",draft.enabled),b->{draft.enabled=!draft.enabled;requestRebuild();}),y);y+=22;
        addEntry(addButton(x,y,w,20,toggle("ui.fangsu.ann.pre_synthesize",draft.preSynthesize),b->{draft.preSynthesize=!draft.preSynthesize;requestRebuild();}),y);y+=22;
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.pre_synthesize_hint"),0x555555)+6;
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.billing_hint"),0x555555)+10;
        addEntry(addButton(x,y,w,20,toggle("ui.fangsu.menu.advanced",advanced),b->{advanced=!advanced;requestRebuild();}),y);y+=22;
        if(advanced){
            y=field(x,y,w,"ui.fangsu.ann.model",draft.model,value->draft.model=value);
            y=field(x,y,w,"ui.fangsu.ann.account",accountInput,value->accountInput=value);
            addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.load_account_token"),b->switchAccount()),y);y+=22;
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.env_fallback",provider.keyEnvironment),0x555555)+8;
        }
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.clear_token"),b->{token="";remember=false;status="ui.fangsu.menu.clear_on_save";requestRebuild();}),y);
    }
    private int field(int x,int y,int w,String label,String value,java.util.function.Consumer<String> change){
        y=addWrappedText(x,y,w,ComponentHelper.translatable(label),0x555555)+4;EditBox box=new EditBox(font,x,y,w,20,ComponentHelper.translatable(label));box.setMaxLength(100);box.setValue(value);box.setResponder(change);addRenderableWidget(box);addEntry(box,y);return y+26;
    }
    private net.minecraft.network.chat.Component toggle(String label,boolean enabled){return ComponentHelper.translatable(label).append(": ").append(ComponentHelper.translatable(enabled?"options.on":"options.off"));}
    private String providerLabel(Region provider){
        return switch(provider){
            case MIMO -> "MiMo";
            case MINIMAX_CN -> ComponentHelper.translatableString("ui.fangsu.menu.minimax_cn");
            case MINIMAX_INTL -> ComponentHelper.translatableString("ui.fangsu.menu.minimax_intl");
            case ALIYUN -> ComponentHelper.translatableString("ui.fangsu.menu.aliyun");
        };
    }
    private void rememberProvider(){
        draft.providerModels.put(draft.provider,draft.model);
        draft.providerAccounts.put(draft.provider,draft.accountNamespace);
    }
    private void selectProvider(String value){
        rememberDraft();rememberProvider();
        draft.provider=value;
        status="ui.fangsu.menu.provider_hint";
        draft.model=draft.providerModels.getOrDefault(value,Region.find(value).defaultModel);
        draft.accountNamespace=draft.providerAccounts.getOrDefault(value,"default");
        accountInput=draft.accountNamespace;
        loadToken();requestRebuild();
    }
    private void switchAccount(){
        if(!accountInput.matches("[A-Za-z0-9_.-]{1,64}")){
            status="ui.fangsu.menu.invalid_account";requestRebuild();return;
        }
        rememberDraft();
        draft.accountNamespace=accountInput;
        status="ui.fangsu.menu.provider_hint";
        loadToken();requestRebuild();
    }
    private void save(){
        if(!accountInput.equals(draft.accountNamespace)){
            status="ui.fangsu.menu.confirm_account";requestRebuild();return;
        }
        try{
            var changed=new Gson().fromJson(new Gson().toJson(AnnouncementClient.settings()),AnnouncementClient.Settings.class);
            changed.providerModels.put(changed.provider,changed.model);
            changed.providerAccounts.put(changed.provider,changed.accountNamespace);
            changed.provider=draft.provider;changed.model=draft.model;changed.accountNamespace=draft.accountNamespace;
            changed.providerModels.put(changed.provider,changed.model);
            changed.providerAccounts.put(changed.provider,changed.accountNamespace);
            changed.enabled=draft.enabled;changed.preSynthesize=draft.preSynthesize;
            AnnouncementClient.validate(changed);
            ClientTokens.save(draft.provider,draft.accountNamespace,token,remember);
            AnnouncementClient.saveSettings(changed);
            rememberDraft();status="ui.fangsu.config.applied";
        }catch(Exception e){status="ui.fangsu.ann.failed";}
        requestRebuild();
    }
    @Override public void onClose(){token="";tokenDrafts.clear();rememberDrafts.clear();show=false;Minecraft.getInstance().setScreen(parent);}
}
