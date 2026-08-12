import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MCHRouteGUI_WithAStar extends JFrame {

    // ═══════════════════════════════════════════════════════
    // THEME — dark + light token sets
    // ═══════════════════════════════════════════════════════
    static boolean darkMode = false;

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

    static final Color ACCENT     = new Color(0x00, 0xC8, 0x75); 
    static final Color ASTAR_CLR  = new Color(0x8A, 0x2B, 0xE2); // Purple for A*
    static final Color HULL_CLR   = new Color(0xF5, 0xA6, 0x23, 180);
    static final Color START_CLR  = new Color(0x00, 0xC8, 0x75);
    static final Color GOAL_CLR   = new Color(0xF0, 0x3E, 0x52);
    static final Color SUCCESS_CLR= new Color(0x00, 0xC8, 0x75);
    static final Color PATH_CLR   = new Color(0x2B, 0x8A, 0xF6, 240); // Blue for MCH
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
    
    public static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static final double EPS = 1e-9;
    static final int POINT_PRECISION = 6;

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

    // ═══════════════════════════════════════
    // MAP DATA & THREAD-SAFE LISTS
    // ═══════════════════════════════════════
    static int rows, cols;
    static char[][] grid;
    
    static List<List<Pt>> allHulls = new CopyOnWriteArrayList<>();
    static Map<String, Boolean> segmentValidCache = new HashMap<>();

    static int r(Pt p){ return (int)Math.floor(p.y); }
    static int c(Pt p){ return (int)Math.floor(p.x); }
    static int key(int r, int c){ return r * 100000 + c; }
    static int key(Pt p){ return key(r(p), c(p)); }

    // GUI STATE
    MapPanel   mapPanel;
    JTextArea  logArea;
    HintTextField startRow, startCol, targetRow, targetCol, targetDeadline;
    JLabel     statusLbl;
    JButton    runBtn, runAStarBtn, clearBtn, loadBtn, themeBtn;
    String     mapPath = "Coding Java/MAPF/Berlin_1_256.map";

    List<DeliveryTask> targets = new CopyOnWriteArrayList<>();
    List<DeliveryTask> executedTasks = new CopyOnWriteArrayList<>();
    
    // SEPARATED PATHS FOR COMPARISON
    List<Pt> mchPath = new CopyOnWriteArrayList<>();
    List<Pt> aStarPath = new CopyOnWriteArrayList<>();
    List<Pt> mchWaypoints = new CopyOnWriteArrayList<>();
    List<Pt> aStarWaypoints = new CopyOnWriteArrayList<>();
    
    List<Pt[]> directLines = new CopyOnWriteArrayList<>(); 
    List<List<Pt>> nearHulls = new CopyOnWriteArrayList<>();
    
    long lastMs = 0;
    int pickMode = 0;

    JPanel ctrlPanel, topBar, destListPanel;
    JScrollPane mapScroll, logScroll;
    List<JLabel> secLabels = new ArrayList<>();
    List<JLabel> secondaryLabels = new ArrayList<>();

    public static void main(String[] a){
        SwingUtilities.invokeLater(()->new MCHRouteGUI_WithAStar().setVisible(true));
    }

    MCHRouteGUI_WithAStar(){
        super("PRS Geometric Engine + A*");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 800);
        setMinimumSize(new Dimension(1100,700));
        setExtendedState(JFrame.MAXIMIZED_BOTH); 
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

            Color accentToUse = (customFg != null && primary) ? customFg : ACCENT;

            if(primary){
                Color base = darkMode ? new Color(0x0C, 0x10, 0x16) : new Color(0xFF, 0xFF, 0xFF);
                Color fill = pressing ? blend(base, accentToUse, 0.15) : (hovering ? blend(base, accentToUse, 0.08) : base);
                g.setColor(fill); 
                g.fillRoundRect(1, 1, w-2, h-2, 8, 8); 
                g.setColor(blend(base, accentToUse, 0.4)); 
                g.setStroke(new BasicStroke(1.2f)); 
                g.drawRoundRect(1, 1, w-2, h-2, 8, 8); 
                setForeground(accentToUse);
            } else {
                Color base = bgCard();
                Color fill = pressing ? blend(base, Color.BLACK, 0.15) : (hovering ? blend(base, Color.WHITE, 0.12) : base);
                g.setColor(fill); 
                g.fillRoundRect(1, 1, w-2, h-2, 8, 8);
                g.setColor(border()); 
                g.setStroke(new BasicStroke(1.0f)); 
                g.drawRoundRect(1, 1, w-2, h-2, 8, 8);
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
    // UI BUILDER
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
        JLabel title=new JLabel(" PRS Geometric Engine & A*");
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

        ctrlPanel.add(secLbl("CHOOSE THE MAP FILE")); ctrlPanel.add(Box.createVerticalStrut(8));
        loadBtn=btn("Load .map file", null, true);
        loadBtn.setPreferredSize(new Dimension(250,34));
        loadBtn.addActionListener(e->chooseMap());
        ctrlPanel.add(wrap(loadBtn)); 
        JLabel ml=new JLabel("  Map is loaded"); ml.setFont(FONT_M); ml.setForeground(textSec());
        secondaryLabels.add(ml);
        ctrlPanel.add(wrap(ml)); 
        ctrlPanel.add(Box.createVerticalStrut(18));

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
                addTarget(new Pt(c + 0.5, r + 0.5), dl); 
                targetRow.setText(""); targetCol.setText(""); targetDeadline.setText("");
            } catch(Exception ex){ status("Invalid manual destination / deadline!"); }
        });
        clrTgt.addActionListener(e->{ 
            targets.clear(); executedTasks.clear(); directLines.clear(); nearHulls.clear();
            mchPath.clear(); aStarPath.clear(); mchWaypoints.clear(); aStarWaypoints.clear();
            refreshTargetsUI(); mapPanel.repaint(); status("Destinations cleared.");
        });
        JPanel addClrP = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); addClrP.setOpaque(false);
        addClrP.add(Box.createHorizontalStrut(20)); addClrP.add(bAddG); addClrP.add(clrTgt);
        ctrlPanel.add(addClrP); ctrlPanel.add(Box.createVerticalStrut(15));

        destListPanel = new JPanel();
        destListPanel.setLayout(new BoxLayout(destListPanel, BoxLayout.Y_AXIS));
        destListPanel.setOpaque(false);
        ctrlPanel.add(destListPanel);
        refreshTargetsUI();
        ctrlPanel.add(Box.createVerticalStrut(10));

        runBtn=btn("▶ Run PRS Engine", null, true);
        runBtn.setPreferredSize(new Dimension(250,44));
        runBtn.addActionListener(e->run());
        ctrlPanel.add(wrap(runBtn)); 
        
        runAStarBtn=btn("▶ RUN A* ALGORITHM", ASTAR_CLR, true);
        runAStarBtn.setPreferredSize(new Dimension(250,44));
        runAStarBtn.addActionListener(e->runAStar());
        ctrlPanel.add(wrap(runAStarBtn)); 
        
        ctrlPanel.add(Box.createVerticalStrut(8));
        
        clearBtn=btn("Clear path", null, false);
        clearBtn.setPreferredSize(new Dimension(250,34));
        clearBtn.addActionListener(e->{
            mchPath.clear(); aStarPath.clear(); mchWaypoints.clear(); aStarWaypoints.clear();
            executedTasks.clear(); directLines.clear(); nearHulls.clear(); 
            mapPanel.repaint(); log("Cleared.");
        });
        ctrlPanel.add(wrap(clearBtn)); ctrlPanel.add(Box.createVerticalStrut(15));

        JPanel zp=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); zp.setOpaque(false);
        JButton bZI=btn("Zoom +", null, false); bZI.setPreferredSize(new Dimension(110,35));
        JButton bZO=btn("Zoom -", null, false); bZO.setPreferredSize(new Dimension(110,35));
        bZI.addActionListener(e->mapPanel.zoom(1)); bZO.addActionListener(e->mapPanel.zoom(-1));
        zp.add(Box.createHorizontalStrut(8)); zp.add(bZI); zp.add(bZO); 
        ctrlPanel.add(zp);
        ctrlPanel.add(Box.createVerticalStrut(25));

        ctrlPanel.add(secLbl("LEGEND")); ctrlPanel.add(Box.createVerticalStrut(10));
        ctrlPanel.add(legRow(START_CLR,"Source (S₀)"));
        ctrlPanel.add(legRow(GOAL_CLR,"Destinations (D1, D2...)"));
        ctrlPanel.add(legRow(RED_LINE,"Straight Line (Direct)"));
        ctrlPanel.add(legRow(PATH_CLR,"PRS Path (Geometry)"));
        ctrlPanel.add(legRow(ASTAR_CLR,"A* Path (Grid)"));
        ctrlPanel.add(legRow(HULL_CLR,"Exact Concave Hulls"));
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
        logScroll.setBorder(new MatteBorder(0,1,0,0,border()));
        logScroll.setPreferredSize(new Dimension(340,0));
        logScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        logScroll.getVerticalScrollBar().setPreferredSize(new Dimension(10,10));

        JSplitPane sp=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,mapScroll,logScroll);
        sp.setResizeWeight(0.78); sp.setBorder(null);
        add(sp,BorderLayout.CENTER);
    }

    void addTarget(Pt p, double dl) {
        if(targets.size() >= 4) { status("Max 4 destinations reached."); return; }
        targets.add(new DeliveryTask(p, dl, targets.size() + 1));
        
        executedTasks.clear(); directLines.clear(); nearHulls.clear();
        mchPath.clear(); aStarPath.clear(); mchWaypoints.clear(); aStarWaypoints.clear();
        
        refreshTargetsUI();
        mapPanel.repaint();
        status("Added target D" + targets.size() + " at (" + (int)p.x + ", " + (int)p.y + ") DL: " + dl);
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
        logScroll.setBorder(new MatteBorder(0,1,0,0,border()));
        logArea.setBackground(logBg());
        logArea.setForeground(darkMode ? new Color(0x17,0xC7,0x8B) : new Color(0x00,0x80,0x55));
        statusLbl.setForeground(textSec());
        themeBtn.setText(darkMode?"☀  Light":"🌙  Dark");
        
        for(Component comp: ctrlPanel.getComponents()) restyle(comp);
        for(JLabel l: secLabels) l.setForeground(textDim());
        for(JLabel l: secondaryLabels) l.setForeground(textSec()); 
        
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
                    setToolTipText((r>=0&&r<rows&&c>=0&&c<cols)?"("+c+","+r+") "+grid[r][c]:null);
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
                        status("Source set to ("+c+","+r+")");
                        pickMode=0;
                    }
                    else if(mode==2){
                        String input = JOptionPane.showInputDialog(MCHRouteGUI_WithAStar.this, "Enter Deadline for D" + (targets.size() + 1) + ":", "1000");
                        if (input != null && !input.trim().isEmpty()) {
                            try {
                                double dl = Double.parseDouble(input.trim());
                                addTarget(new Pt(c + 0.5, r + 0.5), dl);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(MCHRouteGUI_WithAStar.this, "Invalid deadline entered. Delivery not added.");
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
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Rectangle clip=g.getClipBounds();
            int r0=clip==null?0:Math.max(0,(int)(clip.y/cs));
            int r1=clip==null?rows:Math.min(rows,(int)((clip.y+clip.height)/cs)+2);
            int c0=clip==null?0:Math.max(0,(int)(clip.x/cs));
            int c1=clip==null?cols:Math.min(cols,(int)((clip.x+clip.width)/cs)+2);

            g.setColor(gridFree());
            g.fillRect((int)(c0 * cs), (int)(r0 * cs), (int)Math.ceil((c1 - c0) * cs), (int)Math.ceil((r1 - r0) * cs));

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
            for(List<Pt> hull : nearHulls){
                if(hull.size()<2)continue;
                int[]hx=new int[hull.size()],hy=new int[hull.size()];
                for(int i=0;i<hull.size();i++){
                    hx[i]=(int)Math.round(hull.get(i).x * cs);
                    hy[i]=(int)Math.round(hull.get(i).y * cs);
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
                    int x1 = (int)Math.round(line[0].x*cs);
                    int y1 = (int)Math.round(line[0].y*cs);
                    int x2 = (int)Math.round(line[1].x*cs);
                    int y2 = (int)Math.round(line[1].y*cs);
                    g.drawLine(x1, y1, x2, y2);
                }
            }

            // Draw A* Path (Purple)
            if(aStarPath != null && aStarPath.size() >= 2){
                g.setColor(withAlpha(ASTAR_CLR, 70));
                g.setStroke(new BasicStroke(Math.max(4.0f, (float)(cs * 1.6)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(int i=0;i<aStarPath.size()-1;i++){
                    Pt a=aStarPath.get(i),b=aStarPath.get(i+1);
                    int x1 = (int)Math.round(a.x*cs); int y1 = (int)Math.round(a.y*cs);
                    int x2 = (int)Math.round(b.x*cs); int y2 = (int)Math.round(b.y*cs);
                    g.drawLine(x1, y1, x2, y2);
                }
                g.setColor(ASTAR_CLR);
                g.setStroke(new BasicStroke(Math.max(2.0f, (float)(cs * 0.8)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(int i=0;i<aStarPath.size()-1;i++){
                    Pt a=aStarPath.get(i),b=aStarPath.get(i+1);
                    int x1 = (int)Math.round(a.x*cs); int y1 = (int)Math.round(a.y*cs);
                    int x2 = (int)Math.round(b.x*cs); int y2 = (int)Math.round(b.y*cs);
                    g.drawLine(x1, y1, x2, y2);
                }
            }
            
            // Draw MCH Path (Blue) - Drawn on top of A* so both are visible
            if(mchPath != null && mchPath.size() >= 2){
                g.setColor(withAlpha(PATH_CLR, 70));
                g.setStroke(new BasicStroke(Math.max(4.0f, (float)(cs * 1.6)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(int i=0;i<mchPath.size()-1;i++){
                    Pt a=mchPath.get(i),b=mchPath.get(i+1);
                    int x1 = (int)Math.round(a.x*cs); int y1 = (int)Math.round(a.y*cs);
                    int x2 = (int)Math.round(b.x*cs); int y2 = (int)Math.round(b.y*cs);
                    g.drawLine(x1, y1, x2, y2);
                }
                g.setColor(PATH_CLR);
                g.setStroke(new BasicStroke(Math.max(2.0f, (float)(cs * 0.8)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for(int i=0;i<mchPath.size()-1;i++){
                    Pt a=mchPath.get(i),b=mchPath.get(i+1);
                    int x1 = (int)Math.round(a.x*cs); int y1 = (int)Math.round(a.y*cs);
                    int x2 = (int)Math.round(b.x*cs); int y2 = (int)Math.round(b.y*cs);
                    g.drawLine(x1, y1, x2, y2);
                }
            }

            if (cs >= 2) {
                int sz = (int)Math.max(3, cs);
                g.setStroke(new BasicStroke(1.0f));
                
                // A* Waypoints
                if(aStarWaypoints.size() > 2) {
                    for(int i=1;i<aStarWaypoints.size()-1;i++){
                        Pt wp=aStarWaypoints.get(i);
                        int cx=(int)Math.round(wp.x*cs); int cy=(int)Math.round(wp.y*cs);
                        g.setColor(withAlpha(Color.BLACK,70));
                        g.fillPolygon(new int[]{cx+1,cx+sz+1,cx+1,cx-sz+1},new int[]{cy-sz+2,cy+2,cy+sz+2,cy+2},4);
                        g.setColor(ASTAR_CLR);
                        g.fillPolygon(new int[]{cx,cx+sz,cx,cx-sz},new int[]{cy-sz,cy,cy+sz,cy},4);
                        g.setColor(textPri());
                        g.drawPolygon(new int[]{cx,cx+sz,cx,cx-sz},new int[]{cy-sz,cy,cy+sz,cy},4);
                    }
                }
                
                // MCH Waypoints
                if(mchWaypoints.size() > 2) {
                    for(int i=1;i<mchWaypoints.size()-1;i++){
                        Pt wp=mchWaypoints.get(i);
                        int cx=(int)Math.round(wp.x*cs); int cy=(int)Math.round(wp.y*cs);
                        g.setColor(withAlpha(Color.BLACK,70));
                        g.fillPolygon(new int[]{cx+1,cx+sz+1,cx+1,cx-sz+1},new int[]{cy-sz+2,cy+2,cy+sz+2,cy+2},4);
                        g.setColor(WP_CLR);
                        g.fillPolygon(new int[]{cx,cx+sz,cx,cx-sz},new int[]{cy-sz,cy,cy+sz,cy},4);
                        g.setColor(textPri());
                        g.drawPolygon(new int[]{cx,cx+sz,cx,cx-sz},new int[]{cy-sz,cy,cy+sz,cy},4);
                    }
                }
            }

            try{
                int sr=Integer.parseInt(startRow.getText().trim());
                int sc=Integer.parseInt(startCol.getText().trim());
                drawEP(g,new Pt(sc + 0.5, sr + 0.5),START_CLR,"S");
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
            int cx=(int)Math.round(p.x*cs);
            int cy=(int)Math.round(p.y*cs);
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

        targets.clear(); executedTasks.clear(); directLines.clear();
        mchPath.clear(); aStarPath.clear();
        segmentValidCache.clear(); allHulls.clear(); nearHulls.clear();
        mchWaypoints.clear(); aStarWaypoints.clear();
        
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
                mapPanel.revalidate(); mapPanel.repaint();
                status("Loaded "+rows+"×"+cols + " | " + allHulls.size() + " exact perimeters");
                log("Done. "+rows+" rows × "+cols+" cols. " + allHulls.size() + " exact boundary polygons.");
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

    // ════════════════════════════════════════════════════════
    // EXACT INFLATED CONCAVE POLYGON EXTRACTION
    // ════════════════════════════════════════════════════════
    static void buildHulls(){
        allHulls.clear();
        boolean[][] seen = new boolean[rows][cols];
        double inf = 0.05; 

        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++){
                if(grid[i][j]=='@' && !seen[i][j]){
                    List<Pt> perimeter = tracePerimeter(i, j);
                    if(!perimeter.isEmpty()) {
                        perimeter = simplifyPolygon(perimeter);
                        perimeter = inflatePolygon(perimeter, inf);
                        allHulls.add(perimeter);
                        floodFillCluster(i, j, seen);
                    }
                }
            }
        }
    }

    static List<Pt> tracePerimeter(int startR, int startC) {
        List<Pt> perim = new ArrayList<>();
        int[] dr = {-1, -1, 0, 1, 1, 1, 0, -1};
        int[] dc = {0, 1, 1, 1, 0, -1, -1, -1};
        
        int bDir = -1;
        for(int i=0; i<8; i++){
            int r = startR+dr[i], c = startC+dc[i];
            if(r<0 || c<0 || r>=rows || c>=cols || grid[r][c]=='.') { bDir = i; break; }
        }
        if(bDir == -1) { perim.add(new Pt(startC, startR)); return perim; } 
        
        int currR = startR, currC = startC;
        int currB = bDir;
        Set<Integer> added = new HashSet<>();
        
        do {
            if(!added.contains(key(currR, currC))) {
                perim.add(new Pt(currC, currR));
                added.add(key(currR, currC));
            }
            
            boolean found = false;
            for(int i=1; i<=8; i++) {
                int checkDir = (currB + i) % 8;
                int nr = currR + dr[checkDir];
                int nc = currC + dc[checkDir];
                
                if(nr>=0 && nc>=0 && nr<rows && nc<cols && grid[nr][nc]=='@') {
                    int freeDir = (currB + i - 1) % 8;
                    int freeR = currR + dr[freeDir];
                    int freeC = currC + dc[freeDir];
                    
                    currR = nr; currC = nc;
                    currB = -1;
                    for(int j=0; j<8; j++){
                        if(currR + dr[j] == freeR && currC + dc[j] == freeC) { currB = j; break; }
                    }
                    if(currB == -1) currB = (checkDir + 4) % 8; 
                    
                    found = true; break;
                }
            }
            if(!found || perim.size() > rows*cols) break; 
        } while(!(currR == startR && currC == startC));
        
        return perim;
    }

    static void floodFillCluster(int sr, int sc, boolean[][] seen) {
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{sr,sc}); seen[sr][sc] = true;
        int[][] dirs={{-1,0},{1,0},{0,-1},{0,1}};
        while(!q.isEmpty()){
            int[] p = q.poll();
            for(int[] d:dirs){
                int nr=p[0]+d[0], nc=p[1]+d[1];
                if(nr>=0 && nc>=0 && nr<rows && nc<cols && grid[nr][nc]=='@' && !seen[nr][nc]){
                    seen[nr][nc] = true; q.add(new int[]{nr,nc});
                }
            }
        }
    }

    static List<Pt> simplifyPolygon(List<Pt> poly) {
        if (poly.size() <= 2) return poly;
        List<Pt> sim = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Pt prev = poly.get((i - 1 + n) % n);
            Pt curr = poly.get(i);
            Pt next = poly.get((i + 1) % n);
            if (Math.abs(cross(prev, curr, next)) > 1e-4) {
                sim.add(curr);
            }
        }
        return sim;
    }

    static List<Pt> inflatePolygon(List<Pt> poly, double inf) {
        if (poly.size() < 3) return poly;
        List<Pt> inflated = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Pt prev = poly.get((i - 1 + n) % n);
            Pt curr = poly.get(i);
            Pt next = poly.get((i + 1) % n);
            
            double dx1 = curr.x - prev.x, dy1 = curr.y - prev.y;
            double len1 = Math.hypot(dx1, dy1);
            if(len1 > 0) { dx1 /= len1; dy1 /= len1; }
            double nx1 = dy1, ny1 = -dx1; 
            
            double dx2 = next.x - curr.x, dy2 = next.y - curr.y;
            double len2 = Math.hypot(dx2, dy2);
            if(len2 > 0) { dx2 /= len2; dy2 /= len2; }
            double nx2 = dy2, ny2 = -dx2;
            
            Pt p1Start = new Pt(prev.x + nx1*inf, prev.y + ny1*inf);
            Pt p1End = new Pt(curr.x + nx1*inf, curr.y + ny1*inf);
            Pt p2Start = new Pt(curr.x + nx2*inf, curr.y + ny2*inf);
            Pt p2End = new Pt(next.x + nx2*inf, next.y + ny2*inf);
            
            Pt inter = lineIntersect(p1Start, p1End, p2Start, p2End);
            if (inter != null) inflated.add(inter);
            else inflated.add(new Pt(curr.x + nx1*inf, curr.y + ny1*inf));
        }
        return inflated;
    }

    // ═══════════════════════════════════════
    // EXECUTION ENGINE: MCH
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
        log("Running PYTHON GEOMETRIC Scheduler...");
        status("Computing scheduled route…"); 
        runBtn.setEnabled(false); runAStarBtn.setEnabled(false);

        Pt source = new Pt(sc + 0.5, sr + 0.5);
        List<DeliveryTask> captureTasks = new ArrayList<>(targets);

        new SwingWorker<List<Pt>,Void>(){
            double totalDist = 0;

            protected List<Pt> doInBackground(){
                long t=System.currentTimeMillis();
                
                directLines.clear();
                nearHulls.clear();
                mchWaypoints.clear();
                mchPath.clear();
                
                captureTasks.sort(Comparator.comparingDouble(tsk -> tsk.deadline));

                List<Pt> fullSequentialPath = new ArrayList<>();
                Pt currentStart = source;
                double currentTime = 0;

                for (int i = 0; i < captureTasks.size(); i++) {
                    DeliveryTask currentTask = captureTasks.get(i);
                    currentTask.execOrder = i + 1;
                    
                    // ═══════════════════════════════════════════════════════
                    // DESTINATION OBSTACLE CHECK
                    // ═══════════════════════════════════════════════════════
                    Pt adjustedDest = currentTask.pt;
                    List<Pt> targetHull = null;
                    
                    // Check if the point is strictly inside any obstacle
                    for (List<Pt> hull : allHulls) {
                        if (pointStrictlyInPolygon(adjustedDest, hull)) {
                            targetHull = hull;
                            break;
                        }
                    }
                    
                    if (targetHull != null) {
                        Pt bestSidePt = null;
                        double bestT = Double.MAX_VALUE;
                        
                        // Find the first intersection of a line drawn from start to target
                        for (int j = 0; j < targetHull.size(); j++) {
                            Pt a = targetHull.get(j);
                            Pt b = targetHull.get((j + 1) % targetHull.size());
                            Pt intPt = segmentIntersectionPoint(currentStart, adjustedDest, a, b);
                            if (intPt != null) {
                                double param = segmentParameterUnclamped(currentStart, adjustedDest, intPt);
                                if (param >= -EPS && param <= 1 + EPS && param < bestT) {
                                    bestT = param;
                                    bestSidePt = intPt;
                                }
                            }
                        }
                        
                        // Fallback: If line of sight fails, find the closest geometric point on the perimeter
                        if (bestSidePt == null) {
                            double closestDist = Double.MAX_VALUE;
                            for (int j = 0; j < targetHull.size(); j++) {
                                Pt a = targetHull.get(j);
                                Pt b = targetHull.get((j + 1) % targetHull.size());
                                Pt proj = getClosestPointOnSegment(adjustedDest, a, b);
                                double d = distance(adjustedDest, proj);
                                if (d < closestDist) {
                                    closestDist = d;
                                    bestSidePt = proj;
                                }
                            }
                        }
                        
                        if (bestSidePt != null) {
                            // Pull the point slightly backward toward the start to avoid floating-point trap
                            double dirX = currentStart.x - bestSidePt.x;
                            double dirY = currentStart.y - bestSidePt.y;
                            double len = Math.hypot(dirX, dirY);
                            if (len > EPS) {
                                adjustedDest = new Pt(bestSidePt.x + (dirX/len)*0.01, bestSidePt.y + (dirY/len)*0.01);
                            } else {
                                adjustedDest = bestSidePt;
                            }
                            log("Warning: Target T" + currentTask.execOrder + " is inside an obstacle! Stopping at edge: " + adjustedDest);
                        }
                    }
                    // ═══════════════════════════════════════════════════════

                    directLines.add(new Pt[]{currentStart, adjustedDest});
                    
                    log("Task T" + currentTask.execOrder + ": Routing...");
                    segmentValidCache.clear();
                    
                    // PYTHON 1:1 Implementation with Macro-Evaluation
                    List<Pt> rawPath = computeDetourPath(currentStart, adjustedDest, allHulls);
                    List<Pt> geomPath = smoothPath(rawPath, allHulls);
                    mchWaypoints.addAll(geomPath);

                    if (fullSequentialPath.isEmpty()) {
                        fullSequentialPath.addAll(geomPath);
                    } else {
                        fullSequentialPath.addAll(geomPath.subList(1, geomPath.size()));
                    }

                    double segmentDistance = totalLength(geomPath);
                    currentTime += segmentDistance;

                    currentTask.arrivalTime = currentTime;
                    currentTask.onTime = (currentTime <= currentTask.deadline);
                    currentTask.pt = adjustedDest; // Snaps visual marker to the valid stopping point

                    currentStart = adjustedDest;
                }

                // Add used hulls to map rendering
                Set<Integer> ps = new HashSet<>();
                for(Pt p : mchWaypoints) ps.add(key(p));
                for(List<Pt> hull : allHulls){
                    boolean near = false;
                    for(Pt hp : hull) {
                        for(int dr = -2; dr <= 2; dr++) {
                            for(int dc = -2; dc <= 2; dc++){
                                if(ps.contains(key(r(hp) + dr, c(hp) + dc)) || ps.contains(key(hp))){ 
                                    nearHulls.add(hull); near = true; break; 
                                }
                            }
                            if(near) break;
                        }
                        if(near) break;
                    }
                }

                totalDist = currentTime;
                lastMs = System.currentTimeMillis() - t;
                return fullSequentialPath;
            }

            protected void done(){
                try{
                    List<Pt> returnedPath = get();
                    mchPath.addAll(returnedPath);
                    
                    executedTasks.clear();
                    executedTasks.addAll(captureTasks);
                    
                    mapPanel.repaint();

                    status("Done PRS · " + captureTasks.size() + " scheduled goals · " + lastMs + "ms");
                    
                    log("");
                    log("=================================================");
                    log("         PRS SCHEDULING RESULTS");
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
                runBtn.setEnabled(true); runAStarBtn.setEnabled(true);
            }
        }.execute();
    }
    
    // ═══════════════════════════════════════
    // EXECUTION ENGINE: A* ALGORITHM
    // ═══════════════════════════════════════
    
    void runAStar(){
        int sr, sc;
        try{
            sr=Integer.parseInt(startRow.getText().trim());
            sc=Integer.parseInt(startCol.getText().trim());
        }catch(NumberFormatException ex){log("Bad Source coordinates.");return;}

        if(grid==null){log("No map loaded.");return;}
        if(targets.isEmpty()) { log("Please add at least 1 Destination."); return; }

        log("──────────────────────────────────────");
        log("Running A* GRID Scheduler...");
        status("Computing scheduled A* route…"); 
        runBtn.setEnabled(false); runAStarBtn.setEnabled(false);

        Pt source = new Pt(sc + 0.5, sr + 0.5);
        List<DeliveryTask> captureTasks = new ArrayList<>(targets);

        new SwingWorker<List<Pt>,Void>(){
            double totalDist = 0;

            protected List<Pt> doInBackground(){
                long t=System.currentTimeMillis();
                
                directLines.clear();
                aStarWaypoints.clear();
                aStarPath.clear();
                
                captureTasks.sort(Comparator.comparingDouble(tsk -> tsk.deadline));

                List<Pt> fullSequentialPath = new ArrayList<>();
                Pt currentStart = source;
                double currentTime = 0;

                for (int i = 0; i < captureTasks.size(); i++) {
                    DeliveryTask currentTask = captureTasks.get(i);
                    currentTask.execOrder = i + 1;
                    
                    // ═══════════════════════════════════════════════════════
                    // DESTINATION OBSTACLE CHECK FOR A* FAIRNESS
                    // We apply the exact same geometric snap so A* targets the valid outer edge
                    // ═══════════════════════════════════════════════════════
                    Pt adjustedDest = currentTask.pt;
                    List<Pt> targetHull = null;
                    for (List<Pt> hull : allHulls) {
                        if (pointStrictlyInPolygon(adjustedDest, hull)) { targetHull = hull; break; }
                    }
                    if (targetHull != null) {
                        Pt bestSidePt = null;
                        double bestT = Double.MAX_VALUE;
                        for (int j = 0; j < targetHull.size(); j++) {
                            Pt a = targetHull.get(j); Pt b = targetHull.get((j + 1) % targetHull.size());
                            Pt intPt = segmentIntersectionPoint(currentStart, adjustedDest, a, b);
                            if (intPt != null) {
                                double param = segmentParameterUnclamped(currentStart, adjustedDest, intPt);
                                if (param >= -EPS && param <= 1 + EPS && param < bestT) {
                                    bestT = param; bestSidePt = intPt;
                                }
                            }
                        }
                        if (bestSidePt == null) {
                            double closestDist = Double.MAX_VALUE;
                            for (int j = 0; j < targetHull.size(); j++) {
                                Pt a = targetHull.get(j); Pt b = targetHull.get((j + 1) % targetHull.size());
                                Pt proj = getClosestPointOnSegment(adjustedDest, a, b);
                                double d = distance(adjustedDest, proj);
                                if (d < closestDist) { closestDist = d; bestSidePt = proj; }
                            }
                        }
                        if (bestSidePt != null) {
                            double dirX = currentStart.x - bestSidePt.x; double dirY = currentStart.y - bestSidePt.y;
                            double len = Math.hypot(dirX, dirY);
                            if (len > EPS) adjustedDest = new Pt(bestSidePt.x + (dirX/len)*0.01, bestSidePt.y + (dirY/len)*0.01);
                            else adjustedDest = bestSidePt;
                            log("Warning: Target T" + currentTask.execOrder + " is inside an obstacle! Snapping A* target to edge: " + adjustedDest);
                        }
                    }
                    // ═══════════════════════════════════════════════════════
                    
                    directLines.add(new Pt[]{currentStart, adjustedDest});
                    
                    log("Task T" + currentTask.execOrder + ": A* Routing...");
                    
                    // A* Implementation aiming for the safe edge
                    List<Pt> gridPath = computeAStarPath(currentStart, adjustedDest);
                    aStarWaypoints.addAll(gridPath);

                    if (fullSequentialPath.isEmpty()) {
                        fullSequentialPath.addAll(gridPath);
                    } else {
                        fullSequentialPath.addAll(gridPath.subList(1, gridPath.size()));
                    }

                    double segmentDistance = totalLength(gridPath);
                    currentTime += segmentDistance;

                    currentTask.arrivalTime = currentTime;
                    currentTask.onTime = (currentTime <= currentTask.deadline);
                    currentTask.pt = adjustedDest; // Snaps visual marker

                    currentStart = adjustedDest;
                }

                totalDist = currentTime;
                lastMs = System.currentTimeMillis() - t;
                return fullSequentialPath;
            }

            protected void done(){
                try{
                    List<Pt> returnedPath = get();
                    aStarPath.addAll(returnedPath);
                    
                    executedTasks.clear();
                    executedTasks.addAll(captureTasks);
                    
                    mapPanel.repaint();

                    status("Done A* · " + captureTasks.size() + " scheduled goals · " + lastMs + "ms");
                    
                    log("");
                    log("=================================================");
                    log("         A* SCHEDULING RESULTS");
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
                runBtn.setEnabled(true); runAStarBtn.setEnabled(true);
            }
        }.execute();
    }
    
    // A* Helper classes and functions
    static class ANode implements Comparable<ANode> {
        int r, c; double g, f; ANode parent;
        public ANode(int r, int c, double g, double f, ANode parent) {
            this.r = r; this.c = c; this.g = g; this.f = f; this.parent = parent;
        }
        @Override
        public int compareTo(ANode o) {
            return Double.compare(this.f, o.f);
        }
    }

    static List<Pt> computeAStarPath(Pt startPt, Pt endPt) {
        int startR = r(startPt), startC = c(startPt);
        int endR = r(endPt), endC = c(endPt);

        PriorityQueue<ANode> openSet = new PriorityQueue<>();
        Map<Integer, Double> gScores = new HashMap<>();

        double startH = Math.hypot(startR - endR, startC - endC);
        openSet.add(new ANode(startR, startC, 0.0, startH, null));
        gScores.put(key(startR, startC), 0.0);

        ANode endNode = null;
        
        // 8 directions: N, NE, E, SE, S, SW, W, NW
        int[] dr = {-1, -1, 0, 1, 1, 1, 0, -1};
        int[] dc = {0, 1, 1, 1, 0, -1, -1, -1};
        double[] cost = {1.0, 1.41421356, 1.0, 1.41421356, 1.0, 1.41421356, 1.0, 1.41421356};

        while (!openSet.isEmpty()) {
            ANode current = openSet.poll();
            
            if (current.r == endR && current.c == endC) {
                endNode = current;
                break;
            }

            if (gScores.getOrDefault(key(current.r, current.c), Double.MAX_VALUE) < current.g) {
                continue; 
            }

            for (int i = 0; i < 8; i++) {
                int nr = current.r + dr[i];
                int nc = current.c + dc[i];

                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && grid[nr][nc] != '@') {
                    // Avoid cutting corners through blocked diagonal cells
                    if (dr[i] != 0 && dc[i] != 0) {
                        if (grid[current.r][nc] == '@' || grid[nr][current.c] == '@') {
                            continue;
                        }
                    }

                    double tentativeG = current.g + cost[i];
                    if (tentativeG < gScores.getOrDefault(key(nr, nc), Double.MAX_VALUE)) {
                        gScores.put(key(nr, nc), tentativeG);
                        double h = Math.hypot(nr - endR, nc - endC);
                        openSet.add(new ANode(nr, nc, tentativeG, tentativeG + h, current));
                    }
                }
            }
        }

        List<Pt> path = new ArrayList<>();
        if (endNode != null) {
            ANode curr = endNode;
            while (curr != null) {
                path.add(new Pt(curr.c + 0.5, curr.r + 0.5));
                curr = curr.parent;
            }
            Collections.reverse(path);
            
            // Snap to exact floating points requested for better visual integration
            path.set(0, startPt);
            path.set(path.size() - 1, endPt);
        } else {
            // Path not found, return direct line fallback so GUI handles it cleanly
            path.add(startPt);
            path.add(endPt);
        }
        return path;
    }

    // ════════════════════════════════════════════════════════
    // PYTHON 1:1 GEOMETRIC ALGORITHM (UPDATED MACRO-EVALUATION)
    // ════════════════════════════════════════════════════════
    
    static class IntersectionResult {
        Pt pt; double t; int edgeIdx;
        IntersectionResult(Pt pt, double t, int edgeIdx) { this.pt = pt; this.t = t; this.edgeIdx = edgeIdx; }
    }

    static class HullAndIntersections {
        List<Pt> hull; List<IntersectionResult> intersections;
        HullAndIntersections(List<Pt> h, List<IntersectionResult> i) { this.hull = h; this.intersections = i; }
    }

    static HullAndIntersections findFirstIntersectingHull(Pt current, Pt destination, List<List<Pt>> hulls) {
        List<Pt> bestHull = null;
        double bestT = Double.MAX_VALUE;
        List<IntersectionResult> bestIntersections = null;

        for (List<Pt> hull : hulls) {
            if (!segmentCrossesPolygon(current, destination, hull)) continue;

            List<IntersectionResult> intersections = new ArrayList<>();
            int n = hull.size();
            for (int i = 0; i < n; i++) {
                Pt a = hull.get(i);
                Pt b = hull.get((i + 1) % n);
                Pt point = segmentIntersectionPoint(current, destination, a, b);
                
                if (point != null) {
                    double t = segmentParameterUnclamped(current, destination, point);
                    if (t >= -EPS && t <= 1 + EPS) {
                        addIntersection(intersections, point, t, i);
                    }
                }
            }

            if (intersections.isEmpty()) continue;

            double entryT = Double.MAX_VALUE;
            for (IntersectionResult r : intersections) {
                entryT = Math.min(entryT, r.t);
            }

            if (entryT < bestT) {
                bestT = entryT;
                bestHull = hull;
                bestIntersections = intersections;
            }
        }
        return new HullAndIntersections(bestHull, bestIntersections);
    }
    
    static void addIntersection(List<IntersectionResult> intersections, Pt point, double t, int edgeIdx) {
        for (IntersectionResult ex : intersections) {
            if (samePoint(ex.pt, point)) return; 
        }
        intersections.add(new IntersectionResult(point, t, edgeIdx));
    }

    static class BoundItem {
        double pos; Pt point; boolean isVertex; int edgeIdx;
        BoundItem(double pos, Pt pt, boolean isVertex, int edgeIdx) {
            this.pos = pos; this.point = pt; this.isVertex = isVertex; this.edgeIdx = edgeIdx;
        }
    }

    static List<List<Pt>> shorterAndLongerPathVertices(List<Pt> polygon, List<IntersectionResult> intersections) {
        if (intersections.size() < 2) return new ArrayList<>();

        intersections.sort(Comparator.comparingDouble(a -> a.t));
        IntersectionResult startInter = intersections.get(0);
        IntersectionResult endInter = intersections.get(intersections.size() - 1);

        List<Double> edgeLengths = new ArrayList<>();
        List<Double> cum = new ArrayList<>();
        cum.add(0.0);
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            double len = distance(polygon.get(i), polygon.get((i + 1) % n));
            edgeLengths.add(len);
            cum.add(cum.get(cum.size() - 1) + len);
        }
        double perimeter = cum.get(cum.size() - 1);

        List<BoundItem> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(new BoundItem(cum.get(i), polygon.get(i), true, i));
        }

        for (IntersectionResult r : Arrays.asList(startInter, endInter)) {
            Pt edgeStart = polygon.get(r.edgeIdx);
            Pt edgeEnd = polygon.get((r.edgeIdx + 1) % n);
            
            boolean exists = false;
            for (BoundItem it : items) {
                if (samePoint(r.pt, it.point)) { exists = true; break; }
            }
            if (exists) continue;

            double t = segmentParameterUnclamped(edgeStart, edgeEnd, r.pt);
            t = Math.max(0.0, Math.min(1.0, t));
            double pos = cum.get(r.edgeIdx) + t * edgeLengths.get(r.edgeIdx);
            items.add(new BoundItem(pos, r.pt, false, r.edgeIdx));
        }

        items.sort(Comparator.comparingDouble(a -> a.pos));

        int startIdx = -1, endIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (samePoint(items.get(i).point, startInter.pt)) startIdx = i;
            if (samePoint(items.get(i).point, endInter.pt)) endIdx = i;
        }

        if (startIdx == -1 || endIdx == -1) return new ArrayList<>();

        double posA = items.get(startIdx).pos;
        double posB = items.get(endIdx).pos;
        double forwardDist = posB >= posA ? posB - posA : perimeter - (posA - posB);
        double backwardDist = perimeter - forwardDist;

        List<Pt> forwardVertices = new ArrayList<>();
        int idx = startIdx;
        while (true) {
            idx = (idx + 1) % items.size();
            if (idx == endIdx) break;
            if (items.get(idx).isVertex) forwardVertices.add(items.get(idx).point);
        }

        List<Pt> backwardVertices = new ArrayList<>();
        idx = startIdx;
        while (true) {
            idx = (idx - 1 + items.size()) % items.size();
            if (idx == endIdx) break;
            if (items.get(idx).isVertex) backwardVertices.add(items.get(idx).point);
        }

        List<List<Pt>> res = new ArrayList<>();
        if (forwardDist <= backwardDist) {
            res.add(forwardVertices);
            res.add(backwardVertices);
        } else {
            res.add(backwardVertices);
            res.add(forwardVertices);
        }

        if (res.get(0).isEmpty() && res.get(1).isEmpty()) {
            Set<Pt> fallback = new HashSet<>();
            fallback.add(polygon.get(startInter.edgeIdx));
            fallback.add(polygon.get((startInter.edgeIdx + 1) % n));
            fallback.add(polygon.get(endInter.edgeIdx));
            fallback.add(polygon.get((endInter.edgeIdx + 1) % n));
            res.set(0, new ArrayList<>(fallback));
        }
        return res;
    }

    static List<Pt> computeDetourPath(Pt source, Pt destination, List<List<Pt>> hulls) {
        List<Pt> path = new ArrayList<>();
        path.add(source);
        Pt current = source;
        Set<String> visited = new HashSet<>();
        visited.add(roundPoint(source).toString());

        int iter = 0;
        while (!samePoint(current, destination) && iter++ < 200) {
            if (segmentValidCached(current, destination, hulls)) {
                break;
            }

            HullAndIntersections hi = findFirstIntersectingHull(current, destination, hulls);
            if (hi == null || hi.hull == null || hi.intersections.isEmpty()) break;

            // Identify the exact entry and exit points on this specific obstacle
            hi.intersections.sort(Comparator.comparingDouble(a -> a.t));
            Pt startInter = hi.intersections.get(0).pt;
            Pt endInter = hi.intersections.get(hi.intersections.size() - 1).pt;

            List<List<Pt>> arcGroups = shorterAndLongerPathVertices(hi.hull, hi.intersections);
            if (arcGroups.isEmpty()) {
                arcGroups.add(new ArrayList<>());
            }

            List<Pt> bestMacroPath = null;
            double bestScore = Double.MAX_VALUE;

            // Macro-Evaluation: Test both full paths around the obstacle
            for (List<Pt> arc : arcGroups) {
                List<Pt> candidateArc = new ArrayList<>();
                candidateArc.add(current);
                candidateArc.add(startInter);
                candidateArc.addAll(arc);
                candidateArc.add(endInter);

                // Clean up any duplicate consecutive points
                List<Pt> cleanArc = new ArrayList<>();
                for (Pt p : candidateArc) {
                    if (cleanArc.isEmpty() || !samePoint(cleanArc.get(cleanArc.size()-1), p)) {
                        cleanArc.add(p);
                    }
                }

                // Preemptively smooth the arc. This bridges concave pockets automatically!
                List<Pt> smoothedArc = smoothPath(cleanArc, hulls);
                
                // Score based on actual traversal distance, not just a greedy straight line
                double cost = totalLength(smoothedArc) + distance(endInter, destination);

                if (cost < bestScore) {
                    bestScore = cost;
                    bestMacroPath = smoothedArc;
                }
            }

            if (bestMacroPath != null && bestMacroPath.size() > 1) {
                // Append the winning macro-path to our global route
                for (int i = 1; i < bestMacroPath.size(); i++) {
                    Pt pt = bestMacroPath.get(i);
                    String rounded = roundPoint(pt).toString();
                    if (!visited.contains(rounded)) {
                        path.add(pt);
                        visited.add(rounded);
                        current = pt;
                    }
                }
            } else {
                break; // Fallback to prevent infinite loops if math fails
            }
        }

        if (!samePoint(path.get(path.size() - 1), destination)) {
            path.add(destination);
        }
        return path;
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
                    smoothed.add(path.get(j));
                    i = j;
                    found = true;
                    break;
                }
                j--;
            }
            if (!found) {
                smoothed.add(path.get(i + 1));
                i++;
            }
        }
        return smoothed;
    }

    // ════════════════════════════════════════════════════════
    // ROBUST VECTOR GEOMETRY
    // ════════════════════════════════════════════════════════

    static Pt getClosestPointOnSegment(Pt p, Pt a, Pt b) {
        double l2 = Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2);
        if (l2 < EPS) return a;
        double t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2;
        t = Math.max(0.0, Math.min(1.0, t));
        return new Pt(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y));
    }

    static boolean segmentValid(Pt p1, Pt p2, List<List<Pt>> hulls) {
        for (List<Pt> hull : hulls) {
            if (segmentCrossesPolygon(p1, p2, hull)) return false;
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

    static boolean segmentCrossesPolygon(Pt p1, Pt p2, List<Pt> poly) {
        List<Double> ts = new ArrayList<>();
        ts.add(0.0);
        ts.add(1.0);
        
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Pt a = poly.get(i);
            Pt b = poly.get((i + 1) % n);
            
            double denom = (p1.x - p2.x) * (a.y - b.y) - (p1.y - p2.y) * (a.x - b.x);
            if (Math.abs(denom) > EPS) {
                double t = ((p1.x - a.x) * (a.y - b.y) - (p1.y - a.y) * (a.x - b.x)) / denom;
                double u = -((p1.x - p2.x) * (p1.y - a.y) - (p1.y - p2.y) * (p1.x - a.x)) / denom;
                if (t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS) {
                    ts.add(Math.max(0.0, Math.min(1.0, t)));
                }
            } else {
                if (Math.abs(cross(p1, p2, a)) <= EPS) {
                    double ta = segmentParameterUnclamped(p1, p2, a);
                    double tb = segmentParameterUnclamped(p1, p2, b);
                    if ((ta >= -EPS && ta <= 1 + EPS) || (tb >= -EPS && tb <= 1 + EPS) || (ta < 0 && tb > 1) || (tb < 0 && ta > 1)) {
                        ts.add(Math.max(0.0, Math.min(1.0, ta)));
                        ts.add(Math.max(0.0, Math.min(1.0, tb)));
                    }
                }
            }
        }
        
        Collections.sort(ts);
        
        for (int i = 0; i < ts.size() - 1; i++) {
            double t0 = ts.get(i);
            double t1 = ts.get(i + 1);
            if (t1 - t0 > 1e-4) {
                double midT = (t0 + t1) / 2.0;
                Pt midP = new Pt(p1.x + midT * (p2.x - p1.x), p1.y + midT * (p2.y - p1.y));
                if (pointStrictlyInPolygon(midP, poly)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean pointStrictlyInPolygon(Pt pt, List<Pt> poly) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Pt pi = poly.get(i);
            Pt pj = poly.get(j);
            
            if (pointOnSegment(pt, pi, pj)) return false;

            if (((pi.y > pt.y) != (pj.y > pt.y)) &&
                (pt.x < (pj.x - pi.x) * (pt.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    static boolean pointOnSegment(Pt pt, Pt a, Pt b) {
        if (Math.min(a.x, b.x) - EPS <= pt.x && pt.x <= Math.max(a.x, b.x) + EPS &&
            Math.min(a.y, b.y) - EPS <= pt.y && pt.y <= Math.max(a.y, b.y) + EPS) {
            return Math.abs(cross(a, b, pt)) <= EPS;
        }
        return false;
    }

    static double segmentParameterUnclamped(Pt a, Pt b, Pt pt) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (Math.abs(dx) >= Math.abs(dy)) return Math.abs(dx) < EPS ? 0.0 : (pt.x - a.x) / dx;
        return Math.abs(dy) < EPS ? 0.0 : (pt.y - a.y) / dy;
    }
    
    static Pt segmentIntersectionPoint(Pt p1, Pt p2, Pt q1, Pt q2) {
        double denom = (p1.x - p2.x) * (q1.y - q2.y) - (p1.y - p2.y) * (q1.x - q2.x);
        if (Math.abs(denom) > EPS) {
            double t = ((p1.x - q1.x)*(q1.y - q2.y) - (p1.y - q1.y)*(q1.x - q2.x)) / denom;
            double u = -((p1.x - p2.x)*(p1.y - q1.y) - (p1.y - p2.y)*(p1.x - q1.x)) / denom;
            if (t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS) {
                return new Pt(p1.x + t*(p2.x - p1.x), p1.y + t*(p2.y - p1.y));
            }
        } else {
            if (Math.abs(cross(p1, p2, q1)) <= EPS) {
                double t1 = segmentParameterUnclamped(p1, p2, q1);
                double t2 = segmentParameterUnclamped(p1, p2, q2);
                double tStart = Math.min(t1, t2);
                double tEnd = Math.max(t1, t2);
                if (tEnd >= -EPS && tStart <= 1 + EPS) {
                    double tInt = Math.max(0.0, Math.min(1.0, tStart));
                    return new Pt(p1.x + tInt*(p2.x - p1.x), p1.y + tInt*(p2.y - p1.y));
                }
            }
        }
        return null;
    }

    static Pt lineIntersect(Pt p1, Pt p2, Pt p3, Pt p4) {
        double denom = (p1.x - p2.x)*(p3.y - p4.y) - (p1.y - p2.y)*(p3.x - p4.x);
        if (Math.abs(denom) < EPS) return null;
        double px = ((p1.x*p2.y - p1.y*p2.x)*(p3.x - p4.x) - (p1.x - p2.x)*(p3.x*p4.y - p3.y*p4.x)) / denom;
        double py = ((p1.x*p2.y - p1.y*p2.x)*(p3.y - p4.y) - (p1.y - p2.y)*(p3.x*p4.y - p3.y*p4.x)) / denom;
        return new Pt(px, py);
    }

    static boolean samePoint(Pt a, Pt b) { return Math.abs(a.x - b.x) <= 1e-6 && Math.abs(a.y - b.y) <= 1e-6; }

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
    static double totalLength(List<Pt> path) {
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += distance(path.get(i), path.get(i + 1));
        }
        return total;
    }

    // ════════════════════════════════════════════════════════
    // UI HELPERS
    // ════════════════════════════════════════════════════════

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