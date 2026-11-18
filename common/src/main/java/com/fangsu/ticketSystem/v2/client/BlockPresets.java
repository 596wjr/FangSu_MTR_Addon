package com.fangsu.ticketSystem.v2.client;
import com.fangsu.ticketSystem.v2.TicketMachineConfig;
/** 只切换外观与屏幕位置，不覆盖销售、网络及支付配置。 */
public final class BlockPresets {
    public static TicketMachineConfig modern(TicketMachineConfig c){
        c.model="fangsu:ticketing/ticket_machine_new.bbmodel";
        c.shape="[[0,0,3,16,32,13.5]]";c.screenX=.0125;c.screenY=1.40906;c.screenZ=-.25816;c.screenPitch=5.56135;
        c.screenWidth=.50875;c.screenHeight=.87977;c.screenPixelsX=256;c.screenPixelsY=448;return c;
    }
    public static TicketMachineConfig legacy(TicketMachineConfig c){
        c.model="fangsu:ticketing/ticket_machine_old.bbmodel";
        c.shape="[[0,0,2.5,16,32,13.5]]";c.screenX=-.2141;c.screenY=1.309;c.screenZ=-.218;c.screenPitch=22.5;
        c.screenWidth=.4289;c.screenHeight=.3819;c.screenPixelsX=256;c.screenPixelsY=192;return c;
    }
    private BlockPresets(){ }
}
