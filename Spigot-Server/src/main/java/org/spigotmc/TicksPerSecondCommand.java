package org.spigotmc;

import net.minecraft.server.MinecraftServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class TicksPerSecondCommand extends Command
{

    public TicksPerSecondCommand(String name)
    {
        super( name );
        this.description = "Gets the current ticks per second for the server";
        this.usageMessage = "/tps";
        this.setPermission( "bukkit.command.tps" );
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !testPermission( sender ) )
        {
            return true;
        }

        StringBuilder sb = new StringBuilder( ChatColor.GOLD + "TPS from last 1m, 5m, 15m: " );
        for ( double tps : MinecraftServer.getServer().recentTps )
        {
            sb.append( format( tps ) );
            sb.append( ", " );
        }

        sender.sendMessage( sb.substring( 0, sb.length() - 2 ) );

        StringBuilder MatrixTicks = new StringBuilder(ChatColor.GOLD+"MatrixAsyncScheduler Ticks:"+ChatColor.YELLOW);
        int cur = 0;
        for (int tick: MinecraftServer.getMatrixAsyncSchedulerTicks()){
            if(tick == -1){
                MatrixTicks.append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+"Thr").append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+cur).append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+": "+ChatColor.RESET+ChatColor.YELLOW).append("sleep, ");
                continue;
            }else if(tick > 1000){
                MatrixTicks.append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+"Thr").append(""+ChatColor.YELLOW+cur).append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+": "+ChatColor.RESET+ChatColor.YELLOW+ChatColor.UNDERLINE).append(tick/1000).append("SEC"+ChatColor.YELLOW+", ");
                continue;
            }
            if(tick < 0) tick = 0;
            MatrixTicks.append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+"Thr").append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+cur).append(ChatColor.UNDERLINE+""+ChatColor.YELLOW+": "+ChatColor.RESET+ChatColor.YELLOW).append(tick).append("ms, ");
            cur++;
        }
        sender.sendMessage(MatrixTicks.toString());

        sender.sendMessage(ChatColor.GOLD + "Current Memory Usage: " + ChatColor.GREEN + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)) + "/" + (Runtime.getRuntime().totalMemory() / (1024 * 1024)) + " mb (Max: "
                + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " mb)");

        return true;
    }

    private String format(double tps)
    {
        return ( ( tps > 18.0 ) ? ChatColor.GREEN : ( tps > 16.0 ) ? ChatColor.YELLOW : ChatColor.RED ).toString()
                + ( ( tps > 20.0 ) ? "*" : "" ) + Math.min( Math.round( tps * 100.0 ) / 100.0, 20.0 );
    }
}
