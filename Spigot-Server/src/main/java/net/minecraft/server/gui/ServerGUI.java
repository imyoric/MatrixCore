package net.minecraft.server.gui;

import com.google.common.collect.Lists;
import com.mojang.logging.LogQueues;
import com.mojang.logging.LogUtils;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SystemUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.slf4j.Logger;

public class ServerGUI extends JComponent {

    private static final Font MONOSPACED = new Font("Monospaced", 0, 12);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TITLE = "Minecraft server";
    private static final String SHUTDOWN_TITLE = "Minecraft server - shutting down!";
    private final DedicatedServer server;
    private Thread logAppenderThread;
    private final Collection<Runnable> finalizers = Lists.newArrayList();
    final AtomicBoolean isClosing = new AtomicBoolean();

    public static ServerGUI showFrameFor(final DedicatedServer dedicatedserver) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
            ;
        }

        final JFrame jframe = new JFrame("Minecraft server");
        final ServerGUI servergui = new ServerGUI(dedicatedserver);

        jframe.setDefaultCloseOperation(2);
        jframe.add(servergui);
        jframe.pack();
        jframe.setLocationRelativeTo((Component) null);
        jframe.setVisible(true);
        jframe.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowevent) {
                if (!servergui.isClosing.getAndSet(true)) {
                    jframe.setTitle("Minecraft server - shutting down!");
                    dedicatedserver.halt(true);
                    servergui.runFinalizers();
                }

            }
        });
        Objects.requireNonNull(jframe);
        servergui.addFinalizer(jframe::dispose);
        servergui.start();
        return servergui;
    }

    private ServerGUI(DedicatedServer dedicatedserver) {
        this.server = dedicatedserver;
        this.setPreferredSize(new Dimension(854, 480));
        this.setLayout(new BorderLayout());

        try {
            this.add(this.buildChatPanel(), "Center");
            this.add(this.buildInfoPanel(), "West");
        } catch (Exception exception) {
            ServerGUI.LOGGER.error("Couldn't build server GUI", exception);
        }

    }

    public void addFinalizer(Runnable runnable) {
        this.finalizers.add(runnable);
    }

    private JComponent buildInfoPanel() {
        JPanel jpanel = new JPanel(new BorderLayout());
        GuiStatsComponent guistatscomponent = new GuiStatsComponent(this.server){
            @Override
            public void paint(Graphics var0) {
                super.paint(var0);

                String[] MatrixTicks = new String[MinecraftServer.getMatrixAsyncSchedulerTicks().length+1];
                MatrixTicks[0] = "MatrixAsyncScheduler Ticks:";

                var0.drawString(MatrixTicks[0], 32, 116 + 3 * 16);

                int cur = 0;
                for (int tick: MinecraftServer.getMatrixAsyncSchedulerTicks()){
                    if(tick == -1) MatrixTicks[cur+1] = "Thr" +cur+ ": sleep.";
                    if(tick < -1) tick = 0;
                    if(tick > 1000){
                        tick /= 1000;
                        MatrixTicks[cur+1] = "Thr" +cur+ ": " + tick + "SEC";
                    }else MatrixTicks[cur+1] = "Thr" +cur+ ": " + tick + "ms";

                    cur++;
                }

                int TicksCub = (MatrixTicks.length-1) / 4;

                cur = 1;
                int CurX = 32;
                int CurY = 4;
                for(int curCub = 0; curCub != TicksCub; curCub++){
                    for(int curCunElm = 0; curCunElm != 4; curCunElm++){
                        int curX = CurX;
                        // System.out.println("CUR = "+cur/4);

                        if(cur - 4 < MatrixTicks.length && cur - 4 > 0){
                            curX += MatrixTicks[cur - 4].length() * 2 * curCub;
                        }

                        String endwith = ".";
                        if(cur > 15)
                            endwith = ". And other...";

                        if(MatrixTicks[cur] == null){
                            var0.drawString("Thr?" + ": ?" + "ms"+endwith, curX, 116 + CurY * 16);
                        }else var0.drawString(MatrixTicks[cur]+endwith, curX, 116 + CurY * 16);
                        cur++;
                        CurY++;
                    }
                    if(cur-1 > 16) continue;
                    CurY = 4;
                    CurX+=64;
                }
            }
        };
        Collection<Runnable> collection = this.finalizers; // CraftBukkit - decompile error

        Objects.requireNonNull(guistatscomponent);
        collection.add(guistatscomponent::close);
        jpanel.add(guistatscomponent, "North");
        jpanel.add(this.buildPlayerPanel(), "Center");
        jpanel.setBorder(new TitledBorder(new EtchedBorder(), "Stats"));
        return jpanel;
    }

    private JComponent buildPlayerPanel() {
        JList<?> jlist = new PlayerListBox(this.server);
        JScrollPane jscrollpane = new JScrollPane(jlist, 22, 30);

        jscrollpane.setBorder(new TitledBorder(new EtchedBorder(), "Players"));
        return jscrollpane;
    }

    private JComponent buildChatPanel() {
        JPanel jpanel = new JPanel(new BorderLayout());
        JTextArea jtextarea = new JTextArea();
        JScrollPane jscrollpane = new JScrollPane(jtextarea, 22, 30);

        jtextarea.setEditable(false);
        jtextarea.setFont(ServerGUI.MONOSPACED);
        JTextField jtextfield = new JTextField();

        jtextfield.addActionListener((actionevent) -> {
            String s = jtextfield.getText().trim();

            if (!s.isEmpty()) {
                this.server.handleConsoleInput(s, this.server.createCommandSourceStack());
            }

            jtextfield.setText("");
        });
        jtextarea.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent focusevent) {}
        });
        jpanel.add(jscrollpane, "Center");
        jpanel.add(jtextfield, "South");
        jpanel.setBorder(new TitledBorder(new EtchedBorder(), "Log and chat"));
        this.logAppenderThread = new Thread(() -> {
            String s;

            while ((s = LogQueues.getNextLogEvent("ServerGuiConsole")) != null) {
                this.print(jtextarea, jscrollpane, s);
            }

        });
        this.logAppenderThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(ServerGUI.LOGGER));
        this.logAppenderThread.setDaemon(true);
        return jpanel;
    }

    public void start() {
        this.logAppenderThread.start();
    }

    public void close() {
        if (!this.isClosing.getAndSet(true)) {
            this.runFinalizers();
        }

    }

    void runFinalizers() {
        this.finalizers.forEach(Runnable::run);
    }

    private static final java.util.regex.Pattern ANSI = java.util.regex.Pattern.compile("\\x1B\\[([0-9]{1,2}(;[0-9]{1,2})*)?[m|K]"); // CraftBukkit
    public void print(JTextArea jtextarea, JScrollPane jscrollpane, String s) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                this.print(jtextarea, jscrollpane, s);
            });
        } else {
            Document document = jtextarea.getDocument();
            JScrollBar jscrollbar = jscrollpane.getVerticalScrollBar();
            boolean flag = false;

            if (jscrollpane.getViewport().getView() == jtextarea) {
                flag = (double) jscrollbar.getValue() + jscrollbar.getSize().getHeight() + (double) (ServerGUI.MONOSPACED.getSize() * 4) > (double) jscrollbar.getMaximum();
            }

            try {
                document.insertString(document.getLength(), ANSI.matcher(s).replaceAll(""), (AttributeSet) null); // CraftBukkit
            } catch (BadLocationException badlocationexception) {
                ;
            }

            if (flag) {
                jscrollbar.setValue(Integer.MAX_VALUE);
            }

        }
    }
}
