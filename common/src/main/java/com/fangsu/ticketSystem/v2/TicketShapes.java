package com.fangsu.ticketSystem.v2;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
public final class TicketShapes {
    public static VoxelShape rotate(VoxelShape source, Direction direction) {
        int turns=switch(direction) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        VoxelShape result=source;
        for(int i=0;i<turns;i++) {
            VoxelShape[] next={Shapes.empty()};
            result.forAllBoxes((x1,y1,z1,x2,y2,z2) -> next[0]=Shapes.or(next[0],Shapes.box(1-z2,y1,x1,1-z1,y2,x2)));
            result=next[0];
        }
        return result;
    }
    private TicketShapes() { }
}
