import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class MCHRouteGUI extends JFrame {

    // ═══════════════════════════════════════════════════════
    // THEME — dark + light token sets (Modern IDE Palette)
    // ═══════════════════════════════════════════════════════
    static boolean darkMode = false;

    // Sleek Dark Theme
    static final Color D_BG_DEEP  = new Color(0x0A, 0x0C, 0x11);
    static final Color D_BG_PANEL = new Color(0x13, 0x16, 0x1D);
    static final Color D_BG_CARD  = new Color(0x1B, 0x20, 0x2A);
    static final Color D_BORDER   = new Color(0x2B, 0x32, 0x40);
    static final Color D_TEXT_PRI = new Color(0xF3, 0xF4, 0xF6);
    static final Color D_TEXT_SEC = new Color(0x9C, 0xA3, 0xAF);
    static final Color D_TEXT_DIM = new Color(0x4B, 0x55, 0x63);
    static final Color D_GRID_FREE= new Color(0x1B, 0x21, 0x2C); 
    static final Color D_GRID_OBS = new Color(0x0A, 0x0C, 0x11); 
    static final Color D_LOG_BG   = new Color(0x07, 0x08, 0x0B);

    // Crisp Light Theme
    static final Color L_BG_DEEP  = new Color(0xF4, 0xF5, 0xF7);
    static final Color L_BG_PANEL = new Color(0xFF, 0xFF, 0xFF);
    static final Color L_BG_CARD  = new Color(0xEA, 0xEC, 0xF0);
    static final Color L_BORDER   = new Color(0xD2, 0xD5, 0xDB);
    static final Color L_TEXT_PRI = new Color(0x11, 0x18, 0x27);
    static final Color L_TEXT_SEC = new Color(0x6B, 0x72, 0x80);
    static final Color L_TEXT_DIM = new Color(0x9C, 0xA3, 0xAF);
    static final Color L_GRID_FREE= new Color(0xE8, 0xEA, 0xED);
    static final Color L_GRID_OBS = new Color(0xC5, 0xC9, 0xD1);
    static final Color L_LOG_BG   = new Color(0xF9, 0xFA, 0xFB);

    // Dynamic Accents
    static final Color ACCENT     = new Color(0x00, 0xC8, 0x75); 
    static final Color HULL_CLR   = new Color(0xF5, 0xA6, 0x23, 180);
    static final Color START_CLR  = new Color(0x00, 0xC8, 0x75);
    static final Color GOAL_CLR   = new Color(0xF0, 0x3E, 0x52);
    static final Color SUCCESS_CLR= new Color(0x00, 0xC8, 0x75);
    static final Color PATH_CLR   = new Color(0x2B, 0x8A, 0xF6, 240);
    static final Color RED_LINE   = new Color(0xFF, 0x40, 0x50, 160);
    static final Color WP_CLR     = new Color(0xFF, 0x7A, 0x2E);

    static final Font  FONT_M     = new Font("Consolas", Font.PLAIN, 12);
    static final Font  FONT_LBL   = new Font("SansSerif", Font.BOLD, 12);
    static final Font  FONT_BIG   = new Font("SansSerif", Font.BOLD, 22);

    static Color bgDeep()  { return darkMode ? D_BG_DEEP : L_BG_DEEP; }
    static Color bgPanel() { return darkMode ? D_BG_PANEL : L_BG_PANEL; }
    static Color bgCard()  { return darkMode ? D_BG_CARD : L_BG_CARD; }
    static Color border()  { return darkMode ? D_BORDER : L_BORDER; }
    static Color textPri() { return darkMode ? D_TEXT_PRI : L_TEXT_PRI; }
    static Color textSec() { return darkMode ? D_TEXT_SEC : L_TEXT_SEC; }
    static Color textDim() { return darkMode ? D_TEXT_DIM : L_TEXT_DIM; }
    static Color gridFree(){ return darkMode ? D_GRID_FREE : L_GRID_FREE; }
    static Color gridObs() { return darkMode ? D_GRID_OBS : L_GRID_OBS; }
    static Color logBg()   { return darkMode ? D_LOG_BG : L_LOG_BG; }

    static int clamp255(int v){ return Math.max(0,Math.min(255,v)); }
    static Color blend(Color a, Color b, double t){
        int r=(int)Math.round(a.getRed()+(b.getRed()-a.getRed())*t);
        int g=(int)Math.round(a.getGreen()+(b.getGreen()-a.getGreen())*t);
        int bl=(int)Math.round(a.getBlue()+(b.getBlue()-a.getBlue())*t);
        return new Color(clamp255(r),clamp255(g),clamp255(bl));
    }
    static Color withAlpha(Color c, int a){ return new Color(c.getRed(),c.getGreen(),c.getBlue(),a); }

    static final double EPS = 1e-9;
    static final int POINT_PRECISION = 6;

    private static MCHRouteGUI activeGui;

    public static void attach(MCHRouteGUI gui) {
        activeGui = gui;
    }

    public static MCHRouteGUI getActiveGui() {
        return activeGui;
    }

    public static void showRoutePreview(int startX, int startY, int goalX, int goalY) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MCHRouteGUI gui = new MCHRouteGUI();
            attach(gui);
            gui.setVisible(true);
            gui.addTarget(new Pt(startX, startY), 1000.0);
            gui.addTarget(new Pt(goalX, goalY), 1000.0);
        });
    }

    public static void showTaskPreview(Task task) {
        if (task == null || java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MCHRouteGUI gui = new MCHRouteGUI();
            attach(gui);
            gui.setVisible(true);
            gui.addTarget(new Pt(task.getPickupX(), task.getPickupY()), Math.max(10.0, task.getTimeRemainingMinutes()));
            gui.addTarget(new Pt(task.getDeliveryX(), task.getDeliveryY()), Math.max(20.0, task.getTimeRemainingMinutes() + 5));
        });
    }

    public static List<Task> importTasksFromMCHRoute(MCHRouteGUI gui) {
        if (gui == null) {
            return new ArrayList<>();
        }

        attach(gui);
        List<DeliveryTask> targets = gui.targets;
        List<Task> tasks = new ArrayList<>();
        if (targets == null || targets.size() < 2) {
            return tasks;
        }

        for (int i = 0; i + 1 < targets.size(); i += 2) {
            DeliveryTask pickupTarget = targets.get(i);
            DeliveryTask deliveryTarget = targets.get(i + 1);

            if (pickupTarget == null || deliveryTarget == null || pickupTarget.pt == null || deliveryTarget.pt == null) {
                continue;
            }

            double pickupX = pickupTarget.pt.x;
            double pickupY = pickupTarget.pt.y;
            double deliveryX = deliveryTarget.pt.x;
            double deliveryY = deliveryTarget.pt.y;
            double deadlineMinutes = Math.max(15.0, Math.max(pickupTarget.deadline, deliveryTarget.deadline));
            LocalDateTime deadline = LocalDateTime.now().plusMinutes((long) Math.ceil(deadlineMinutes));
            tasks.add(new Task(i / 2 + 1, pickupX, pickupY, deliveryX, deliveryY, deadline));
        }

        return tasks;
    }

    public static List<Task> importTasksFromMCHRoute() {
        return importTasksFromMCHRoute(activeGui);
    }

    public static double getMapCoordinateLimit() {
        if (grid != null && rows > 0 && cols > 0) {
            return Math.max(rows, cols);
        }
        return 1024.0;
    }

    public static double estimateShortestDistance(double startX, double startY, double endX, double endY) {
        return Math.hypot(endX - startX, endY - startY);
    }

    public static void showZones(List<Zone> zones) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MCHRouteGUI gui = activeGui != null ? activeGui : new MCHRouteGUI();
            attach(gui);
            if (!gui.isVisible()) {
                gui.setVisible(true);
            }
            gui.setDisplayedZones(zones);
        });
    }

    public static void showTaskOverlay(List<Task> tasks) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MCHRouteGUI gui = activeGui != null ? activeGui : new MCHRouteGUI();
            attach(gui);
            if (!gui.isVisible()) {
                gui.setVisible(true);
            }
            gui.setDisplayedTasks(tasks);
        });
    }

    // ═══════════════════════════════════════
    // GEOMETRIC POINT CLASS
    // ═══════════════════════════════════════
    static class Pt {
        final double x, y;
        Pt(double x, double y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if(o == this) return true;
            if(!(o instanceof Pt)) return false;
            Pt p = (Pt)o;
            return Math.abs(x - p.x) <= 1e-6 && Math.abs(y - p.y) <= 1e-6;
        }
        @Override
        public int hashCode() { return Objects.hash(Math.round(x * 1000), Math.round(y * 1000)); }
        @Override
        public String toString() { return String.format("(%.2f, %.2f)", x, y); }
    }

    static class DeliveryTask {
        Pt pt; double deadline; int originalId;
        boolean onTime; double arrivalTime; int execOrder;
        DeliveryTask(Pt pt, double deadline, int originalId) {
            this.pt = pt; this.deadline = deadline; this.originalId = originalId;
        }
    }

    static class ZoneVisual {
        final double centerX;
        final double centerY;
        final double radius;

        ZoneVisual(double centerX, double centerY, double radius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
        }
    }

    // ═══════════════════════════════════════
    // MAP DATA
    // ═══════════════════════════════════════
    static int rows, cols;
    static char[][] grid;
    static List<List<Pt>> allHulls = new ArrayList<>();
    static Map<String, Boolean> segmentValidCache = new HashMap<>();
    static Pt globalS, globalG;

    // ═══════════════════════════════════════
    // GUI STATE
    // ═══════════════════════════════════════
    MapPanel   mapPanel;
    JTextArea  logArea;
    HintTextField startRow, startCol, targetRow, targetCol, targetDeadline;
    JLabel     statusLbl;
    JButton    runBtn, clearBtn, loadBtn, themeBtn;
    String     mapPath = "Coding Java/MAPF/Berlin_1_256.map";

    List<DeliveryTask> targets = new ArrayList<>();
    List<DeliveryTask> executedTasks = new ArrayList<>();
    List<Pt> currentPath = new ArrayList<>();
    List<ZoneVisual> displayedZones = new ArrayList<>();
    List<Task> displayedTasks = new ArrayList<>();
    List<Pt[]> directLines = new ArrayList<>(); 
    int[][] zoneMask = null;
    Map<Integer, Color> zoneColorMap = new HashMap<>();
    long lastMs = 0;
    int pickMode = 0;

    JPanel ctrlPanel, topBar, destListPanel;
    JScrollPane mapScroll, logScroll;
    List<JLabel> secLabels = new ArrayList<>();
    List<JLabel> secondaryLabels = new ArrayList<>(); // Restored this array to fix the compile bug!

    public static void main(String[] a){
        SwingUtilities.invokeLater(()->new MCHRouteGUI().setVisible(true));
    }

    MCHRouteGUI(){
        super("CH-Route · Smart Deadline Scheduler");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 800);
        setMinimumSize(new Dimension(1100,700));
        setExtendedState(JFrame.MAXIMIZED_BOTH); //Making full screen
        setLayout(new BorderLayout());
        buildUI();
        applyTheme();
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(()->tryLoadMap(mapPath));
    }

    // ═══════════════════════════════════════
    // MODERN COMPONENTS
    // ═══════════════════════════════════════
    static class RoundedButton extends JButton {
        boolean primary; Color customFg;
        boolean hovering=false, pressing=false;
        
        RoundedButton(String text, Color customFg, boolean primary){
            super(text);
            this.primary=primary; this.customFg=customFg;
            setFont(FONT_LBL); setFocusPainted(false); setContentAreaFilled(false);
            setBorderPainted(false); setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){hovering=true; repaint();}
                public void mouseExited(MouseEvent e){hovering=false; pressing=false; repaint();}
                public void mousePressed(MouseEvent e){pressing=true; repaint();}
                public void mouseReleased(MouseEvent e){pressing=false; repaint();}
            });
        }
        
        protected void paintComponent(Graphics g0){
            Graphics2D g=(Graphics2D)g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(), h=getHeight();
            RoundRectangle2D shape=new RoundRectangle2D.Float(1,1,w-2,h-2,8,8);

            if(primary){
                Color base = darkMode ? new Color(0x0C, 0x10, 0x16) : new Color(0xFF, 0xFF, 0xFF);
                Color fill = pressing ? blend(base, ACCENT, 0.15) : (hovering ? blend(base, ACCENT, 0.08) : base);
                g.setColor(fill); g.fill(shape);
                g.setColor(blend(base, ACCENT, 0.4)); 
                g.setStroke(new BasicStroke(1.2f)); g.draw(shape);
                setForeground(ACCENT);
            } else {
                Color base = bgCard();
                Color fill = pressing ? blend(base, Color.BLACK, 0.15) : (hovering ? blend(base, Color.WHITE, 0.12) : base);
                g.setColor(fill); g.fill(shape);
                g.setColor(border()); g.setStroke(new BasicStroke(1.0f)); g.draw(shape);
                setForeground(customFg != null ? customFg : textPri());
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static class HintTextField extends JTextField {
        String hint;
        HintTextField(String hint, int cols) {
            super(cols); this.hint = hint;
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(textDim()); g2.setFont(FONT_M);
                int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
                g2.drawString(hint, 7, y);
                g2.dispose();
            }
        }
    }

    static JComponent swatch(Color c, int size){
        JComponent sw = new JComponent(){
            public void paint(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),getWidth()/2,getHeight()/2);
                g2.dispose();
            }
        };
        sw.setPreferredSize(new Dimension(size,size));
        sw.setOpaque(false);
        return sw;
    }

    static class ModernScrollBarUI extends BasicScrollBarUI {
        protected void configureScrollBarColors(){}
        protected JButton createDecreaseButton(int orientation){ return zeroButton(); }
        protected JButton createIncreaseButton(int orientation){ return zeroButton(); }
        private JButton zeroButton(){
            JButton b=new JButton();
            b.setPreferredSize(new Dimension(0,0)); b.setMinimumSize(new Dimension(0,0)); b.setMaximumSize(new Dimension(0,0));
            return b;
        }
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setColor(bgPanel());
            g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            g2.dispose();
        }
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds){
            if(thumbBounds.isEmpty() || !c.isEnabled()) return;
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(border());
            int pad=3;
            g2.fillRoundRect(thumbBounds.x+pad, thumbBounds.y+pad, Math.max(1,thumbBounds.width-pad*2), Math.max(1,thumbBounds.height-pad*2), 8, 8);
            g2.dispose();
        }
    }

    // ═══════════════════════════════════════
    // UI BUILDER (Exact Requested Layout)
    // ═══════════════════════════════════════
    void buildUI(){
        topBar=new JPanel(new BorderLayout()){
            protected void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(), h=getHeight();
                GradientPaint gp=new GradientPaint(0,0,ACCENT,w,0,withAlpha(ACCENT,30));
                g2.setPaint(gp); g2.fillRect(0,h-2,w,2); g2.dispose();
            }
        };
        topBar.setBorder(new MatteBorder(0,0,1,0,border()));
        topBar.setPreferredSize(new Dimension(0,52));
        JLabel title=new JLabel("  ◆  CH-ROUTE  DRONE PATHFINDER 2.0");
        title.setFont(FONT_BIG); title.setForeground(ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(0,16,0,0));
        topBar.add(title,BorderLayout.WEST);

        JPanel topRight=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,10));
        topRight.setOpaque(false);
        statusLbl=new JLabel("Load a map to begin");
        statusLbl.setFont(FONT_LBL); statusLbl.setForeground(textSec());
        themeBtn=btn("☀  Light", null, false);
        themeBtn.setPreferredSize(new Dimension(100,32));
        themeBtn.addActionListener(e->{ darkMode=!darkMode; applyTheme(); });
        topRight.add(statusLbl); topRight.add(themeBtn);
        topBar.add(topRight,BorderLayout.EAST);
        add(topBar,BorderLayout.NORTH);

        ctrlPanel=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp=new GradientPaint(0,0,bgPanel(),0,getHeight(),blend(bgPanel(),bgDeep(),0.2));
                g2.setPaint(gp); g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        ctrlPanel.setPreferredSize(new Dimension(280,0));
        ctrlPanel.setBorder(new MatteBorder(0,0,0,1,border()));
        ctrlPanel.setLayout(new BoxLayout(ctrlPanel,BoxLayout.Y_AXIS));
        ctrlPanel.add(Box.createVerticalStrut(16));

        // 1. Choose Map File
        ctrlPanel.add(secLbl("CHOOSE THE MAP FILE")); ctrlPanel.add(Box.createVerticalStrut(8));
        loadBtn=btn("Load .map file", null, true);
        loadBtn.setPreferredSize(new Dimension(250,34));
        loadBtn.addActionListener(e->chooseMap());
        ctrlPanel.add(wrap(loadBtn)); 
        JLabel ml=new JLabel("  Map is loaded"); ml.setFont(FONT_M); ml.setForeground(textSec());
        secondaryLabels.add(ml);
        ctrlPanel.add(wrap(ml)); 
        ctrlPanel.add(Box.createVerticalStrut(18));

        // 2. Source
        ctrlPanel.add(secLbl("SOURCE (row/column)")); ctrlPanel.add(Box.createVerticalStrut(8));
        startRow = field("Row", 10); startRow.setText("10"); 
        startCol = field("Col",10); startCol.setText("10");
        JPanel srcP = new JPanel(new FlowLayout(FlowLayout.LEFT,7,0)); srcP.setOpaque(false);
        srcP.add(Box.createHorizontalStrut(10)); srcP.add(swatch(START_CLR,10)); 
        srcP.add(startRow);srcP.add(Box.createHorizontalStrut(40)); srcP.add(startCol);
        ctrlPanel.add(srcP); ctrlPanel.add(Box.createVerticalStrut(6));
        
        JButton bPickS = btn("Choose from map", START_CLR, false); 
        bPickS.setPreferredSize(new Dimension(210,30));
        bPickS.addActionListener(e->{ pickMode=1; status("Right-click map to set SOURCE"); });
        JPanel pickSP = new JPanel(new FlowLayout(FlowLayout.LEFT, 40, 0)); pickSP.setOpaque(false);
        pickSP.add(bPickS);
        ctrlPanel.add(pickSP); ctrlPanel.add(Box.createVerticalStrut(15));

        // 3. Destination
        ctrlPanel.add(secLbl("DESTINATION")); ctrlPanel.add(Box.createVerticalStrut(8));
        targetRow = field("Row", 7); targetCol = field("Col",7 ); targetDeadline = field("Deadline", 10);
        JPanel tgtP = new JPanel(new FlowLayout(FlowLayout.LEFT,7,0)); tgtP.setOpaque(false);
        tgtP.add(Box.createHorizontalStrut(10)); tgtP.add(swatch(GOAL_CLR,10));
        tgtP.add(targetRow); tgtP.add(targetCol); tgtP.add(targetDeadline);
        ctrlPanel.add(tgtP); ctrlPanel.add(Box.createVerticalStrut(5));

        

        JButton bAddG = btn("+ Add", null, false); bAddG.setPreferredSize(new Dimension(100,28));
        JButton clrTgt = btn("✕ Clear", null, false); clrTgt.setPreferredSize(new Dimension(100,28));

        JButton bPickG = btn("Choose from map", GOAL_CLR, false); 
        bPickG.setPreferredSize(new Dimension(210, 30));
        bPickG.addActionListener(e -> { pickMode=2; status("Click map to place Delivery and enter Deadline"); });
        JPanel pickGP = new JPanel(new FlowLayout(FlowLayout.LEFT, 40, 0)); pickGP.setOpaque(false);
        pickGP.add(bPickG);
        ctrlPanel.add(pickGP); ctrlPanel.add(Box.createVerticalStrut(8));

        bAddG.addActionListener(e->{
            try {
                int r=Integer.parseInt(targetRow.getText().trim());
                int c=Integer.parseInt(targetCol.getText().trim());
                double dl=Double.parseDouble(targetDeadline.getText().trim());
                addTarget(new Pt(c, r), dl);
                targetRow.setText(""); targetCol.setText(""); targetDeadline.setText("");
            } catch(Exception ex){ status("Invalid manual destination / deadline!"); }
        });
        clrTgt.addActionListener(e->{ 
            targets.clear(); executedTasks.clear(); currentPath.clear(); directLines.clear(); 
            refreshTargetsUI(); mapPanel.repaint(); status("Destinations cleared.");
        });
        JPanel addClrP = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); addClrP.setOpaque(false);
        addClrP.add(Box.createHorizontalStrut(20)); addClrP.add(bAddG); addClrP.add(clrTgt);
        ctrlPanel.add(addClrP); ctrlPanel.add(Box.createVerticalStrut(15));

        // 4. Destinations List
        destListPanel = new JPanel();
        destListPanel.setLayout(new BoxLayout(destListPanel, BoxLayout.Y_AXIS));
        destListPanel.setOpaque(false);
        ctrlPanel.add(destListPanel);
        refreshTargetsUI();
        ctrlPanel.add(Box.createVerticalStrut(20));

        // 5. Actions: RUN and Clear Path
        runBtn=btn("▶ RUN CH-ROUTE", null, true);
        runBtn.setPreferredSize(new Dimension(250,44));
        runBtn.addActionListener(e->run());
        ctrlPanel.add(wrap(runBtn)); ctrlPanel.add(Box.createVerticalStrut(8));
        
        clearBtn=btn("Clear path", null, false);
        clearBtn.setPreferredSize(new Dimension(250,34));
        clearBtn.addActionListener(e->{currentPath.clear(); executedTasks.clear(); directLines.clear(); mapPanel.repaint(); log("Cleared.");});
        ctrlPanel.add(wrap(clearBtn)); ctrlPanel.add(Box.createVerticalStrut(15));

        // 6. Zoom Controls
        JPanel zp=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); zp.setOpaque(false);
        JButton bZI=btn("Zoom +", null, false); bZI.setPreferredSize(new Dimension(110,35));
        JButton bZO=btn("Zoom -", null, false); bZO.setPreferredSize(new Dimension(110,35));
        bZI.addActionListener(e->mapPanel.zoom(1)); bZO.addActionListener(e->mapPanel.zoom(-1));
        zp.add(Box.createHorizontalStrut(8)); zp.add(bZI); zp.add(bZO); 
        ctrlPanel.add(zp);
        ctrlPanel.add(Box.createVerticalStrut(25));

        // 7. Legend
        ctrlPanel.add(secLbl("LEGEND")); ctrlPanel.add(Box.createVerticalStrut(10));
        ctrlPanel.add(legRow(START_CLR,"Source (S₀)"));
        ctrlPanel.add(legRow(GOAL_CLR,"Destinations (D1, D2...)"));
        ctrlPanel.add(legRow(RED_LINE,"Straight Line (Direct)"));
        ctrlPanel.add(legRow(PATH_CLR,"Optimal Sequential Path"));
        ctrlPanel.add(legRow(HULL_CLR,"Convex hulls"));
        ctrlPanel.add(Box.createVerticalStrut(18));

        ctrlPanel.add(Box.createVerticalGlue());
        add(ctrlPanel,BorderLayout.WEST);

        mapPanel=new MapPanel();
        mapScroll=new JScrollPane(mapPanel);
        mapScroll.setBorder(null);
        mapScroll.getHorizontalScrollBar().setUnitIncrement(20);
        mapScroll.getVerticalScrollBar().setUnitIncrement(20);
        mapScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        mapScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        mapScroll.getVerticalScrollBar().setPreferredSize(new Dimension(10,10));
        mapScroll.getHorizontalScrollBar().setPreferredSize(new Dimension(10,10));

        logArea=new JTextArea(6,40); logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBorder(BorderFactory.createEmptyBorder(10,16,10,16));
        logArea.setLineWrap(true);
        logScroll=new JScrollPane(logArea);
        logScroll.setBorder(new MatteBorder(1,0,0,0,border()));
        logScroll.setPreferredSize(new Dimension(0,100));
        logScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        logScroll.getVerticalScrollBar().setPreferredSize(new Dimension(10,10));

        JSplitPane sp=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,mapScroll,logScroll);
        sp.setResizeWeight(0.38); sp.setBorder(null);
        add(sp,BorderLayout.CENTER);
    }

    void addTarget(Pt p, double dl) {
        if(targets.size() >= 4) { status("Max 4 destinations reached."); return; }
        targets.add(new DeliveryTask(p, dl, targets.size() + 1));
        executedTasks.clear();
        currentPath.clear();
        directLines.clear();
        refreshTargetsUI();
        mapPanel.repaint();
        status("Added target D" + targets.size() + " at (" + (int)p.y + ", " + (int)p.x + ") DL: " + dl);
    }

    void setDisplayedZones(List<Zone> zones) {
        displayedZones.clear();
        if (zones == null) {
            zoneMask = null;
            zoneColorMap.clear();
            if (mapPanel != null) mapPanel.repaint();
            return;
        }
        for (Zone zone : zones) {
            if (zone != null) {
                displayedZones.add(new ZoneVisual(zone.getCenterX(), zone.getCenterY(), zone.getRadius()));
            }
        }
        recomputeZoneMask();
        if (mapPanel != null) {
            mapPanel.repaint();
        }
    }

    void recomputeZoneMask() {
        if (displayedZones == null || displayedZones.isEmpty() || grid == null || rows <= 0 || cols <= 0) {
            zoneMask = null;
            zoneColorMap.clear();
            return;
        }

        zoneMask = new int[rows][cols];
        zoneColorMap.clear();
        int zoneCount = displayedZones.size();
        for (int i = 0; i < zoneCount; i++) {
            double hue = (i * 1.0 / Math.max(1, zoneCount - 1)) * 0.65;
            Color base = new Color(Color.HSBtoRGB((float)hue, 0.35f, 0.92f));
            zoneColorMap.put(i + 1, withAlpha(base, 180));
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int nearestZoneId = 1;
                double bestDist = Double.MAX_VALUE;
                double cx = c + 0.5;
                double cy = r + 0.5;
                for (int zoneIndex = 0; zoneIndex < displayedZones.size(); zoneIndex++) {
                    ZoneVisual zone = displayedZones.get(zoneIndex);
                    double dist = Math.hypot(cx - zone.centerX, cy - zone.centerY);
                    if (dist < bestDist) {
                        bestDist = dist;
                        nearestZoneId = zoneIndex + 1;
                    }
                }
                zoneMask[r][c] = nearestZoneId;
            }
        }
    }

    void setDisplayedTasks(List<Task> tasks) {
        displayedTasks.clear();
        if (tasks != null) {
            displayedTasks.addAll(tasks);
        }
        if (mapPanel != null) {
            mapPanel.repaint();
        }
    }

    void refreshTargetsUI() {
        destListPanel.removeAll();
        if (targets.isEmpty()) {
            JLabel lbl = new JLabel("  No destinations added.");
            lbl.setForeground(textDim()); lbl.setFont(FONT_M);
            destListPanel.add(wrap(lbl));
        } else {
            for (int i = 0; i < targets.size(); i++) {
                DeliveryTask t = targets.get(i);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
                row.setOpaque(false);
                row.add(Box.createHorizontalStrut(10));
                row.add(swatch(GOAL_CLR, 10));
                JLabel lbl = new JLabel("D" + t.originalId + ". (" + (int)t.pt.x + "," + (int)t.pt.y + ") | DL - " + t.deadline);
                lbl.setForeground(textPri()); lbl.setFont(FONT_M);
                row.add(lbl);
                destListPanel.add(row);
            }
        }
        destListPanel.revalidate();
        destListPanel.repaint();
    }

    HintTextField field(String hint, int w){
        HintTextField f = new HintTextField(hint, w);
        f.setBackground(bgCard()); f.setForeground(textPri()); f.setCaretColor(ACCENT);
        f.setFont(FONT_M);
        applyFieldBorder(f,false);
        f.addFocusListener(new FocusAdapter(){
            public void focusGained(FocusEvent e){ applyFieldBorder(f,true); f.repaint(); }
            public void focusLost(FocusEvent e){ applyFieldBorder(f,false); f.repaint(); }
        });
        return f;
    }

    static void applyFieldBorder(JTextField f, boolean focused){
        Color c = focused? ACCENT : border();
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(c,focused?2:1,true),
            BorderFactory.createEmptyBorder(5,7,5,7)));
    }

    void applyTheme(){
        getContentPane().setBackground(bgDeep());
        topBar.setBackground(bgPanel());
        topBar.setBorder(new MatteBorder(0,0,1,0,border()));
        ctrlPanel.setBackground(bgPanel());
        ctrlPanel.setBorder(new MatteBorder(0,0,0,1,border()));
        mapScroll.setBackground(bgDeep());
        mapScroll.getViewport().setBackground(bgDeep());
        logScroll.setBorder(new MatteBorder(1,0,0,0,border()));
        logArea.setBackground(logBg());
        logArea.setForeground(darkMode ? new Color(0x17,0xC7,0x8B) : new Color(0x00,0x80,0x55));
        statusLbl.setForeground(textSec());
        themeBtn.setText(darkMode?"☀  Light":"🌙  Dark");
        
        for(Component comp: ctrlPanel.getComponents()) restyle(comp);
        for(JLabel l: secLabels) l.setForeground(textDim());
        for(JLabel l: secondaryLabels) l.setForeground(textSec()); // This prevents the compile error!
        
        refreshTargetsUI();
        topBar.repaint();
        ctrlPanel.repaint();
        repaint();
        if(mapPanel!=null) mapPanel.repaint();
    }

    void restyle(Component comp){
        if(comp instanceof JPanel){
            comp.setBackground(bgPanel());
            for(Component child: ((JPanel)comp).getComponents()) restyle(child);
        } else if(comp instanceof JButton){
            comp.repaint();
        } else if(comp instanceof JTextField){
            JTextField f=(JTextField)comp;
            f.setBackground(bgCard());
            f.setForeground(textPri());
            applyFieldBorder(f,false);
        }
    }

    // ═══════════════════════════════════════
    // MAP PANEL 
    // ═══════════════════════════════════════
    class MapPanel extends JPanel {
        double cs = 3.0; 

        MapPanel(){
            addMouseWheelListener(e->{ if(e.getWheelRotation()<0)zoom(1); else zoom(-1); });
            addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseMoved(MouseEvent e){
                    int r=(int)(e.getY()/cs),c=(int)(e.getX()/cs);
                    setToolTipText((r>=0&&r<rows&&c>=0&&c<cols)?"("+r+","+c+") "+grid[r][c]:null);
                }
            });
            addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){
                    if(grid==null) return;
                    boolean isRightClick = SwingUtilities.isRightMouseButton(e);
                    int r=(int)(e.getY()/cs), c=(int)(e.getX()/cs);
                    if(r<0||r>=rows||c<0||c>=cols) return;

                    int mode = pickMode;
                    if (isRightClick && mode == 0) return; 

                    if(mode==1){
                        startRow.setText(""+r); startCol.setText(""+c);
                        status("Source set to ("+r+","+c+")");
                        pickMode=0;
                    }
                    else if(mode==2){
                        String input = JOptionPane.showInputDialog(MCHRouteGUI.this, "Enter Deadline for D" + (targets.size() + 1) + ":", "1000");
                        if (input != null && !input.trim().isEmpty()) {
                            try {
                                double dl = Double.parseDouble(input.trim());
                                addTarget(new Pt(c, r), dl);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(MCHRouteGUI.this, "Invalid deadline entered. Delivery not added.");
                            }
                        }
                        if(targets.size() >= 4) pickMode = 0;
                    }
                    repaint();
                }
            });
        }

        void zoom(int d){
            double old=cs;
            if(d>0) cs = Math.min(cs * 1.25, 35.0);
            else cs = Math.max(cs / 1.25, 0.12); 
            if(old!=cs){
                setPreferredSize(new Dimension((int)Math.round(cols*cs), (int)Math.round(rows*cs)));
                revalidate();
                repaint();
            }
        }

        public Dimension getPreferredSize(){
            return grid==null?new Dimension(600,600):new Dimension((int)Math.round(cols*cs), (int)Math.round(rows*cs));
        }

        protected void paintComponent(Graphics g0){
            super.paintComponent(g0);
            setBackground(bgDeep());
            if(grid==null){g0.setColor(textDim());g0.setFont(FONT_BIG);g0.drawString("Load a map file",80,80);return;}
            Graphics2D g=(Graphics2D)g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);

            Rectangle clip=g.getClipBounds();
            int r0=clip==null?0:Math.max(0,(int)(clip.y/cs));
            int r1=clip==null?rows:Math.min(rows,(int)((clip.y+clip.height)/cs)+2);
            int c0=clip==null?0:Math.max(0,(int)(clip.x/cs));
            int c1=clip==null?cols:Math.min(cols,(int)((clip.x+clip.width)/cs)+2);

            if (zoneMask != null) {
                for (int r = r0; r < r1; r++) {
                    for (int c = c0; c < c1; c++) {
                        int zid = zoneMask[r][c];
                        Color zc = zoneColorMap.getOrDefault(zid, withAlpha(ACCENT, 24));
                        g.setColor(zc);
                        int px = (int)Math.round(c * cs);
                        int py = (int)Math.round(r * cs);
                        g.fillRect(px, py, (int)Math.max(1, Math.round(cs)), (int)Math.max(1, Math.round(cs)));
                    }
                }
            } else {
                g.setColor(gridFree());
                g.fillRect((int)(c0 * cs), (int)(r0 * cs), (int)Math.ceil((c1 - c0) * cs), (int)Math.ceil((r1 - r0) * cs));
            }

            g.setColor(gridObs());
            for(int r=r0;r<r1;r++) {
                for(int c=c0;c<c1;c++){
                    if(grid[r][c]=='@'){
                        int px = (int)Math.round(c * cs);
                        int py = (int)Math.round(r * cs);
                        int pw = (int)Math.round((c + 1) * cs) - px;
                        int ph = (int)Math.round((r + 1) * cs) - py;
                        g.fillRect(px, py, Math.max(1, pw), Math.max(1, ph));
                    }
                }
            }

            g.setStroke(new BasicStroke(Math.max(1.0f, (float)(cs * 0.2)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for(List<Pt> hull:allHulls){
                if(hull.size()<2)continue;
                int[]hx=new int[hull.size()],hy=new int[hull.size()];
                for(int i=0;i<hull.size();i++){
                    hx[i]=(int)Math.round(hull.get(i).x*cs + cs/2.0);
                    hy[i]=(int)Math.round(hull.get(i).y*cs + cs/2.0);
                }
                if(hull.size()>=3){
                    g.setColor(withAlpha(HULL_CLR, 25));
                    g.fillPolygon(hx,hy,hull.size());
                }
                g.setColor(HULL_CLR);
                g.drawPolygon(hx,hy,hull.size());
            }

            if(directLines != null && !directLines.isEmpty()){
                g.setColor(RED_LINE);
                g.setStroke(new BasicStroke(Math.max(1.5f, (float)(cs * 0.4)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(Pt[] line : directLines){
                    int x1 = (int)Math.round(line[0].x*cs + cs/2.0);
                    int y1 = (int)Math.round(line[0].y*cs + cs/2.0);
                    int x2 = (int)Math.round(line[1].x*cs + cs/2.0);
                    int y2 = (int)Math.round(line[1].y*cs + cs/2.0);
                    g.drawLine(x1, y1, x2, y2);
                }
            }

            if(currentPath != null && currentPath.size() >= 2){
                g.setColor(withAlpha(PATH_CLR, 70));
                g.setStroke(new BasicStroke(Math.max(4.0f, (float)(cs * 1.6)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(int i=0;i<currentPath.size()-1;i++){
                    Pt a=currentPath.get(i),b=currentPath.get(i+1);
                    int x1 = (int)Math.round(a.x*cs + cs/2.0);
                    int y1 = (int)Math.round(a.y*cs + cs/2.0);
                    int x2 = (int)Math.round(b.x*cs + cs/2.0);
                    int y2 = (int)Math.round(b.y*cs + cs/2.0);
                    g.drawLine(x1, y1, x2, y2);
                }
                g.setColor(PATH_CLR);
                g.setStroke(new BasicStroke(Math.max(2.0f, (float)(cs * 0.8)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(int i=0;i<currentPath.size()-1;i++){
                    Pt a=currentPath.get(i),b=currentPath.get(i+1);
                    int x1 = (int)Math.round(a.x*cs + cs/2.0);
                    int y1 = (int)Math.round(a.y*cs + cs/2.0);
                    int x2 = (int)Math.round(b.x*cs + cs/2.0);
                    int y2 = (int)Math.round(b.y*cs + cs/2.0);
                    g.drawLine(x1, y1, x2, y2);
                }
            }

            if(currentPath != null && currentPath.size() > 2) {
                int sz = (int)Math.max(3, cs);
                g.setStroke(new BasicStroke(1.0f));
                for(int i=1;i<currentPath.size()-1;i++){
                    Pt wp=currentPath.get(i);
                    int cx=(int)Math.round(wp.x*cs + cs/2.0);
                    int cy=(int)Math.round(wp.y*cs + cs/2.0);
                    g.setColor(withAlpha(Color.BLACK,70));
                    g.fillPolygon(new int[]{cx+1,cx+sz+1,cx+1,cx-sz+1},new int[]{cy-sz+2,cy+2,cy+sz+2,cy+2},4);
                    g.setColor(WP_CLR);
                    g.fillPolygon(new int[]{cx,cx+sz,cx,cx-sz},new int[]{cy-sz,cy,cy+sz,cy},4);
                    g.setColor(textPri());
                    g.drawPolygon(new int[]{cx,cx+sz,cx,cx-sz},new int[]{cy-sz,cy,cy+sz,cy},4);
                }
            }

            if (displayedZones != null && !displayedZones.isEmpty()) {
                g.setStroke(new BasicStroke(Math.max(1.0f, (float)(cs * 0.25)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < displayedZones.size(); i++) {
                    ZoneVisual zone = displayedZones.get(i);
                    int cx = (int)Math.round(zone.centerX * cs + cs / 2.0);
                    int cy = (int)Math.round(zone.centerY * cs + cs / 2.0);
                    int radiusPx = (int)Math.round(zone.radius * cs);
                    g.setColor(withAlpha(ACCENT, 28));
                    g.fillOval(cx - radiusPx, cy - radiusPx, radiusPx * 2, radiusPx * 2);
                    g.setColor(withAlpha(ACCENT, 180));
                    g.drawOval(cx - radiusPx, cy - radiusPx, radiusPx * 2, radiusPx * 2);
                    g.setFont(new Font("SansSerif", Font.BOLD, (int)Math.max(10, cs * 1.1)));
                    g.setColor(textPri());
                    String label = "Z" + (i + 1);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(label, cx - fm.stringWidth(label) / 2, cy - radiusPx - 6);
                }
            }

            if (displayedTasks != null && !displayedTasks.isEmpty()) {
                for (Task task : displayedTasks) {
                    if (task == null) {
                        continue;
                    }
                    drawPointLabel(g, task.getPickupX(), task.getPickupY(), START_CLR, "P" + task.getTaskId());
                    drawPointLabel(g, task.getDeliveryX(), task.getDeliveryY(), GOAL_CLR, "D" + task.getTaskId());
                    g.setColor(withAlpha(PATH_CLR, 70));
                    g.setStroke(new BasicStroke(Math.max(1.0f, (float)(cs * 0.25))));
                    int x1 = (int)Math.round(task.getPickupX() * cs + cs / 2.0);
                    int y1 = (int)Math.round(task.getPickupY() * cs + cs / 2.0);
                    int x2 = (int)Math.round(task.getDeliveryX() * cs + cs / 2.0);
                    int y2 = (int)Math.round(task.getDeliveryY() * cs + cs / 2.0);
                    g.drawLine(x1, y1, x2, y2);
                }
            }

            try{
                int sr=Integer.parseInt(startRow.getText().trim());
                int sc=Integer.parseInt(startCol.getText().trim());
                drawEP(g,new Pt(sc, sr),START_CLR,"S");
            }catch(Exception ignore){}

            if (executedTasks != null && !executedTasks.isEmpty()) {
                for (DeliveryTask t : executedTasks) {
                    Color c = t.onTime ? SUCCESS_CLR : GOAL_CLR;
                    drawEP(g, t.pt, c, "T" + t.execOrder);
                }
            } else {
                for (DeliveryTask t : targets) {
                    drawEP(g, t.pt, GOAL_CLR, "D" + t.originalId);
                }
            }
        }
        
        void drawEP(Graphics2D g,Pt p,Color c,String lbl){
            int cx=(int)Math.round(p.x*cs + cs/2.0);
            int cy=(int)Math.round(p.y*cs + cs/2.0);
            int sz=(int)Math.max(6, cs+4);

            g.setColor(withAlpha(c, 60));
            g.fillOval(cx-sz/2-4,cy-sz/2-4,sz+8,sz+8);
            g.setColor(withAlpha(Color.BLACK, 90));
            g.fillOval(cx-sz/2+1,cy-sz/2+2,sz,sz);

            g.setColor(c); g.fillOval(cx-sz/2,cy-sz/2,sz,sz);
            g.setColor(bgDeep()); g.setFont(new Font("SansSerif",Font.BOLD,(int)Math.max(8,cs+1)));
            FontMetrics fm=g.getFontMetrics();
            g.drawString(lbl,cx-fm.stringWidth(lbl)/2,cy+fm.getAscent()/2-1);
        }

        void drawPointLabel(Graphics2D g, double x, double y, Color c, String lbl) {
            int cx=(int)Math.round(x*cs + cs/2.0);
            int cy=(int)Math.round(y*cs + cs/2.0);
            int sz=(int)Math.max(6, cs+2);
            g.setColor(withAlpha(c, 60));
            g.fillOval(cx-sz/2-3,cy-sz/2-3,sz+6,sz+6);
            g.setColor(c); g.fillOval(cx-sz/2,cy-sz/2,sz,sz);
            g.setColor(bgDeep()); g.setFont(new Font("SansSerif",Font.BOLD,(int)Math.max(8,cs+1)));
            FontMetrics fm=g.getFontMetrics();
            g.drawString(lbl,cx-fm.stringWidth(lbl)/2,cy+fm.getAscent()/2-1);
        }
    }

    void chooseMap(){
        JFileChooser chooser = new JFileChooser();
        try {
            File curr = new File(mapPath).getAbsoluteFile().getParentFile();
            if (curr != null && curr.exists()) chooser.setCurrentDirectory(curr);
            else chooser.setCurrentDirectory(new File("."));
        } catch (Exception e) { chooser.setCurrentDirectory(new File(".")); }

        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Map files (*.map, *.txt)","map","txt"));
        if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
            mapPath = chooser.getSelectedFile().getAbsolutePath();
            tryLoadMap(mapPath);
        }
    }

    void tryLoadMap(String path){
        log("Loading: "+path);

        targets.clear();
        executedTasks.clear();
        currentPath.clear();
        directLines.clear();
        segmentValidCache.clear();
        allHulls.clear();
        refreshTargetsUI();

        new SwingWorker<Void,Void>(){
            protected Void doInBackground() throws Exception{ loadMap(path); buildHulls(); return null; }
            protected void done(){
                try{get();}catch(Exception ex){log("ERROR: "+ex.getMessage());return;}

                int maxDim = Math.max(rows, cols);
                if (maxDim >= 1000) mapPanel.cs = 0.55;
                else if (maxDim > 512) mapPanel.cs = 1.0;
                else if (maxDim > 256) mapPanel.cs = 2.0;
                else if (maxDim > 128) mapPanel.cs = 3.0;
                else mapPanel.cs = 6.0;

                mapPanel.setPreferredSize(new Dimension((int)Math.round(cols*mapPanel.cs), (int)Math.round(rows*mapPanel.cs)));
                recomputeZoneMask();
                mapPanel.revalidate(); mapPanel.repaint();
                status("Loaded "+rows+"×"+cols + " | " + allHulls.size() + " hulls");
                log("Done. "+rows+" rows × "+cols+" cols. " + allHulls.size() + " obstacle hulls.");
            }
        }.execute();
    }

    static void loadMap(String file) throws Exception{
        BufferedReader br=new BufferedReader(new FileReader(file));
        List<String> lines=new ArrayList<>(); String line;
        while((line=br.readLine())!=null){
            if(line.startsWith("type")||line.startsWith("height")||line.startsWith("width")||line.startsWith("map")) continue;
            if(!line.isEmpty()) lines.add(line);
        }
        br.close();
        rows=lines.size(); cols=lines.get(0).length();
        grid=new char[rows][cols];
        for(int i=0;i<rows;i++){
            for(int j=0; j<cols; j++) {
                char c = lines.get(i).charAt(j);
                if (c == 'T' || c == 'O' || c == 'W' || c == '@') grid[i][j] = '@';
                else grid[i][j] = '.';
            }
        }
    }

    static void buildHulls(){
        boolean[][] seen = new boolean[rows][cols];
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1},{1,1},{-1,-1},{1,-1},{-1,1}};

        for(int i=0; i<rows; i++){
            for(int j=0; j<cols; j++){
                if(grid[i][j]=='@' && !seen[i][j]){
                    List<Pt> corners = new ArrayList<>();
                    Queue<int[]> q = new LinkedList<>();
                    q.add(new int[]{i,j}); seen[i][j]=true;
                    while(!q.isEmpty()){
                        int[] p = q.poll();
                        int r = p[0], c = p[1];
                        corners.add(new Pt(c, r)); corners.add(new Pt(c+1, r));
                        corners.add(new Pt(c, r+1)); corners.add(new Pt(c+1, r+1));
                        for(int[] d: dirs){
                            int nr = r+d[0], nc = c+d[1];
                            if(nr>=0 && nc>=0 && nr<rows && nc<cols && grid[nr][nc]=='@' && !seen[nr][nc]){
                                seen[nr][nc] = true; q.add(new int[]{nr, nc});
                            }
                        }
                    }
                    List<Pt> hull = convexHull(corners);
                    if(hull.size() >= 3) allHulls.add(hull);
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // GREEDY MULTI-TARGET ROUTING ALGORITHM
    // ═══════════════════════════════════════

    void run(){
        int sr, sc;
        try{
            sr=Integer.parseInt(startRow.getText().trim());
            sc=Integer.parseInt(startCol.getText().trim());
        }catch(NumberFormatException ex){log("Bad Source coordinates.");return;}

        if(grid==null){log("No map loaded.");return;}
        if(targets.isEmpty()) { log("Please add at least 1 Destination."); return; }

        log("──────────────────────────────────────");
        //log("Running Greedy EDF (Earliest Deadline First) Scheduler...");
        log("Running Scheduler...");
        status("Computing scheduled route…"); runBtn.setEnabled(false);

        Pt source = new Pt(sc, sr);
        List<DeliveryTask> captureTasks = new ArrayList<>(targets);

        new SwingWorker<List<Pt>,Void>(){
            double totalDist = 0;

            protected List<Pt> doInBackground(){
                long t=System.currentTimeMillis();
                
                directLines.clear();
                captureTasks.sort(Comparator.comparingDouble(tsk -> tsk.deadline));

                List<Pt> fullSequentialPath = new ArrayList<>();
                Pt currentStart = source;
                double currentTime = 0;

                for (int i = 0; i < captureTasks.size(); i++) {
                    DeliveryTask currentTask = captureTasks.get(i);
                    currentTask.execOrder = i + 1;
                    
                    directLines.add(new Pt[]{currentStart, currentTask.pt});

                    globalS = currentStart;
                    globalG = currentTask.pt;
                    segmentValidCache.clear();

                    List<List<Pt>> activeHulls = new ArrayList<>(allHulls);
                    List<Pt> geomPath = computeGeometricPath(currentStart, currentTask.pt, activeHulls, new HashSet<>(), 50);
                    geomPath = smoothPath(geomPath, activeHulls);

                    boolean hasClipping = false;
                    for (int k = 0; k < geomPath.size() - 1; k++) {
                        if (!isStrictLOSClear(geomPath.get(k), geomPath.get(k + 1))) { hasClipping = true; break; }
                    }
                    if (hasClipping) geomPath = simplifyGridPath(computeGridShortestPath(currentStart, currentTask.pt));

                    if (fullSequentialPath.isEmpty()) {
                        fullSequentialPath.addAll(geomPath);
                    } else {
                        fullSequentialPath.addAll(geomPath.subList(1, geomPath.size()));
                    }

                    double segmentDistance = totalLength(geomPath);
                    currentTime += segmentDistance;

                    currentTask.arrivalTime = currentTime;
                    currentTask.onTime = (currentTime <= currentTask.deadline);

                    currentStart = currentTask.pt;
                }

                totalDist = currentTime;
                lastMs = System.currentTimeMillis() - t;
                return fullSequentialPath;
            }

            protected void done(){
                try{
                    currentPath = get();
                    executedTasks = captureTasks;
                    mapPanel.repaint();

                    int wpCount = Math.max(0, currentPath.size() - captureTasks.size() - 1);
                    status("Done · " + captureTasks.size() + " scheduled goals · " + lastMs + "ms");
                    
                    log("");
                    log("=================================================");
                    log("         GREEDY SCHEDULING RESULTS");
                    log("=================================================");
                    log("Total Delivery Time (Distance): " + String.format("%.1f", totalDist));
                    log("-------------------------------------------------");
                    for (DeliveryTask t : captureTasks) {
                        String statusTxt = t.onTime ? "ON TIME" : "LATE";
                        log(String.format("T%d (Orig D%d) -> Location: (%d, %d)", t.execOrder, t.originalId, (int)t.pt.x, (int)t.pt.y));
                        log(String.format("   Arrival Time : %.1f", t.arrivalTime));
                        log(String.format("   Deadline     : %.1f", t.deadline));
                        log(String.format("   Status       : %s", statusTxt));
                        log("-------------------------------------------------");
                    }
                    log("");

                }catch(Exception ex){log("ERROR: "+ex.getMessage()); ex.printStackTrace();}
                runBtn.setEnabled(true);
            }
        }.execute();
    }

    static boolean isStrictLOSClear(Pt a, Pt b) {
        List<int[]> line = bresenham((int)a.y, (int)a.x, (int)b.y, (int)b.x);
        for (int[] p : line) {
            if (p[0] >= 0 && p[1] >= 0 && p[0] < rows && p[1] < cols) {
                if (grid[p[0]][p[1]] == '@') return false;
            }
        }
        return true;
    }

    static List<int[]> bresenham(int r0, int c0, int r1, int c1) {
        List<int[]> cells = new ArrayList<>();
        int dr = Math.abs(r1 - r0), sr = r0 < r1 ? 1 : -1;
        int dc = Math.abs(c1 - c0), sc = c0 < c1 ? 1 : -1;
        int err = dr - dc;
        while (true) {
            cells.add(new int[]{r0, c0});
            if (r0 == r1 && c0 == c1) break;
            int e2 = 2 * err;
            if (e2 > -dc) { err -= dc; r0 += sr; }
            if (e2 < dr) { err += dr; c0 += sc; }
        }
        return cells;
    }

    static class Node {
        int r, c; Node parent; double g, f;
        Node(int r, int c, Node p, double g, double h) { this.r = r; this.c = c; this.parent = p; this.g = g; this.f = g + h; }
    }

    static List<Pt> computeGridShortestPath(Pt start, Pt goal) {
        if (grid == null || rows <= 0 || cols <= 0) {
            return new ArrayList<>(Arrays.asList(start, goal));
        }

        int sr = (int) Math.round(start.y);
        int sc = (int) Math.round(start.x);
        int gr = (int) Math.round(goal.y);
        int gc = (int) Math.round(goal.x);

        if (sr < 0 || sc < 0 || gr < 0 || gc < 0 || sr >= rows || sc >= cols || gr >= rows || gc >= cols) {
            return new ArrayList<>(Arrays.asList(start, goal));
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.g));
        Map<Integer, Node> best = new HashMap<>();

        Node startNode = new Node(sr, sc, null, 0.0, 0.0);
        open.add(startNode);
        best.put(sr * cols + sc, startNode);

        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}};

        while (!open.isEmpty()) {
            Node curr = open.poll();
            if (curr.r == gr && curr.c == gc) {
                List<Pt> path = new ArrayList<>();
                while (curr != null) {
                    path.add(0, new Pt(curr.c, curr.r));
                    curr = curr.parent;
                }
                path.set(0, start);
                path.set(path.size() - 1, goal);
                return path;
            }

            for (int[] d : dirs) {
                int nr = curr.r + d[0];
                int nc = curr.c + d[1];
                if (nr < 0 || nc < 0 || nr >= rows || nc >= cols || grid[nr][nc] == '@') {
                    continue;
                }
                if (Math.abs(d[0]) == 1 && Math.abs(d[1]) == 1) {
                    if (grid[curr.r][nc] == '@' || grid[nr][curr.c] == '@') {
                        continue;
                    }
                }

                double stepCost = (Math.abs(d[0]) + Math.abs(d[1]) == 2) ? 1.414 : 1.0;
                double nextCost = curr.g + stepCost;
                int key = nr * cols + nc;
                Node existing = best.get(key);
                if (existing == null || nextCost < existing.g) {
                    Node next = new Node(nr, nc, curr, nextCost, 0.0);
                    best.put(key, next);
                    open.add(next);
                }
            }
        }

        return new ArrayList<>(Arrays.asList(start, goal));
    }

    static List<Pt> simplifyGridPath(List<Pt> raw) {
        if (raw.size() <= 2) return raw;
        List<Pt> res = new ArrayList<>();
        res.add(raw.get(0));
        int curr = 0;
        while (curr < raw.size() - 1) {
            int furthest = curr + 1;
            for (int i = raw.size() - 1; i > curr; i--) {
                if (isStrictLOSClear(raw.get(curr), raw.get(i))) { furthest = i; break; }
            }
            res.add(raw.get(furthest));
            curr = furthest;
        }
        return res;
    }

    static Pt roundPoint(Pt pt) {
        double factor = Math.pow(10, POINT_PRECISION);
        return new Pt(Math.round(pt.x * factor) / factor, Math.round(pt.y * factor) / factor);
    }
    static String segmentKey(Pt p1, Pt p2) {
        Pt a = roundPoint(p1), b = roundPoint(p2);
        if (a.x < b.x || (a.x == b.x && a.y <= b.y)) return a.toString() + "-" + b.toString();
        return b.toString() + "-" + a.toString();
    }
    static double cross(Pt o, Pt a, Pt b) { return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x); }
    static double distance(Pt a, Pt b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    static List<Pt> convexHull(List<Pt> points) {
        List<Pt> p = new ArrayList<>(new HashSet<>(points));
        if (p.size() <= 1) return p;
        p.sort((a, b) -> Math.abs(a.x - b.x) > EPS ? Double.compare(a.x, b.x) : Double.compare(a.y, b.y));

        List<Pt> lower = new ArrayList<>();
        for (Pt q : p) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), q) <= 0)
                lower.remove(lower.size() - 1);
            lower.add(q);
        }
        List<Pt> upper = new ArrayList<>();
        for (int i = p.size() - 1; i >= 0; i--) {
            Pt q = p.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), q) <= 0)
                upper.remove(upper.size() - 1);
            upper.add(q);
        }
        lower.remove(lower.size() - 1); upper.remove(upper.size() - 1);
        lower.addAll(upper); return lower;
    }

    static boolean pointOnSegment(Pt pt, Pt a, Pt b) {
        if (Math.min(a.x, b.x) - EPS <= pt.x && pt.x <= Math.max(a.x, b.x) + EPS &&
            Math.min(a.y, b.y) - EPS <= pt.y && pt.y <= Math.max(a.y, b.y) + EPS) {
            return Math.abs(cross(a, b, pt)) <= EPS;
        }
        return false;
    }
    static double segmentParameter(Pt a, Pt b, Pt pt) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (Math.abs(dx) >= Math.abs(dy)) return Math.abs(dx) < EPS ? 0.0 : (pt.x - a.x) / dx;
        return Math.abs(dy) < EPS ? 0.0 : (pt.y - a.y) / dy;
    }
    static Pt segmentIntersectionPoint(Pt p1, Pt p2, Pt q1, Pt q2) {
        double denom = (p1.x - p2.x) * (q1.y - q2.y) - (p1.y - p2.y) * (q1.x - q2.x);
        if (Math.abs(denom) < EPS) return null;
        double px = ((p1.x * p2.y - p1.y * p2.x) * (q1.x - q2.x) - (p1.x - p2.x) * (q1.x * q2.y - q1.y * q2.x)) / denom;
        double py = ((p1.x * p2.y - p1.y * p2.x) * (q1.y - q2.y) - (p1.y - p2.y) * (q1.x * q2.y - q1.y * q2.x)) / denom;
        Pt point = new Pt(px, py);
        if (pointOnSegment(point, p1, p2) && pointOnSegment(point, q1, q2)) return point;
        return null;
    }

    static boolean samePoint(Pt a, Pt b) { return Math.abs(a.x - b.x) <= 1e-6 && Math.abs(a.y - b.y) <= 1e-6; }

    static boolean pointOnPolygonBoundary(Pt pt, List<Pt> polygon) {
        for (int i = 0; i < polygon.size(); i++) {
            if (pointOnSegment(pt, polygon.get(i), polygon.get((i + 1) % polygon.size()))) return true;
        }
        return false;
    }

    static boolean pointInConvexPolygon(Pt pt, List<Pt> polygon) {
        if (polygon.size() < 3) return false;
        Boolean sign = null;
        for (int i = 0; i < polygon.size(); i++) {
            double value = cross(polygon.get(i), polygon.get((i + 1) % polygon.size()), pt);
            if (Math.abs(value) <= EPS) continue;
            boolean currentSign = value > 0;
            if (sign == null) sign = currentSign;
            else if (currentSign != sign) return false;
        }
        return true;
    }

    static boolean segmentsColinear(Pt a, Pt b, Pt c, Pt d) { return Math.abs(cross(a, b, c)) <= EPS && Math.abs(cross(a, b, d)) <= EPS; }

    static double projectParam(Pt a, Pt b, Pt p) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (Math.abs(dx) >= Math.abs(dy)) return Math.abs(dx) > EPS ? (p.x - a.x) / dx : 0.0;
        return Math.abs(dy) > EPS ? (p.y - a.y) / dy : 0.0;
    }

    static boolean segmentOverlapsStrict(Pt p1, Pt p2, Pt q1, Pt q2) {
        if (!segmentsColinear(p1, p2, q1, q2)) return false;
        double t0 = projectParam(p1, p2, q1), t1 = projectParam(p1, p2, q2);
        if (t0 > t1) { double temp = t0; t0 = t1; t1 = temp; }
        if (t1 < -EPS || t0 > 1 + EPS) return false;
        if (t0 <= 0 + EPS && t1 <= 0 + EPS) return false;
        if (t0 >= 1 - EPS && t1 >= 1 - EPS) return false;
        return true;
    }

    static boolean pointsOnSameHullEdge(Pt p1, Pt p2, List<Pt> polygon) {
        for (int i = 0; i < polygon.size(); i++) {
            Pt a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            if (pointOnSegment(p1, a, b) && pointOnSegment(p2, a, b)) return true;
        }
        return false;
    }

    static boolean boundarySegmentValid(Pt p1, Pt p2, List<Pt> polygon) {
        if (!pointOnPolygonBoundary(p1, polygon) || !pointOnPolygonBoundary(p2, polygon)) return false;
        return pointsOnSameHullEdge(p1, p2, polygon);
    }

    static boolean segmentCrossesHull(Pt p1, Pt p2, List<Pt> polygon) {
        double minx = Double.MAX_VALUE, maxx = -Double.MAX_VALUE;
        double miny = Double.MAX_VALUE, maxy = -Double.MAX_VALUE;
        for (Pt pt : polygon) {
            minx = Math.min(minx, pt.x); maxx = Math.max(maxx, pt.x);
            miny = Math.min(miny, pt.y); maxy = Math.max(maxy, pt.y);
        }
        if (Math.max(p1.x, p2.x) < minx - EPS || Math.min(p1.x, p2.x) > maxx + EPS ||
            Math.max(p1.y, p2.y) < miny - EPS || Math.min(p1.y, p2.y) > maxy + EPS) return false;

        boolean p1On = pointOnPolygonBoundary(p1, polygon), p2On = pointOnPolygonBoundary(p2, polygon);
        if (pointInConvexPolygon(p1, polygon) && !p1On) return true;
        if (pointInConvexPolygon(p2, polygon) && !p2On) return true;
        if (p1On && p2On) return !boundarySegmentValid(p1, p2, polygon);

        boolean hasIntersections = false;
        for (int i = 0; i < polygon.size(); i++) {
            Pt a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            if (segmentOverlapsStrict(p1, p2, a, b)) return true;
            Pt pt = segmentIntersectionPoint(p1, p2, a, b);
            if (pt == null) continue;
            if (samePoint(pt, p1) || samePoint(pt, p2)) continue;
            if (!samePoint(pt, a) && !samePoint(pt, b)) return true;
            hasIntersections = true;
        }
        if (!hasIntersections) return false;
        return pointInConvexPolygon(new Pt((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0), polygon);
    }

    static boolean segmentValid(Pt p1, Pt p2, List<List<Pt>> hulls) {
        if (!isStrictLOSClear(p1, p2)) return false;
        for (List<Pt> hull : hulls) {
            if (globalS != null && pointInConvexPolygon(globalS, hull)) continue;
            if (globalG != null && pointInConvexPolygon(globalG, hull)) continue;
            if (segmentCrossesHull(p1, p2, hull)) return false;
        }
        return true;
    }

    static boolean segmentValidCached(Pt p1, Pt p2, List<List<Pt>> hulls) {
        String key = segmentKey(p1, p2);
        if (segmentValidCache.containsKey(key)) return segmentValidCache.get(key);
        boolean valid = segmentValid(p1, p2, hulls);
        segmentValidCache.put(key, valid);
        return valid;
    }

    static boolean validatePath(List<Pt> path, List<List<Pt>> hulls) {
        if (path == null || path.size() < 2) return false;
        for (int i = 0; i < path.size() - 1; i++) {
            if (!segmentValidCached(path.get(i), path.get(i + 1), hulls)) return false;
        }
        return true;
    }

    static Pt[] getOuterTangents(Pt S, Pt D, List<Pt> hull) {
        int leftIdx = 0, rightIdx = 0;
        double maxLeft = -Double.MAX_VALUE, maxRight = -Double.MAX_VALUE;
        for (int i = 0; i < hull.size(); i++) {
            double cp = cross(S, D, hull.get(i)), dist = distance(S, hull.get(i));
            if (dist < EPS) continue;
            double score = cp / dist;
            if (score > maxLeft) { maxLeft = score; leftIdx = i; }
            if (-score > maxRight) { maxRight = -score; rightIdx = i; }
        }
        return new Pt[]{hull.get(leftIdx), hull.get(rightIdx)};
    }

    static double totalLength(List<Pt> p) {
        double len = 0;
        for (int i = 0; i < p.size() - 1; i++) len += distance(p.get(i), p.get(i + 1));
        return len;
    }

    static List<Pt> computeGeometricPath(Pt source, Pt destination, List<List<Pt>> hulls, Set<Integer> visitedHulls, int maxDepth) {
        if (segmentValidCached(source, destination, hulls)) return new ArrayList<>(Arrays.asList(source, destination));

        List<Pt> closestHull = null;
        int closestHullIdx = -1;
        double minDist = Double.MAX_VALUE;

        for (int idx = 0; idx < hulls.size(); idx++) {
            if (visitedHulls.contains(idx)) continue;
            List<Pt> hull = hulls.get(idx);
            if (pointInConvexPolygon(globalS, hull) || pointInConvexPolygon(globalG, hull)) continue;
            if (!segmentCrossesHull(source, destination, hull)) continue;

            double d = Double.MAX_VALUE;
            for (Pt v : hull) d = Math.min(d, distance(source, v));
            if (d < minDist) { minDist = d; closestHull = hull; closestHullIdx = idx; }
        }

        if (closestHull == null || visitedHulls.size() >= maxDepth) return new ArrayList<>(Arrays.asList(source, destination));

        visitedHulls.add(closestHullIdx);
        Pt[] tangents = getOuterTangents(source, destination, closestHull);
        Pt leftT = tangents[0], rightT = tangents[1];

        Set<Integer> vLeft1 = new HashSet<>(visitedHulls), vLeft2 = new HashSet<>(visitedHulls);
        List<Pt> leftPath = computeGeometricPath(source, leftT, hulls, vLeft1, maxDepth);
        List<Pt> leftTail = computeGeometricPath(leftT, destination, hulls, vLeft2, maxDepth);
        leftPath.addAll(leftTail.subList(1, leftTail.size()));

        Set<Integer> vRight1 = new HashSet<>(visitedHulls), vRight2 = new HashSet<>(visitedHulls);
        List<Pt> rightPath = computeGeometricPath(source, rightT, hulls, vRight1, maxDepth);
        List<Pt> rightTail = computeGeometricPath(rightT, destination, hulls, vRight2, maxDepth);
        rightPath.addAll(rightTail.subList(1, rightTail.size()));

        boolean leftValid = validatePath(leftPath, hulls), rightValid = validatePath(rightPath, hulls);
        List<List<Pt>> candidates = new ArrayList<>();
        if (leftValid) candidates.add(leftPath);
        if (rightValid) candidates.add(rightPath);

        if (!candidates.isEmpty()) {
            List<Pt> best = candidates.get(0);
            for (List<Pt> c : candidates) if (totalLength(c) < totalLength(best)) best = c;
            return best;
        }
        return new ArrayList<>(Arrays.asList(source, destination));
    }

    static List<Pt> smoothPath(List<Pt> path, List<List<Pt>> hulls) {
        if (path == null || path.size() <= 2) return path;
        List<Pt> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));
        int i = 0, n = path.size();
        while (i < n - 1) {
            int j = n - 1;
            boolean found = false;
            while (j > i) {
                if (segmentValidCached(smoothed.get(smoothed.size() - 1), path.get(j), hulls)) {
                    smoothed.add(path.get(j)); i = j; found = true; break;
                }
                j--;
            }
            if (!found) { smoothed.add(path.get(i + 1)); i++; }
        }
        return smoothed;
    }

    JLabel secLbl(String t){
        JLabel l = new JLabel(t, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif",Font.BOLD,10)); 
        l.setForeground(textDim());
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setMaximumSize(new Dimension(320, 20));
        secLabels.add(l); 
        return l;
    }
    JButton btn(String t,Color fg, boolean primary){
        return new RoundedButton(t, fg, primary);
    }
    JPanel wrap(JComponent c){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,16,2)); p.setOpaque(false); p.add(c); p.setMaximumSize(new Dimension(330,50)); return p;
    }
    JPanel legRow(Color c,String t){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,1));
        p.setOpaque(false); p.setMaximumSize(new Dimension(220,20));
        JComponent dot = swatch(c, 12);
        JLabel lbl = new JLabel(t); lbl.setFont(FONT_M); lbl.setForeground(textSec());
        secondaryLabels.add(lbl);
        p.add(Box.createHorizontalStrut(8)); p.add(dot); p.add(lbl); return p;
    }

    void log(String m){ SwingUtilities.invokeLater(()->{ logArea.append(m+"\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); }); }
    void status(String m){ SwingUtilities.invokeLater(()->statusLbl.setText(m)); }
}