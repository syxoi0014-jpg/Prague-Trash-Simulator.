import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

public class PragueTrashSim extends JPanel implements ActionListener, KeyListener {
    
    // ==========================================
    // КОНСТАНТЫ И ОПЦИИ ИГРЫ
    // ==========================================
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int WORLD_SIZE = 4500;
    private static final int BLOCK_SIZE = 400;
    private static final int STREET_WIDTH = 300;
    private static final int GRID_STEP = BLOCK_SIZE + STREET_WIDTH;

    // ==========================================
    // ФИЗИКА ИГРОКА (Мусоровоз)
    // ==========================================
    double truckX, truckY, angle, speed = 0;
    private final double maxSpeed = 7.0;
    private final double acceleration = 0.2;
    double friction = 0.1; 
    private final double turnSpeed = 0.05;

    boolean up, down, left, right, space;
    boolean isHonking = false; 
    int reverseBeepTimer = 0;

    // ==========================================
    // МЕНЕДЖЕРЫ РЕСУРСОВ И СПИСКИ
    // ==========================================
    SoundManager soundManager = new SoundManager();
    ImageManager imageManager = new ImageManager();

    List<Building> buildings = new ArrayList<>();
    List<Bin> bins = new ArrayList<>();
    List<Tree> trees = new ArrayList<>(); 
    List<NPCVehicle> traffic = new ArrayList<>(); 
    List<Pedestrian> pedestrians = new ArrayList<>(); 
    List<Cyclist> cyclists = new ArrayList<>(); 
    List<ParkedCar> parkedCars = new ArrayList<>(); 
    List<TrafficLight> semaphores = new ArrayList<>(); 
    List<StreetLight> streetLights = new ArrayList<>(); 
    
    TrashRival rival; 
    TrashWorker playerWorker = null;
    
    // МАССИВ ДЛЯ СКОРЫХ ПОМОЩЕЙ (чтобы забирать всех сбитых)
    List<AmbulanceCar> ambulances = new ArrayList<>();

    List<SmokeParticle> particles = new ArrayList<>();
    List<TireTrack> tracks = new ArrayList<>();
    List<Color> truckContents = new ArrayList<>();
    List<TrashPile> dumpPiles = new ArrayList<>();
    
    BufferedImage nightLayer = null, lightSprite = null, headlightSprite = null;

    PoliceCar police = null;
    int policeCooldown = 0, intersectionCooldown = 0; 
    String notification = "";
    int notifTimer = 0;
    boolean isRaining = false;
    int weatherTimer = 0;

    ZizkovTower tower; 
    Rectangle dumpZone, gasZone;  

    int score = 0, money = 100, timeLeft = 180, frameCounter = 0, trashCount = 0;
    private final int maxTrash = 10;
    int totalBinsAtStart = 0;
    double fuel = 100.0, dayCycle = 0;
    String gameOverReason = ""; 
    boolean isGameOver = false, inMenu = true; 
    int majakFlashFrame = 0;
    Timer timer;

    public PragueTrashSim() {
        soundManager.load("truck_engine.wav", "engine");
        soundManager.load("truck_horn.wav", "truck_horn");
        soundManager.load("car_horn.wav", "car_horn");
        soundManager.load("scream.wav", "scream");
        soundManager.load("tire_screech.wav", "screech");
        soundManager.load("collect.wav", "collect"); 
        soundManager.load("dump.wav", "dump");
        soundManager.load("gas.wav", "gas");
        soundManager.load("win.wav", "win");
        soundManager.load("rain.wav", "rain");
        soundManager.load("siren.wav", "siren");
        soundManager.load("ambulance_siren.wav", "ambulance_siren"); 
        soundManager.load("crash.wav", "crash"); 
        soundManager.load("reverse_beep.wav", "reverse");

        imageManager.load("truck.png", "truck");
        imageManager.load("rival.png", "rival");
        imageManager.load("police.png", "police");
        imageManager.load("ambulance.png", "ambulance"); 
        imageManager.load("car.png", "car");
        imageManager.load("bike.png", "bike"); 
        imageManager.load("scooter.png", "scooter"); 
        imageManager.load("tree.png", "tree");
        imageManager.load("building.png", "building");
        imageManager.load("bin.png", "bin");
        imageManager.load("menu_bg.jpg", "menu_bg");

        initGame(); 
        setFocusable(true);
        addKeyListener(this);
        timer = new Timer(16, this);
        timer.start();
    }

    private void initGame() {
        buildings.clear(); bins.clear(); trees.clear(); traffic.clear();
        pedestrians.clear(); cyclists.clear(); parkedCars.clear(); semaphores.clear();
        streetLights.clear(); particles.clear(); tracks.clear();
        truckContents.clear(); dumpPiles.clear(); ambulances.clear();

        truckX = 150; truckY = 150; angle = 0; speed = 0;
        score = 0; money = 100; timeLeft = 180; frameCounter = 0; trashCount = 0;
        fuel = 100.0; dayCycle = 0; playerWorker = null;
        police = null; policeCooldown = 0; intersectionCooldown = 0;
        notification = ""; notifTimer = 0; isRaining = false; weatherTimer = 0; friction = 0.1;
        gameOverReason = ""; isGameOver = false; isHonking = false;

        soundManager.stopAll();
        soundManager.loop("engine");

        Random rand = new Random();
        Color[] binColors = {Color.GREEN, Color.BLUE, Color.YELLOW, Color.DARK_GRAY};
        
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int x = col * GRID_STEP;
                int y = row * GRID_STEP;
                
                if (row == 0 && col == 0) {
                    dumpZone = new Rectangle(x, y, BLOCK_SIZE, BLOCK_SIZE); 
                } else if (row == 0 && col == 3) { 
                    gasZone = new Rectangle(x, y, BLOCK_SIZE, BLOCK_SIZE); 
                } else if (row == 1 && col == 1) { 
                    tower = new ZizkovTower(x + BLOCK_SIZE / 2, y + BLOCK_SIZE / 2);
                } else {
                    buildings.add(new Building(x, y, BLOCK_SIZE, BLOCK_SIZE));
                    trees.add(new Tree(x + 80, y + 80)); trees.add(new Tree(x + 280, y + 280)); trees.add(new Tree(x + 80, y + 280));
                }

                if (row < 3 && col < 3) {
                    semaphores.add(new TrafficLight(x + BLOCK_SIZE, y + BLOCK_SIZE, STREET_WIDTH));
                }

                if (row < 3 && (row != 0 || col != 0) && (row != 0 || col != 3)) {
                    parkedCars.add(new ParkedCar(x + 60, y + BLOCK_SIZE + 35, true));
                    parkedCars.add(new ParkedCar(x + 240, y + BLOCK_SIZE + 35, true));
                    int[] bx = {100, 140, 175, 210, 260, 300, 360, 395};
                    for (int pos : bx) {
                        boolean empty = rand.nextInt(100) < 30;
                        bins.add(new Bin(x + pos, y + BLOCK_SIZE + 5, binColors[rand.nextInt(binColors.length)], empty));
                    }
                    pedestrians.add(new Pedestrian(x + 50, y + BLOCK_SIZE + 15, true));
                    pedestrians.add(new Pedestrian(x + 150, y + BLOCK_SIZE + STREET_WIDTH - 15, true));
                    cyclists.add(new Cyclist(x + 50, y + BLOCK_SIZE + 40, true, rand.nextBoolean()));
                    cyclists.add(new Cyclist(x + 150, y + BLOCK_SIZE + STREET_WIDTH - 40, true, rand.nextBoolean()));
                    
                    streetLights.add(new StreetLight(x + 100, y + BLOCK_SIZE + 15));
                    streetLights.add(new StreetLight(x + 200, y + BLOCK_SIZE + 15));
                    streetLights.add(new StreetLight(x + 300, y + BLOCK_SIZE + 15));
                    streetLights.add(new StreetLight(x + 100, y + BLOCK_SIZE + STREET_WIDTH - 15));
                    streetLights.add(new StreetLight(x + 200, y + BLOCK_SIZE + STREET_WIDTH - 15));
                    streetLights.add(new StreetLight(x + 300, y + BLOCK_SIZE + STREET_WIDTH - 15));
                }
                
                if (col < 3 && (row != 0 || col != 0) && (row != 0 || col != 3)) {
                    parkedCars.add(new ParkedCar(x + BLOCK_SIZE + 35, y + 60, false));
                    parkedCars.add(new ParkedCar(x + BLOCK_SIZE + 35, y + 240, false));
                    int[] by = {100, 140, 175, 210, 260, 300, 360, 395};
                    for (int pos : by) {
                        boolean empty = rand.nextInt(100) < 30;
                        bins.add(new Bin(x + BLOCK_SIZE + 5, y + pos, binColors[rand.nextInt(binColors.length)], empty));
                    }
                    pedestrians.add(new Pedestrian(x + BLOCK_SIZE + 15, y + 50, false));
                    pedestrians.add(new Pedestrian(x + BLOCK_SIZE + STREET_WIDTH - 15, y + 150, false));
                    cyclists.add(new Cyclist(x + BLOCK_SIZE + 40, y + 50, false, rand.nextBoolean()));
                    cyclists.add(new Cyclist(x + BLOCK_SIZE + STREET_WIDTH - 40, y + 150, false, rand.nextBoolean()));
                    
                    streetLights.add(new StreetLight(x + BLOCK_SIZE + 15, y + 100));
                    streetLights.add(new StreetLight(x + BLOCK_SIZE + 15, y + 200));
                    streetLights.add(new StreetLight(x + BLOCK_SIZE + 15, y + 300));
                    streetLights.add(new StreetLight(x + BLOCK_SIZE + STREET_WIDTH - 15, y + 100));
                    streetLights.add(new StreetLight(x + BLOCK_SIZE + STREET_WIDTH - 15, y + 200));
                    streetLights.add(new StreetLight(x + BLOCK_SIZE + STREET_WIDTH - 15, y + 300));
                }
            }
        }
        totalBinsAtStart = bins.size();
        
        Color[] carColors = {Color.RED, Color.CYAN, Color.LIGHT_GRAY, Color.BLUE, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.MAGENTA, Color.DARK_GRAY};
        int[] hLanes = {515, 585, 1215, 1285, 1915, 1985, 2615, 2685};
        int[] vLanes = {515, 585, 1215, 1285, 1915, 1985, 2615, 2685};
        
        for (int y : hLanes) {
            int dir = (y == 585 || y == 1285 || y == 1985 || y == 2685) ? 1 : -1;
            for (int i = 0; i < 3; i++) { 
                int startX = rand.nextInt(4000);
                Rectangle spawnArea = new Rectangle(startX - 50, y - 15, 150, 60); 
                boolean tooClose = false;
                for(NPCVehicle other : traffic) {
                    if(spawnArea.intersects(other.getBounds())) { tooClose = true; break; }
                }
                if(!tooClose) traffic.add(new NPCVehicle(startX, y, dir * (3 + rand.nextInt(3)), true, carColors[rand.nextInt(carColors.length)]));
            }
        }
        
        for (int x : vLanes) {
            int dir = (x == 585 || x == 1285 || x == 1985 || x == 2685) ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                int startY = rand.nextInt(4000);
                Rectangle spawnArea = new Rectangle(x - 15, startY - 50, 60, 150);
                boolean tooClose = false;
                for(NPCVehicle other : traffic) {
                    if(spawnArea.intersects(other.getBounds())) { tooClose = true; break; }
                }
                if(!tooClose) traffic.add(new NPCVehicle(x, startY, dir * (3 + rand.nextInt(3)), false, carColors[rand.nextInt(carColors.length)]));
            }
        }

        rival = new TrashRival(150, 250);
        rival.angle = 0; 
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (inMenu) {
            drawMenu(g2d, "POPELÁŘ SIMULATOR PRO", "Vítejte na Žižkově!", "Stiskněte [ENTER] pro start hry");
            return;
        }

        int cameraX = (int) ((getWidth() / 2) - truckX);
        int cameraY = (int) ((getHeight() / 2) - truckY);

        g2d.setColor(new Color(60, 60, 65));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        drawRoadMarkingsAndSidewalks(g2d, cameraX, cameraY);
        for (TireTrack t : tracks) { t.draw(g2d, cameraX, cameraY); }

        if (dumpZone != null) drawDumpZone(g2d, cameraX, cameraY); 
        if (gasZone != null) drawGasZone(g2d, cameraX, cameraY); 

        for (Building b : buildings) b.draw(g2d, cameraX, cameraY, imageManager); 
        if (tower != null) tower.draw(g2d, cameraX, cameraY); 
        for (TrafficLight tl : semaphores) tl.draw(g2d, cameraX, cameraY); 
        for (StreetLight sl : streetLights) sl.drawPole(g2d, cameraX, cameraY); 
        for (ParkedCar pc : parkedCars) pc.draw(g2d, cameraX, cameraY, imageManager); 
        for (Bin bin : bins) bin.draw(g2d, cameraX, cameraY, imageManager); 
        for (Tree t : trees) t.draw(g2d, cameraX, cameraY, imageManager); 
        for (Pedestrian p : pedestrians) p.draw(g2d, cameraX, cameraY); 
        for (Cyclist c : cyclists) c.draw(g2d, cameraX, cameraY, imageManager); 

        float darkness = (float) (Math.sin(dayCycle) * 0.5 + 0.5);

        for (NPCVehicle v : traffic) v.draw(g2d, cameraX, cameraY, imageManager); 

        if (rival != null) rival.draw(g2d, cameraX, cameraY, imageManager); 
        if (police != null) police.draw(g2d, cameraX, cameraY, imageManager); 
        
        // Отрисовка всех скорых
        for (AmbulanceCar amb : ambulances) {
            amb.draw(g2d, cameraX, cameraY, imageManager);
        }

        drawTopDownTruck(g2d);
        
        if (playerWorker != null) playerWorker.draw(g2d, cameraX, cameraY); 
        if (rival != null && rival.worker != null) rival.worker.draw(g2d, cameraX, cameraY); 

        drawWeatherAndDay(g2d, darkness, cameraX, cameraY);

        if (darkness > 0.4 && !isGameOver) {
            for (NPCVehicle v : traffic) v.drawHeadlights(g2d, cameraX, cameraY); 
            if (rival != null) rival.drawHeadlights(g2d, cameraX, cameraY); 
        }

        for (SmokeParticle p : particles) p.draw(g2d, cameraX, cameraY); 

        if (!isGameOver) {
            drawUI(g2d);
            drawMiniMap(g2d);
            if (notifTimer > 0) {
                g2d.setColor(Color.RED); g2d.setFont(new Font("SansSerif", Font.BOLD, 45));
                int textW = g2d.getFontMetrics().stringWidth(notification);
                g2d.drawString(notification, getWidth() / 2 - textW / 2, 100);
            }
        }
        
        if (isGameOver) {
            // Теперь победа засчитывается ТОЛЬКО если твоих очков больше или равно очкам конкурента
            boolean isWin = score >= rival.score && !gameOverReason.contains("porazil") && !gameOverReason.contains("konkurent");
            drawMenu(g2d, isWin ? "VÍTĚZSTVÍ!" : "KONEC HRY", gameOverReason + " | Skóre: " + score, "Stiskněte [ENTER] pro restart");
        }
    }

    private void drawDumpZone(Graphics2D g2d, int cx, int cy) {
        int dx = dumpZone.x + cx; int dy = dumpZone.y + cy; int dw = dumpZone.width; int dh = dumpZone.height;
        g2d.setColor(new Color(90, 70, 50)); g2d.fillRect(dx, dy, dw, dh); 
        g2d.setStroke(new BasicStroke(4)); g2d.setColor(new Color(100, 100, 100)); g2d.drawRect(dx, dy, dw, dh);
        for (int i = 0; i < dw; i += 40) {
            g2d.setColor(Color.YELLOW); g2d.fillRect(dx + i, dy + dh - 20, 20, 20);
            g2d.setColor(Color.BLACK); g2d.fillRect(dx + i + 20, dy + dh - 20, 20, 20);
        }
        g2d.setColor(new Color(60, 50, 40)); g2d.fillOval(dx + 50, dy + 50, 150, 100);
        g2d.setColor(new Color(70, 60, 50)); g2d.fillOval(dx + 150, dy + 100, 200, 120);
        g2d.setColor(new Color(50, 60, 40)); g2d.fillOval(dx + 80, dy + 200, 140, 140);
        g2d.setColor(Color.LIGHT_GRAY); g2d.fillRect(dx + 20, dy + dh - 100, 80, 60);
        g2d.setColor(Color.DARK_GRAY); g2d.fillRect(dx + 30, dy + dh - 90, 60, 40);
        g2d.setColor(Color.GREEN); g2d.fillRect(dx + 40, dy + dh - 80, 40, 20); 
        for (TrashPile pile : dumpPiles) { pile.draw(g2d, cx, cy); }
        g2d.setColor(new Color(200, 50, 50)); g2d.fillRoundRect(dx + 100, dy + 10, 200, 60, 10, 10);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("SansSerif", Font.BOLD, 36));
        g2d.drawString("SKLÁDKA", dx + 115, dy + 52);
        g2d.setStroke(new BasicStroke(1));
    }

    private void drawGasZone(Graphics2D g2d, int cx, int cy) {
        int gx = gasZone.x + cx; int gy = gasZone.y + cy;
        g2d.setColor(new Color(40, 40, 45)); g2d.fillRect(gx, gy, gasZone.width, gasZone.height);
        g2d.setColor(new Color(200, 200, 200)); g2d.fillRoundRect(gx + 200, gy + 20, 180, 100, 10, 10);
        g2d.setColor(new Color(100, 200, 255, 180)); g2d.fillRect(gx + 210, gy + 100, 160, 20); 
        g2d.setColor(new Color(20, 20, 20)); g2d.fillRoundRect(gx + 20, gy + 150, 360, 220, 20, 20); 
        g2d.setColor(new Color(220, 50, 50)); g2d.fillRoundRect(gx + 10, gy + 140, 360, 220, 20, 20); 
        g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(4)); g2d.drawRoundRect(gx + 10, gy + 140, 360, 220, 20, 20); g2d.setStroke(new BasicStroke(1));
        for(int i = 0; i < 3; i++) {
            g2d.setColor(Color.DARK_GRAY); g2d.fillRect(gx + 70 + i * 110, gy + 220, 30, 40); 
            g2d.setColor(Color.LIGHT_GRAY); g2d.fillRect(gx + 75 + i * 110, gy + 200, 20, 20); 
            g2d.setColor(Color.GREEN); g2d.fillRect(gx + 78 + i * 110, gy + 205, 14, 10); 
        }
        g2d.setColor(Color.DARK_GRAY); g2d.fillRect(gx + 20, gy + 20, 60, 100);
        g2d.setColor(Color.RED); g2d.setFont(new Font("Monospaced", Font.BOLD, 18)); g2d.drawString("95", gx + 25, gy + 50);
        g2d.setColor(Color.GREEN); g2d.drawString("39.9", gx + 25, gy + 75);
        g2d.setColor(Color.ORANGE); g2d.drawString("D:38", gx + 25, gy + 100);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("SansSerif", Font.BOLD, 46));
        g2d.drawString("ŽIŽKOV GAS", gx + 40, gy + 270);
    }

    private void drawRoadMarkingsAndSidewalks(Graphics2D g2d, int cx, int cy) {
        int blockSize = 400; int streetWidth = 300; int step = GRID_STEP;
        for (int i = -1; i < 6; i++) {
            for (int j = -1; j < 6; j++) {
                int startX = j * step; int startY = i * step;
                g2d.setColor(new Color(110, 110, 110)); 
                g2d.fillRect(startX + cx, startY + blockSize + cy, blockSize, 30);
                g2d.fillRect(startX + cx, startY + blockSize + streetWidth - 30 + cy, blockSize, 30);
                g2d.setColor(new Color(150, 50, 50, 150)); 
                g2d.fillRect(startX + cx, startY + blockSize + 30 + cy, blockSize, 20);
                g2d.fillRect(startX + cx, startY + blockSize + streetWidth - 50 + cy, blockSize, 20);
                g2d.setColor(new Color(110, 110, 110)); 
                g2d.fillRect(startX + blockSize + cx, startY + cy, 30, blockSize);
                g2d.fillRect(startX + blockSize + streetWidth - 30 + cx, startY + cy, 30, blockSize);
                g2d.setColor(new Color(150, 50, 50, 150)); 
                g2d.fillRect(startX + blockSize + 30 + cx, startY + cy, 20, blockSize);
                g2d.fillRect(startX + blockSize + streetWidth - 50 + cx, startY + cy, 20, blockSize);
            }
        }
        g2d.setColor(new Color(255, 255, 255, 90)); g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{20}, 0));
        for (int i = -1; i < 6; i++) {
            g2d.drawLine(-2000 + cx, blockSize + 150 + i * step + cy, 5000 + cx, blockSize + 150 + i * step + cy);
            g2d.drawLine(blockSize + 150 + i * step + cx, -2000 + cy, blockSize + 150 + i * step + cx, 5000 + cy);
        }
        g2d.setStroke(new BasicStroke(1));
        g2d.setColor(new Color(220, 220, 220, 180));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int startX = col * step + cx; int startY = row * step + cy;
                if (row < 3 && col < 3) {
                    int crossX = startX + blockSize; int crossY = startY + blockSize;
                    for (int i = 0; i < 10; i++) {
                        g2d.fillRect(crossX + 15 + (i * 27), crossY + 5, 15, 40);
                        g2d.fillRect(crossX + 15 + (i * 27), crossY + streetWidth - 45, 15, 40);
                        g2d.fillRect(crossX + 5, crossY + 15 + (i * 27), 40, 15);
                        g2d.fillRect(crossX + streetWidth - 45, crossY + 15 + (i * 27), 40, 15);
                    }
                }
                if (col < 3) {
                    int midCrossX = startX + blockSize / 2 - 20; int midCrossY = startY + blockSize;
                    for (int i = 0; i < 10; i++) g2d.fillRect(midCrossX, midCrossY + 15 + (i * 27), 40, 15);
                }
                if (row < 3) {
                    int midCrossX = startX + blockSize; int midCrossY = startY + blockSize / 2 - 20;
                    for (int i = 0; i < 10; i++) g2d.fillRect(midCrossX + 15 + (i * 27), midCrossY, 15, 40);
                }
            }
        }
    }

    private void drawTopDownTruck(Graphics2D g2d) {
        int cx = getWidth() / 2; int cy = getHeight() / 2; int tw = 110; int th = 46;
        AffineTransform old = g2d.getTransform(); g2d.translate(cx, cy); g2d.rotate(angle);
        if (!imageManager.drawSmart(g2d, "truck", -tw / 2, -th / 2, tw, th)) {
            g2d.setColor(new Color(0, 0, 0, 80)); g2d.fillRect(-tw / 2 + 10, -th / 2 + 10, tw, th);
            g2d.setColor(new Color(40, 150, 60)); g2d.fillRoundRect(-tw / 2, -th / 2, tw - 25, th, 10, 10);
            g2d.setColor(new Color(20, 80, 30)); g2d.drawRoundRect(-tw / 2, -th / 2, tw - 25, th, 10, 10);
            g2d.setColor(new Color(30, 120, 45)); for (int i = 0; i < 4; i++) g2d.fillRect(-tw / 2 + 10 + (i * 15), -th / 2, 5, th);
            g2d.setColor(new Color(40, 40, 40)); g2d.fillRect(-tw / 2 + 5, -th / 2 + 7, 25, th - 14);
            g2d.setColor(new Color(240, 240, 240)); g2d.fillRoundRect(tw / 2 - 25, -th / 2 + 3, 25, th - 6, 8, 8);
            g2d.setColor(new Color(100, 180, 220)); g2d.fillRect(tw / 2 - 12, -th / 2 + 5, 8, th - 10);
            g2d.setColor(new Color(255, 140, 0)); g2d.fillRect(-tw / 2 + 40, th / 2, 12, 10); g2d.fillRect(-tw / 2 + 35, th / 2 + 10, 22, 6);
            g2d.setColor(new Color(20, 20, 20)); int wheelW = 20; int wheelH = 8;
            g2d.fillRoundRect(-tw / 2 + 5, -th / 2 - 3, wheelW, wheelH, 4, 4); g2d.fillRoundRect(-tw / 2 + 5, th / 2 - 5, wheelW, wheelH, 4, 4);
            g2d.fillRoundRect(-tw / 6, -th / 2 - 3, wheelW, wheelH, 4, 4); g2d.fillRoundRect(-tw / 6, th / 2 - 5, wheelW, wheelH, 4, 4);
            g2d.fillRoundRect(tw / 2 - 30, -th / 2 - 3, wheelW, wheelH, 4, 4); g2d.fillRoundRect(tw / 2 - 30, th / 2 - 5, wheelW, wheelH, 4, 4);
        }
        majakFlashFrame++; boolean isOn = (majakFlashFrame % 60) < 30;
        if (isOn) {
            g2d.setColor(new Color(255, 140, 0)); g2d.fillOval(tw / 2 - 32, -10, 14, 20);
            g2d.setColor(Color.WHITE); g2d.fillRect(tw / 2 - 27, -3, 4, 6);
        } else {
            g2d.setColor(new Color(180, 100, 0)); g2d.fillOval(tw / 2 - 32, -10, 14, 20);
        }
        if (isHonking) {
            g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(3));
            g2d.drawArc(tw / 2, -th / 2 - 10, 40, th + 20, -45, 90);
            g2d.drawArc(tw / 2 + 15, -th / 2 - 25, 70, th + 50, -45, 90);
            g2d.setStroke(new BasicStroke(1));
        }
        g2d.setTransform(old);
    }

    private void drawUI(Graphics2D g2d) {
        g2d.setColor(new Color(30, 30, 30, 220)); g2d.fillRoundRect(20, 20, 300, 195, 15, 15);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
        g2d.setColor(new Color(255, 215, 0)); g2d.drawString("SKÓRE: " + score, 40, 55);
        g2d.setColor(new Color(50, 205, 50)); g2d.drawString("PENÍZE: " + money + " CZK", 40, 90);
        g2d.setColor(timeLeft <= 30 ? Color.RED : Color.WHITE); g2d.drawString("ČAS: " + timeLeft + "s", 40, 125);
        g2d.setColor(trashCount == maxTrash ? Color.RED : Color.ORANGE); g2d.drawString("KAPACITA: " + trashCount + "/" + maxTrash, 40, 160);
        g2d.setColor(fuel < 20 ? Color.RED : Color.CYAN); g2d.drawString("PALIVO: " + (int)fuel + "%", 40, 195);
        
        if (rival != null) {
            g2d.setColor(new Color(60, 20, 20, 220)); g2d.fillRoundRect(20, 230, 300, 130, 15, 15);
            g2d.setColor(new Color(255, 100, 100)); g2d.drawString("KONKURENT", 40, 265);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g2d.setColor(Color.WHITE); g2d.drawString("Skóre: " + rival.score, 40, 295);
            g2d.setColor(rival.trashCount == maxTrash ? Color.RED : Color.ORANGE); g2d.drawString("Kapacita: " + rival.trashCount + "/" + maxTrash, 40, 320);
            g2d.setColor(rival.fuel <= 0 ? Color.RED : (rival.fuel < 35 ? Color.ORANGE : Color.CYAN)); g2d.drawString("Palivo: " + (int)rival.fuel + "%", 40, 345);
        }
        
        g2d.setColor(new Color(30, 30, 30, 220)); g2d.fillRoundRect(getWidth() - 320, 20, 300, 150, 15, 15);
        g2d.setColor(Color.ORANGE); g2d.setFont(new Font("SansSerif", Font.BOLD, 26)); g2d.drawString("ÚKOLY (OBJECTIVES):", getWidth() - 300, 55);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2d.setColor(bins.isEmpty() ? Color.GREEN : Color.WHITE); g2d.drawString((bins.isEmpty() ? "[X] " : "[ ] ") + "Posbírat všechen odpad", getWidth() - 300, 95);
        g2d.setColor(money >= 500 ? Color.GREEN : Color.WHITE); g2d.drawString((money >= 500 ? "[X] " : "[ ] ") + "Vydělat 500 CZK", getWidth() - 300, 135);
    }

    private void drawMiniMap(Graphics2D g2d) {
        int mapSize = 220; int padding = 20; int mapX = padding; int mapY = getHeight() - mapSize - padding;
        
        // Светлая мини-карта
        g2d.setColor(new Color(240, 240, 240, 230)); 
        g2d.fillRoundRect(mapX, mapY, mapSize, mapSize, 15, 15);
        g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(2)); 
        g2d.drawRoundRect(mapX, mapY, mapSize, mapSize, 15, 15); 
        g2d.setStroke(new BasicStroke(1));
        
        double scale = (double)mapSize / 4500.0; 
        int centerX = mapX + mapSize / 2; 
        int centerY = mapY + mapSize / 2;

        // Отметка свалки [S]
        if (dumpZone != null) {
            int dx = centerX + (int)((dumpZone.x + 200 - truckX) * scale); 
            int dy = centerY + (int)((dumpZone.y + 200 - truckY) * scale);
            if (dx > mapX && dx < mapX+mapSize && dy > mapY && dy < mapY+mapSize) { 
                g2d.setColor(new Color(139,69,19)); g2d.fillRect(dx-7, dy-7, 14, 14); 
                g2d.setColor(Color.WHITE); g2d.setFont(new Font("SansSerif", Font.BOLD, 12)); 
                g2d.drawString("S", dx-4, dy+5); 
            }
        }
        
        // Отметка заправки [G]
        if (gasZone != null) {
            int gx = centerX + (int)((gasZone.x + 200 - truckX) * scale); 
            int gy = centerY + (int)((gasZone.y + 200 - truckY) * scale);
            if (gx > mapX && gx < mapX+mapSize && gy > mapY && gy < mapY+mapSize) { 
                g2d.setColor(Color.RED); g2d.fillRect(gx-7, gy-7, 14, 14); 
                g2d.setColor(Color.WHITE); g2d.setFont(new Font("SansSerif", Font.BOLD, 12)); 
                g2d.drawString("G", gx-5, gy+5); 
            }
        }

        // Баки (темно-серые, чтобы выделялись на светлом фоне)
        g2d.setColor(new Color(60, 60, 60)); 
        for (Bin b : bins) {
            int bx = centerX + (int)((b.x - truckX) * scale); 
            int by = centerY + (int)((b.y - truckY) * scale); 
            if (bx > mapX && bx < mapX + mapSize && by > mapY && by < mapY + mapSize) {
                g2d.fillRect(bx, by, 3, 3); 
            } 
        }
        
        // Конкурент
        if (rival != null) {
            g2d.setColor(rival.fuel <= 0 ? Color.GRAY : new Color(150, 0, 255)); 
            int rx = centerX + (int)((rival.x - truckX) * scale); 
            int ry = centerY + (int)((rival.y - truckY) * scale); 
            if (rx > mapX && rx < mapX + mapSize && ry > mapY && ry < mapY + mapSize) {
                g2d.fillRect(rx - 2, ry - 2, 5, 5); 
            } 
        }
        
        // Полиция
        if (police != null) {
            g2d.setColor(Color.BLUE); 
            int px = centerX + (int)((police.x - truckX) * scale); 
            int py = centerY + (int)((police.y - truckY) * scale); 
            if (px > mapX && px < mapX + mapSize && py > mapY && py < mapY + mapSize) {
                g2d.fillOval(px - 3, py - 3, 6, 6); 
            } 
        }
        
        // Все скорые помощи
        g2d.setColor(Color.PINK); 
        for (AmbulanceCar amb : ambulances) {
            int px = centerX + (int)((amb.x - truckX) * scale); 
            int py = centerY + (int)((amb.y - truckY) * scale);
            if (px > mapX && px < mapX + mapSize && py > mapY && py < mapY + mapSize) {
                g2d.fillOval(px - 3, py - 3, 6, 6); 
            }
        }
        
        // Игрок
        g2d.setColor(Color.RED); 
        g2d.fillOval(centerX - 4, centerY - 4, 8, 8);
    }

    private void drawWeatherAndDay(Graphics2D g2d, float darkness, int cx, int cy) {
        if (isRaining) {
            g2d.setColor(new Color(150, 150, 255, 120)); 
            Random r = new Random(); 
            for (int i = 0; i < 150; i++) {
                int rx = r.nextInt(getWidth()); 
                int ry = r.nextInt(getHeight()); 
                g2d.drawLine(rx, ry, rx - 5, ry + 15); 
            } 
        }
        if (darkness > 0) {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            if (nightLayer == null || nightLayer.getWidth() != getWidth() || nightLayer.getHeight() != getHeight()) { 
                nightLayer = gc.createCompatibleImage(getWidth(), getHeight(), Transparency.TRANSLUCENT); 
            }
            if (lightSprite == null) { 
                lightSprite = gc.createCompatibleImage(320, 320, Transparency.TRANSLUCENT); 
                Graphics2D lg = lightSprite.createGraphics(); 
                lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); 
                lg.setPaint(new RadialGradientPaint(160, 160, 160, new float[]{0f, 1f}, new Color[]{new Color(0, 0, 0, 255), new Color(0, 0, 0, 0)})); 
                lg.fillOval(0, 0, 320, 320); 
                lg.dispose(); 
            }
            if (headlightSprite == null) { 
                headlightSprite = gc.createCompatibleImage(400, 400, Transparency.TRANSLUCENT); 
                Graphics2D hg = headlightSprite.createGraphics(); 
                hg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); 
                float[] dist = {0.0f, 1.0f}; 
                Color[] colors = {new Color(0, 0, 0, 255), new Color(0, 0, 0, 0)}; 
                hg.setPaint(new RadialGradientPaint(new Point2D.Float(200, 180), 150, dist, colors)); 
                hg.fillPolygon(new int[]{200, 350, 350}, new int[]{180, 50, 310}, 3); 
                hg.setPaint(new RadialGradientPaint(new Point2D.Float(200, 220), 150, dist, colors)); 
                hg.fillPolygon(new int[]{200, 350, 350}, new int[]{220, 90, 350}, 3); 
                hg.dispose(); 
            }
            Graphics2D gr = nightLayer.createGraphics(); 
            gr.setComposite(AlphaComposite.Clear); 
            gr.fillRect(0, 0, getWidth(), getHeight()); 
            gr.setComposite(AlphaComposite.SrcOver); 
            gr.setColor(new Color(0, 0, 20, (int)(darkness * 230))); 
            gr.fillRect(0, 0, getWidth(), getHeight()); 
            gr.setComposite(AlphaComposite.DstOut);
            for (StreetLight sl : streetLights) { 
                if (sl.x + cx > -200 && sl.x + cx < getWidth() + 200 && sl.y + cy > -200 && sl.y + cy < getHeight() + 200) { 
                    gr.drawImage(lightSprite, sl.x + cx - 160, sl.y + cy - 160, null); 
                } 
            }
            if (darkness > 0.4 && !inMenu && !isGameOver) { 
                AffineTransform old = gr.getTransform(); 
                gr.translate(getWidth() / 2, getHeight() / 2); 
                gr.rotate(angle); 
                gr.drawImage(headlightSprite, -145, -200, null); 
                gr.setTransform(old); 
            }
            gr.dispose(); 
            g2d.drawImage(nightLayer, 0, 0, null);
            if (darkness > 0.3) { 
                for (StreetLight sl : streetLights) { 
                    if (sl.x + cx > -50 && sl.x + cx < getWidth() + 50 && sl.y + cy > -50 && sl.y + cy < getHeight() + 50) { 
                        g2d.setColor(new Color(255, 255, 180, (int)(150 * darkness))); 
                        g2d.fillOval(sl.x + cx - 12, sl.y + cy - 12, 24, 24); 
                        g2d.setColor(new Color(255, 255, 210, (int)(220 * darkness))); 
                        g2d.fillOval(sl.x + cx - 5, sl.y + cy - 5, 10, 10); 
                    } 
                } 
            }
        }
    }

    private void drawMenu(Graphics2D g2d, String title, String subtitle, String action) {
        if (imageManager.get("menu_bg") != null) { 
            g2d.drawImage(imageManager.get("menu_bg"), 0, 0, getWidth(), getHeight(), null); 
            g2d.setColor(new Color(0, 0, 0, 150)); 
            g2d.fillRect(0, 0, getWidth(), getHeight()); 
        } else { 
            g2d.setColor(new Color(0, 0, 0, 200)); 
            g2d.fillRect(0, 0, getWidth(), getHeight()); 
        }
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("SansSerif", Font.BOLD, 50)); 
        int titleWidth = g2d.getFontMetrics().stringWidth(title); 
        g2d.drawString(title, getWidth() / 2 - titleWidth / 2, getHeight() / 2 - 60);
        g2d.setColor(new Color(255, 215, 0)); g2d.setFont(new Font("SansSerif", Font.BOLD, 30)); 
        int subWidth = g2d.getFontMetrics().stringWidth(subtitle); 
        g2d.drawString(subtitle, getWidth() / 2 - subWidth / 2, getHeight() / 2);
        
        g2d.setColor(Color.LIGHT_GRAY); g2d.setFont(new Font("SansSerif", Font.PLAIN, 20)); 
        // Чешский язык в меню управления!
        String controls = "Ovládání: [W][A][S][D] - Jízda | [H] - Klakson | [SPACE] - Poslat popeláře"; 
        int ctrlWidth = g2d.getFontMetrics().stringWidth(controls); 
        g2d.drawString(controls, getWidth() / 2 - ctrlWidth / 2, getHeight() / 2 + 60);
        
        g2d.setColor(Color.GREEN); g2d.setFont(new Font("SansSerif", Font.BOLD, 24)); 
        int actionWidth = g2d.getFontMetrics().stringWidth(action); 
        g2d.drawString(action, getWidth() / 2 - actionWidth / 2, getHeight() / 2 + 120);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (inMenu || isGameOver) { repaint(); return; }

        dayCycle += 0.001; 
        if (notifTimer > 0) notifTimer--; 
        if (policeCooldown > 0) policeCooldown--; 
        if (intersectionCooldown > 0) intersectionCooldown--; 

        weatherTimer++; 
        if (weatherTimer > 1500) { 
            weatherTimer = 0; 
            isRaining = !isRaining; 
            if (isRaining) { soundManager.loop("rain"); friction = 0.03; } 
            else { soundManager.stop("rain"); friction = 0.1; } 
        }

        boolean isHoriz = Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)); 
        int hbW = isHoriz ? 86 : 30; int hbH = isHoriz ? 30 : 86; 
        Rectangle truckBoxFull = new Rectangle((int)truckX - (hbW+20)/2, (int)truckY - (hbH+20)/2, hbW+20, hbH+20);
        
        boolean inGasZone = (gasZone != null && truckBoxFull.intersects(gasZone));
        
        if (Math.abs(speed) > 0 && !inGasZone) fuel -= 0.018; 
        
       if (fuel <= 0 || timeLeft <= 0) { 
            isGameOver = true; 
            soundManager.stopAll(); 
            if (score >= rival.score) {
                gameOverReason = (fuel <= 0 ? "Došlo palivo! " : "Čas vypršel! ") + "Ale vyhrál jsi na body!";
            } else {
                gameOverReason = (fuel <= 0 ? "Došlo palivo! " : "Čas vypršel! ") + "Konkurent tě porazil!";
            }
        }

        frameCounter++; 
        if (frameCounter >= 60) { timeLeft--; frameCounter = 0; }
        
        if (isHonking && frameCounter % 30 == 0) soundManager.play("truck_horn");

        double oldX = truckX; double oldY = truckY; double oldAngle = angle; 
        
        if (up) { 
            speed = Math.min(speed + acceleration, maxSpeed); 
        } else if (down) { 
            speed = Math.max(speed - acceleration, -maxSpeed / 2); 
            if (speed < -0.5) { 
                reverseBeepTimer++; 
                if (reverseBeepTimer % 35 == 0) soundManager.play("reverse"); 
            } 
        } else { 
            speed *= (1.0 - friction); 
            reverseBeepTimer = 0; 
        }

        if (Math.abs(speed) > 0.5) { 
            double dir = speed > 0 ? 1 : -1; 
            if (left) angle -= turnSpeed * dir; 
            if (right) angle += turnSpeed * dir; 
        }

        for (TrafficLight tl : semaphores) tl.update();

        if (space && playerWorker == null && trashCount < maxTrash && Math.abs(speed) < 1.0) { 
            Bin closest = null; double minD = 100; 
            for (Bin b : bins) { 
                double d = Math.hypot(b.x + 13 - truckX, b.y + 18 - truckY); 
                if (d < minD) { minD = d; closest = b; } 
            } 
            if (closest != null) { 
                playerWorker = new TrashWorker(truckX, truckY, closest, false); 
                space = false; 
            } 
        }

        if (playerWorker != null) { 
            boolean done = playerWorker.update(truckX, truckY, bins, soundManager); 
            if (done) { 
                if (playerWorker.targetBin != null) { 
                    if (playerWorker.targetBin.isEmpty) { 
                        notification = "Prázdný!"; notifTimer = 60; 
                    } else { 
                        truckContents.add(playerWorker.targetBin.color); 
                        trashCount++; score += 15; 
                    } 
                } 
                playerWorker = null; 
            } 
        }

        boolean isSkidding = ((Math.abs(speed) > 3 && (left || right)) || (down && speed > 2)); 
        if (up || Math.abs(speed) > 1) {
            particles.add(new SmokeParticle(truckX - Math.cos(angle) * 40 + Math.sin(angle) * 20, truckY - Math.sin(angle) * 40 - Math.cos(angle) * 20)); 
        }
        if (isSkidding) { 
            tracks.add(new TireTrack(truckX, truckY, angle)); 
            if (!soundManager.isPlaying("screech")) soundManager.loop("screech"); 
        } else { 
            if (soundManager.isPlaying("screech")) soundManager.stop("screech"); 
        }
        
        particles.removeIf(p -> !p.update()); 
        if (tracks.size() > 200) tracks.remove(0);

        truckX += Math.cos(angle) * speed; 
        truckY += Math.sin(angle) * speed;
        
        hbW = Math.abs(Math.cos(angle)) > 0.7 ? 86 : 30; hbH = hbW == 86 ? 30 : 86; 
        Rectangle truckHitbox = new Rectangle((int)truckX - hbW/2, (int)truckY - hbH/2, hbW, hbH);
        
        boolean crashed = false; boolean hitPoliceTrigger = false;
        
        for (Building b : buildings) { if (truckHitbox.intersects(b.getSmartHitbox(imageManager))) crashed = true; } 
        if (tower != null && truckHitbox.intersects(tower.hitbox)) crashed = true; 
        for (ParkedCar pc : parkedCars) { if (truckHitbox.intersects(pc.getSmartBounds(imageManager))) { crashed = true; hitPoliceTrigger = true; } } 
        for (NPCVehicle v : traffic) { if (truckHitbox.intersects(v.getSmartBounds(imageManager))) { crashed = true; hitPoliceTrigger = true; } } 
        if (rival != null && truckHitbox.intersects(rival.getBounds())) crashed = true;

        if (crashed) { 
            if (Math.abs(speed) > 1.0) soundManager.play("crash"); 
            truckX = oldX; truckY = oldY; angle = oldAngle; speed = -speed * 0.6; 
            if (hitPoliceTrigger) triggerPolice(); 
        }

        hbW = Math.abs(Math.cos(angle)) > 0.7 ? 86 : 30; hbH = hbW == 86 ? 30 : 86; 
        truckHitbox = new Rectangle((int)truckX - hbW/2, (int)truckY - hbH/2, hbW, hbH);
        Rectangle rivalBox = (rival != null) ? rival.getBounds() : null;

        for (NPCVehicle v : traffic) { 
            v.update(semaphores, truckHitbox, rivalBox, traffic, imageManager, soundManager, frameCounter); 
        }
        
        if (rival != null) { 
            rival.update(bins, buildings, parkedCars, traffic, pedestrians, cyclists, semaphores, imageManager, soundManager, truckHitbox, dumpZone, gasZone); 
        }

        for (Pedestrian p : pedestrians) { 
            p.update(truckHitbox, rivalBox); 
            if (!p.isHit && !p.isPickedUp && truckHitbox.intersects(p.getBounds())) { 
                truckX = oldX; truckY = oldY; speed = -speed * 0.6; p.isHit = true; 
                soundManager.play("scream"); triggerAmbulance(p.x, p.y, p, null); 
            } 
        }

        for (Cyclist c : cyclists) { 
            c.update(truckHitbox, rivalBox); 
            if (!c.isHit && !c.isPickedUp && truckHitbox.intersects(c.getBounds())) { 
                truckX = oldX; truckY = oldY; speed = -speed * 0.6; c.isHit = true; 
                soundManager.play("scream"); triggerAmbulance(c.x, c.y, null, c); 
            } 
        }

        if (police != null) { 
            police.update(truckX, truckY); 
            if (!police.returning && truckHitbox.intersects(new Rectangle((int)police.x - 20, (int)police.y - 20, 40, 40))) { 
                money -= 50; notification = "POKUTA: -50 CZK!"; notifTimer = 120; 
                police.returning = true; police.returnAngle = Math.random() * Math.PI * 2; 
                policeCooldown = 300; soundManager.stop("siren"); 
            } 
            if (police.returning && Math.hypot(police.x - truckX, police.y - truckY) > 2000) police = null; 
        }
        
        // ОБРАБОТКА МНОЖЕСТВА СКОРЫХ ПОМОЩЕЙ
        Iterator<AmbulanceCar> ambIter = ambulances.iterator();
        boolean anySiren = false;
        while (ambIter.hasNext()) {
            AmbulanceCar amb = ambIter.next();
            amb.update();
            if (!amb.returning) anySiren = true; // Звук сирены, если хоть одна еще едет на вызов
            if (amb.returning && Math.hypot(amb.x - truckX, amb.y - truckY) > 2500) { 
                ambIter.remove(); // Удаляем уехавшую скорую
            }
        }
        if (anySiren) { 
            if(!soundManager.isPlaying("ambulance_siren")) soundManager.loop("ambulance_siren"); 
        } else { 
            soundManager.stop("ambulance_siren"); 
        }

        if (dumpZone != null && truckHitbox.intersects(dumpZone) && trashCount > 0) { 
            Random r = new Random(); 
            for (Color c : truckContents) {
                dumpPiles.add(new TrashPile(dumpZone.x + 50 + r.nextInt(dumpZone.width - 100), dumpZone.y + 50 + r.nextInt(dumpZone.height - 100), c)); 
            }
            money += trashCount * 25; score += trashCount * 100; timeLeft += trashCount * 5; 
            trashCount = 0; truckContents.clear(); soundManager.play("dump"); 
        }

        if (bins.isEmpty() && trashCount == 0 && !isGameOver) { 
            isGameOver = true; 
            soundManager.stopAll(); 
            if (score >= rival.score) {
                gameOverReason = "Mise splněna! Jsi lepší než konkurent!"; 
                soundManager.play("win"); 
            } else {
                gameOverReason = "Vše uklizeno, ale konkurent má více bodů...";
            }
        }

        boolean isRefueling = false; 
        if (inGasZone && money > 0) { 
            if (fuel < 99.9) { fuel += 1.0; money -= 1; isRefueling = true; } 
            else { fuel = 100.0; } 
        }
        if (isRefueling) { 
            if (!soundManager.isPlaying("gas")) soundManager.loop("gas"); 
        } else { 
            if (soundManager.isPlaying("gas")) soundManager.stop("gas"); 
        }
        
        repaint();
    }

    private void triggerPolice() { 
        if (police == null && policeCooldown == 0) { 
            police = new PoliceCar(truckX + 800, truckY + 800); 
            soundManager.loop("siren"); 
        } 
    }

    // ТЕПЕРЬ СОЗДАЕТСЯ НОВАЯ МАШИНА ДЛЯ КАЖДОГО ВЫЗОВА
    private void triggerAmbulance(double victimX, double victimY, Pedestrian ped, Cyclist cyc) {
        Random r = new Random(); 
        double ang = r.nextDouble() * Math.PI * 2;
        ambulances.add(new AmbulanceCar(truckX + Math.cos(ang)*900, truckY + Math.sin(ang)*900, victimX, victimY, ped, cyc));
        money -= 200; notification = "NEHODA! -200 CZK"; notifTimer = 120;
    }

    @Override 
    public void keyPressed(KeyEvent e) { 
        int k = e.getKeyCode(); 
        if (inMenu || isGameOver) { if (k == KeyEvent.VK_ENTER) { inMenu = false; initGame(); } return; } 
        if (k == KeyEvent.VK_W) up = true; if (k == KeyEvent.VK_S) down = true; 
        if (k == KeyEvent.VK_A) left = true; if (k == KeyEvent.VK_D) right = true; 
        if (k == KeyEvent.VK_H) isHonking = true; if (k == KeyEvent.VK_SPACE) space = true; 
    }
    
    @Override 
    public void keyReleased(KeyEvent e) { 
        int k = e.getKeyCode(); 
        if (k == KeyEvent.VK_W) up = false; if (k == KeyEvent.VK_S) down = false; 
        if (k == KeyEvent.VK_A) left = false; if (k == KeyEvent.VK_D) right = false; 
        if (k == KeyEvent.VK_H) isHonking = false; if (k == KeyEvent.VK_SPACE) space = false; 
    }
    
    @Override 
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) { 
        JFrame f = new JFrame("Popelář Simulator: Žižkov Pro"); 
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
        f.add(new PragueTrashSim()); 
        f.setSize(1000, 700); 
        f.setLocationRelativeTo(null); 
        f.setVisible(true); 
    }

    class ImageManager {
        private Map<String, Image> images = new HashMap<>();
        public void load(String fileName, String name) { 
            try { 
                File file = new File(fileName); 
                if (file.exists()) { Image img = ImageIO.read(file); images.put(name, img); } 
            } catch (Exception e) {} 
        }
        public Image get(String name) { return images.get(name); }
        public Rectangle getSmartRect(String name, int x, int y, int w, int h) { 
            Image img = get(name); 
            if (img == null) return new Rectangle(x, y, w, h); 
            int imgW = img.getWidth(null); int imgH = img.getHeight(null); 
            if (imgW <= 0 || imgH <= 0) return new Rectangle(x, y, w, h); 
            double scale = Math.min((double) w / imgW, (double) h / imgH); 
            int drawW = (int) (imgW * scale); int drawH = (int) (imgH * scale); 
            return new Rectangle(x + (w - drawW) / 2, y + (h - drawH) / 2, drawW, drawH); 
        }
        public boolean drawSmart(Graphics2D g, String name, int x, int y, int w, int h) { 
            Rectangle r = getSmartRect(name, x, y, w, h); 
            Image img = get(name); 
            if (img == null) return false; 
            g.drawImage(img, r.x, r.y, r.width, r.height, null); 
            return true; 
        }
    }

    class TrashWorker {
        double x, y; Bin targetBin; boolean returning = false, isRival; int anim = 0; Color shirtColor;
        public TrashWorker(double startX, double startY, Bin target, boolean isRival) { 
            this.x = startX; this.y = startY; this.targetBin = target; this.isRival = isRival; 
            this.shirtColor = isRival ? Color.RED : new Color(255, 140, 0); 
        }
        public boolean update(double ownerX, double ownerY, List<Bin> gameBins, SoundManager sm) { 
            anim++; double tx, ty; 
            if (!returning) { 
                if (targetBin != null && gameBins.contains(targetBin)) { 
                    tx = targetBin.x + 13; ty = targetBin.y + 18; 
                    double dist = Math.hypot(tx - x, ty - y); 
                    if (dist < 10) { 
                        returning = true; gameBins.remove(targetBin); 
                        if (!targetBin.isEmpty) sm.play("collect"); 
                    } 
                } else { 
                    targetBin = null; returning = true; tx = ownerX; ty = ownerY; 
                } 
            } else { 
                tx = ownerX; ty = ownerY; 
                double dist = Math.hypot(tx - x, ty - y); 
                if (dist < 30) return true; 
            } 
            double a = Math.atan2(ty - y, tx - x); 
            double runSpeed = isRival ? 7.5 : 4.0; 
            x += Math.cos(a) * runSpeed; y += Math.sin(a) * runSpeed; 
            return false; 
        }
        public void draw(Graphics2D g, int cx, int cy) { 
            AffineTransform old = g.getTransform(); 
            g.translate(x + cx, y + cy); 
            double tx = returning ? (isRival ? rival.x : truckX) : (targetBin != null ? targetBin.x+13 : x); 
            double ty = returning ? (isRival ? rival.y : truckY) : (targetBin != null ? targetBin.y+18 : y); 
            g.rotate(Math.atan2(ty - y, tx - x) + Math.PI/2); 
            g.setColor(Color.BLACK); 
            int legOffset = (anim / 5) % 2 == 0 ? 4 : -4; 
            g.fillRect(-6, legOffset, 4, 8); g.fillRect(2, -legOffset, 4, 8); 
            g.setColor(shirtColor); g.fillRoundRect(-8, -6, 16, 12, 5, 5); 
            g.setColor(new Color(255, 220, 180)); g.fillOval(-5, -12, 10, 10); 
            if (returning && targetBin != null) { 
                g.setColor(targetBin.color); g.fillRect(-7, -25, 14, 18); 
                g.setColor(Color.BLACK); g.drawRect(-7, -25, 14, 18); 
            } 
            g.setTransform(old); 
        }
    }

 class TrashRival {
        double x, y, speed = 6.5, angle = 0; 
        TrashWorker worker = null; Bin targetBin = null;
        int actionCooldown = 0, ghostTimer = 0, reverseTimer = 0; 
        int score = 0, trashCount = 0, state = 0, patrolIndex = 0; 
        double fuel = 100.0;
        
        double lastX, lastY; int stuckCheckTimer = 0;

        private final Point[] patrolRoute = { 
            new Point(1250, 550), new Point(1950, 550), new Point(1950, 1250), 
            new Point(2650, 1250), new Point(2650, 1950), new Point(1950, 1950), 
            new Point(1950, 2650), new Point(1250, 2650), new Point(1250, 1950), 
            new Point(550, 1950), new Point(550, 1250), new Point(1250, 1250) 
        };

        public TrashRival(double startX, double startY) { 
            this.x = startX; this.y = startY; this.lastX = startX; this.lastY = startY; 
        }

        private boolean isBlocked(double px, double py, List<Building> bld, List<ParkedCar> parked, List<NPCVehicle> traffic, List<Pedestrian> peds, List<Cyclist> cyc, Rectangle playerBox, ImageManager im) { 
            if (playerBox.contains(px, py)) return true; 
            for (Building b : bld) if (b.getSmartHitbox(im).contains(px, py)) return true; 
            for (ParkedCar pc : parked) if (pc.getSmartBounds(im).contains(px, py)) return true; 
            for (NPCVehicle v : traffic) if (v.getSmartBounds(im).contains(px, py)) return true; 
            return false; 
        }

        private Point getNavTarget(Point ultimateTarget, double rx, double ry) { 
            int[] axes = {550, 1250, 1950, 2650}; 
            int nearX = axes[0], nearY = axes[0]; 
            for (int a : axes) { 
                if (Math.abs(rx - a) < Math.abs(rx - nearX)) nearX = a; 
                if (Math.abs(ry - a) < Math.abs(ry - nearY)) nearY = a; 
            } 
            boolean atX = Math.abs(rx - nearX) < 80; 
            boolean atY = Math.abs(ry - nearY) < 80; 
            if (atX && atY) { 
                if (Math.abs(rx - ultimateTarget.x) > Math.abs(ry - ultimateTarget.y)) return new Point((int)ultimateTarget.x, nearY); 
                else return new Point(nearX, (int)ultimateTarget.y);
            } 
            if (atY) return new Point((int)ultimateTarget.x, nearY); 
            if (atX) return new Point(nearX, (int)ultimateTarget.y); 
            if (Math.abs(rx - nearX) < Math.abs(ry - nearY)) return new Point(nearX, (int)ry); 
            return new Point((int)rx, nearY); 
        }

        public void update(List<Bin> gameBins, List<Building> bld, List<ParkedCar> parkedCars, List<NPCVehicle> traffic, List<Pedestrian> peds, List<Cyclist> cyc, List<TrafficLight> lights, ImageManager im, SoundManager sm, Rectangle playerBox, Rectangle dZone, Rectangle gZone) {
            if (actionCooldown > 0) { actionCooldown--; return; }
            if (ghostTimer > 0) ghostTimer--; 
            if (fuel > 0 && Math.abs(speed) > 0) fuel = Math.max(0, fuel - 0.015);
            if (fuel <= 0) { fuel = 0; speed = 0; return; }
            if (fuel <= 35) state = 2; else if (trashCount >= maxTrash) state = 1; else state = 0; 
            
            if (worker != null) { 
                speed = 0; boolean done = worker.update(x, y, gameBins, sm); 
                if (done) { if (worker.targetBin != null && !worker.targetBin.isEmpty) { trashCount++; score += 15; } worker = null; targetBin = null; actionCooldown = 30; } 
                return; 
            }
            if (targetBin != null && !gameBins.contains(targetBin)) targetBin = null;

            stuckCheckTimer++;
            if (stuckCheckTimer > 150) { 
                stuckCheckTimer = 0;
                if (Math.hypot(x - lastX, y - lastY) < 30 && targetBin == null && worker == null) {
                    reverseTimer = 50; angle += Math.PI / 2; ghostTimer = 250; 
                }
                lastX = x; lastY = y;
            }

            Rectangle myHb = getBounds(); boolean hitPhysical = false;
            if (ghostTimer == 0) { if (myHb.intersects(playerBox)) hitPhysical = true; for (Building b : bld) if (myHb.intersects(b.getSmartHitbox(im))) hitPhysical = true; }
            if (hitPhysical && reverseTimer == 0) { reverseTimer = 30; actionCooldown = 10; if (myHb.intersects(playerBox)) sm.play("crash"); return; }
            if (reverseTimer > 0) { reverseTimer--; speed = -3.5; x += Math.cos(angle) * speed; y += Math.sin(angle) * speed; return; }

            boolean isParking = false; 
            boolean forceDirect = false; // НОВЫЙ ФЛАГ: ехать напрямую без дорожных правил
            Point finalTarget = null; 

            if (state == 0) { 
                Bin spottedBin = null; double minDist = Double.MAX_VALUE;
                for (Bin b : gameBins) { double d = Math.hypot(b.x - x, b.y - y); if (d < minDist && d < 1800) { minDist = d; spottedBin = b; } }
                if (spottedBin != null) {
                    targetBin = spottedBin; 
                    int[] axes = {550, 1250, 1950, 2650}; int cX = axes[0], cY = axes[0]; 
                    for (int a : axes) { if (Math.abs(targetBin.x - a) < Math.abs(targetBin.x - cX)) cX = a; if (Math.abs(targetBin.y - a) < Math.abs(targetBin.y - cY)) cY = a; }
                    boolean isHorizBin = Math.abs(targetBin.y % 700 - 550) < Math.abs(targetBin.x % 700 - 550); 
                    int alignX = isHorizBin ? targetBin.x + 13 : cX; 
                    int alignY = isHorizBin ? cY : targetBin.y + 18; 
                    finalTarget = new Point(alignX, alignY); 
                    if (Math.hypot(alignX - x, alignY - y) < 100) { 
                        isParking = true; speed = Math.max(0.0, speed - 0.4);
                        double faceAng = isHorizBin ? ((y > targetBin.y) ? -Math.PI/2 : Math.PI/2) : ((x > targetBin.x) ? Math.PI : 0); 
                        navAngle(faceAng, 0.15); 
                        if (Math.abs(speed) < 1.0) { speed = 0; worker = new TrashWorker(x, y, targetBin, true); } 
                    }
                } else { 
                    Point p = patrolRoute[patrolIndex]; if (Math.hypot(p.x - x, p.y - y) < 100) patrolIndex = (patrolIndex + 1) % patrolRoute.length; finalTarget = patrolRoute[patrolIndex]; 
                }
            } else if (state == 1) { // ЕДЕМ НА СВАЛКУ
                Point dumpCenter = new Point(dZone.x + dZone.width/2, dZone.y + dZone.height/2); 
                if (Math.hypot(dumpCenter.x - x, dumpCenter.y - y) < 250) { // Проверка на сброс
                    score += trashCount * 100; trashCount = 0; state = 0; sm.play("dump"); actionCooldown = 60; 
                } 
                // Если мы уже близко к углу со свалкой (X и Y меньше 800), отключаем дороги
                if (x < 850 && y < 850) { finalTarget = dumpCenter; forceDirect = true; } 
                else { finalTarget = new Point(550, 550); }
            } else if (state == 2) { // ЕДЕМ НА ЗАПРАВКУ
                Point gasCenter = new Point(gZone.x + gZone.width/2, gZone.y + gZone.height/2); 
                if (Math.hypot(gasCenter.x - x, gasCenter.y - y) < 250) { 
                    fuel = 100; state = 0; sm.play("gas"); actionCooldown = 60; 
                } 
                // Если близко к заправке (правый верхний угол), едем напрямую
                if (x > 1800 && y < 850) { finalTarget = gasCenter; forceDirect = true; } 
                else { finalTarget = new Point(2650, 550); } 
            }

            boolean avoiding = false;
            if (ghostTimer == 0 && !isParking) {
                int radarDist = 90; 
                double cx = x + Math.cos(angle) * radarDist; double cy = y + Math.sin(angle) * radarDist; 
                if (isBlocked(cx, cy, bld, parkedCars, traffic, peds, cyc, playerBox, im)) { 
                    avoiding = true; speed = 3.5; angle += 0.15; 
                } else { speed = 6.5; }
            }
            
            if (!isParking && !avoiding) { 
                // Если включен forceDirect, берем цель напрямую, иначе — через систему дорог
                Point waypoint = forceDirect ? finalTarget : getNavTarget(finalTarget, x, y); 
                double targetAngle = Math.atan2(waypoint.y - y, waypoint.x - x); 
                
                if (forceDirect) {
                    navAngle(targetAngle, 0.1); // Свободное движение к цели
                } else {
                    // Старая логика удержания в полосе
                    int[] axes = {550, 1250, 1950, 2650}; int nearX = axes[0], nearY = axes[0]; 
                    for (int a : axes) { if (Math.abs(x - a) < Math.abs(x - nearX)) nearX = a; if (Math.abs(y - a) < Math.abs(y - nearY)) nearY = a; }
                    boolean movingHoriz = Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle));
                    int laneOffset = 35;
                    double diff = Math.abs(targetAngle - angle);
                    while(diff > Math.PI) diff -= 2*Math.PI;
                    boolean isTurning = Math.abs(diff) > 0.5;

                    if (movingHoriz) {
                        int desiredY = nearY + (Math.cos(angle) > 0 ? laneOffset : -laneOffset);
                        if (!isTurning && Math.abs(y - nearY) < 60) { y += (desiredY - y) * 0.15; navAngle(Math.cos(angle) > 0 ? 0 : Math.PI, 0.2); } 
                        else navAngle(targetAngle, 0.1);
                    } else {
                        int desiredX = nearX + (Math.sin(angle) > 0 ? laneOffset : -laneOffset);
                        if (!isTurning && Math.abs(x - nearX) < 60) { x += (desiredX - x) * 0.15; navAngle(Math.sin(angle) > 0 ? Math.PI/2 : -Math.PI/2, 0.2); } 
                        else navAngle(targetAngle, 0.1);
                    }
                }
            }
            x += Math.cos(angle) * speed; y += Math.sin(angle) * speed;
        }

        private void navAngle(double targetAngle, double turnRate) { 
            double diff = targetAngle - angle; 
            while (diff > Math.PI) diff -= 2 * Math.PI; 
            while (diff < -Math.PI) diff += 2 * Math.PI; 
            if (diff > turnRate) angle += turnRate; else if (diff < -turnRate) angle -= turnRate; else angle = targetAngle; 
        }

        public void draw(Graphics2D g, int cx, int cy, ImageManager im) { 
            int tw = 110; int th = 46; 
            AffineTransform old = g.getTransform(); g.translate(x + cx, y + cy); g.rotate(angle); 
            if (ghostTimer > 0) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)); 
            if (!im.drawSmart(g, "rival", -tw / 2, -th / 2, tw, th)) { 
                g.setColor(new Color(130, 20, 20)); g.fillRoundRect(-tw / 2, -th / 2, tw - 25, th, 10, 10); 
                g.setColor(Color.WHITE); g.fillRoundRect(tw / 2 - 25, -th / 2 + 3, 25, th - 6, 8, 8); 
            } 
            if (fuel <= 0 && (System.currentTimeMillis() / 500) % 2 == 0) { 
                g.setColor(Color.ORANGE); g.fillOval(-tw / 2 + 5, -th / 2 - 5, 8, 8); g.fillOval(-tw / 2 + 5, th / 2 - 3, 8, 8); 
            } 
            if (ghostTimer > 0) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); 
            g.setTransform(old); 
        }

        public void drawHeadlights(Graphics2D g2d, int cx, int cy) { 
            if (fuel <= 0) return; 
            AffineTransform old = g2d.getTransform(); g2d.translate(x + cx, y + cy); g2d.rotate(angle); 
            g2d.setColor(new Color(255, 255, 160, 40)); 
            g2d.fillPolygon(new int[]{55, 140, 140}, new int[]{-18, -40, 5}, 3); 
            g2d.fillPolygon(new int[]{55, 140, 140}, new int[]{18, 5, 40}, 3); 
            g2d.setTransform(old); 
        }

        public Rectangle getBounds() { return new Rectangle((int) x - 40, (int) y - 20, 80, 40); }
    }

    class StreetLight { 
        int x, y; 
        public StreetLight(int x, int y) { this.x = x; this.y = y; } 
        public void drawPole(Graphics2D g, int cx, int cy) { 
            g.setColor(new Color(40, 40, 40)); g.fillRect(x + cx - 3, y + cy - 3, 6, 6); 
            g.setColor(new Color(80, 80, 80)); g.fillOval(x + cx - 5, y + cy - 5, 10, 10); 
        } 
    }

    class PoliceCar { 
        double x, y; int flash = 0; boolean returning = false; double returnAngle = 0; 
        public PoliceCar(double startX, double startY) { this.x = startX; this.y = startY; } 
        public void update(double targetX, double targetY) { 
            if (returning) { 
                x += Math.cos(returnAngle) * 8.5; y += Math.sin(returnAngle) * 8.5; 
            } else { 
                double a = Math.atan2(targetY - y, targetX - x); 
                x += Math.cos(a) * 8.5; y += Math.sin(a) * 8.5; 
            } 
            flash++; 
        } 
        public void draw(Graphics2D g, int cx, int cy, ImageManager im) { 
            AffineTransform old = g.getTransform(); g.translate(x + cx, y + cy); 
            double a = returning ? returnAngle : Math.atan2(truckY - y, truckX - x); 
            g.rotate(a); 
            if (!im.drawSmart(g, "police", -30, -15, 60, 30)) { 
                g.setColor(Color.WHITE); g.fillRoundRect(-30, -15, 60, 30, 8, 8); 
                g.setColor(Color.BLUE); g.fillRect(-10, -15, 20, 30); 
            } 
            if ((flash / 10) % 2 == 0) { 
                g.setColor(Color.RED); g.fillOval(-5, -10, 10, 10); 
            } else { 
                g.setColor(Color.BLUE); g.fillOval(-5, 0, 10, 10); 
            } 
            g.setTransform(old); 
        } 
    }

    class AmbulanceCar {
        double x, y, targetX, targetY; Pedestrian targetPedestrian; Cyclist targetCyclist;
        int flash = 0, waitTimer = 0; boolean returning = false; double returnAngle = 0;
        
        public AmbulanceCar(double startX, double startY, double tx, double ty, Pedestrian p, Cyclist c) { 
            this.x = startX; this.y = startY; this.targetX = tx; this.targetY = ty; 
            this.targetPedestrian = p; this.targetCyclist = c; 
        }
        
        public void update() { 
            if (returning) { 
                x += Math.cos(returnAngle) * 7.5; y += Math.sin(returnAngle) * 7.5; 
            } else { 
                double dist = Math.hypot(targetX - x, targetY - y); 
                if (dist > 50) { 
                    double a = Math.atan2(targetY - y, targetX - x); 
                    x += Math.cos(a) * 7.5; y += Math.sin(a) * 7.5; 
                } else { 
                    waitTimer++; 
                    if (waitTimer == 100) { 
                        if (targetPedestrian != null) targetPedestrian.isPickedUp = true; 
                        if (targetCyclist != null) targetCyclist.isPickedUp = true; 
                    } 
                    if (waitTimer > 200) { returning = true; returnAngle = Math.random() * Math.PI * 2; } 
                } 
            } 
            flash++; 
        }
        
        public void draw(Graphics2D g, int cx, int cy, ImageManager im) { 
            AffineTransform old = g.getTransform(); g.translate(x + cx, y + cy); 
            double a = returning ? returnAngle : Math.atan2(targetY - y, targetX - x); g.rotate(a); 
            if (!im.drawSmart(g, "ambulance", -30, -15, 60, 30)) { 
                g.setColor(Color.WHITE); g.fillRoundRect(-30, -15, 60, 30, 8, 8); 
                g.setColor(Color.RED); g.fillRect(-5, -15, 10, 30); g.fillRect(-15, -5, 30, 10); 
            } 
            if ((flash / 10) % 2 == 0) { 
                g.setColor(Color.BLUE); g.fillOval(-5, -10, 10, 10); 
            } else { 
                g.setColor(Color.WHITE); g.fillOval(-5, 0, 10, 10); 
            } 
            g.setTransform(old); 
        }
    }

    class TrafficLight { 
        int x, y, size, timer = 0, state = 0; Rectangle bounds; 
        public TrafficLight(int x, int y, int streetWidth) { this.x = x; this.y = y; this.size = streetWidth; this.bounds = new Rectangle(x, y, streetWidth, streetWidth); } 
        public void update() { timer++; if (state == 0 && timer > 300) { state = 1; timer = 0; } else if (state == 1 && timer > 60) { state = 2; timer = 0; } else if (state == 2 && timer > 300) { state = 3; timer = 0; } else if (state == 3 && timer > 60) { state = 0; timer = 0; } } 
        private void drawLightBox(Graphics2D g, int lx, int ly, boolean isH, int camX, int camY) { 
            AffineTransform old = g.getTransform(); g.translate(lx + camX, ly + camY); if (isH) g.rotate(Math.PI / 2); 
            g.setColor(new Color(0, 0, 0, 100)); g.fillRect(-5, 20, 10, 30); g.setColor(Color.DARK_GRAY); g.fillRect(-3, 20, 6, 30); 
            g.setColor(new Color(40, 40, 40)); g.fillRoundRect(-12, -28, 24, 56, 8, 8); 
            Color hC = (state == 0) ? Color.GREEN : ((state == 1) ? Color.YELLOW : Color.RED); 
            Color vC = (state == 2) ? Color.GREEN : ((state == 3) ? Color.YELLOW : Color.RED); 
            Color activeColor = isH ? hC : vC; 
            g.setColor((activeColor == Color.RED) ? Color.RED : new Color(50, 0, 0)); g.fillOval(-8, -24, 16, 16); 
            g.setColor((activeColor == Color.YELLOW) ? Color.YELLOW : new Color(50, 50, 0)); g.fillOval(-8, -4, 16, 16); 
            g.setColor((activeColor == Color.GREEN) ? Color.GREEN : new Color(0, 50, 0)); g.fillOval(-8, 16, 16, 16); 
            g.setTransform(old); 
        } 
        public void draw(Graphics2D g, int cx, int cy) { drawLightBox(g, x - 10, y + 10, true, cx, cy); drawLightBox(g, x + size + 10, y + size - 10, true, cx, cy); drawLightBox(g, x + size - 10, y - 10, false, cx, cy); drawLightBox(g, x + 10, y + size + 10, false, cx, cy); } 
    }

    class ParkedCar { 
        int x, y, w, h; Color col; boolean horiz; 
        public ParkedCar(int x, int y, boolean horiz) { this.x = x; this.y = y; this.horiz = horiz; this.col = new Color(new Random().nextInt(255), 50, 150); if (horiz) { this.w = 80; this.h = 40; } else { this.w = 40; this.h = 80; } } 
        public void draw(Graphics2D g, int cx, int cy, ImageManager im) { 
            g.setColor(new Color(255, 255, 255, 120)); g.setStroke(new BasicStroke(2)); g.drawRect(x + cx - 2, y + cy - 2, w + 4, h + 4); g.setStroke(new BasicStroke(1)); 
            AffineTransform old = g.getTransform(); g.translate(x + cx + w / 2, y + cy + h / 2); if (!horiz) g.rotate(Math.PI / 2); 
            if (!im.drawSmart(g, "car", -40, -20, 80, 40)) { g.setColor(new Color(0, 0, 0, 80)); g.fillRoundRect(-35, -15, 80, 40, 10, 10); g.setColor(col); g.fillRoundRect(-40, -20, 80, 40, 10, 10); g.setColor(Color.DARK_GRAY); g.fillRect(-25, -15, 50, 30); } 
            g.setTransform(old); 
        } 
        public Rectangle getSmartBounds(ImageManager im) { Rectangle r; if (horiz) { r = im.getSmartRect("car", x, y, w, h); } else { Rectangle sr = im.getSmartRect("car", x, y, h, w); r = new Rectangle(x + (w - sr.height) / 2, y + (h - sr.width) / 2, sr.height, sr.width); } r.grow(-6, -6); return r; } 
    }

    class ZizkovTower { 
        int x, y; Rectangle hitbox; 
        public ZizkovTower(int x, int y) { this.x = x; this.y = y; this.hitbox = new Rectangle(x - 70, y - 70, 140, 140); } 
        public void draw(Graphics2D g, int cx, int cy) { 
            int dx = x + cx; int dy = y + cy; 
            g.setColor(new Color(0, 0, 0, 80)); g.fillOval(dx - 90, dy + 20, 180, 60); 
            g.setColor(new Color(160, 160, 165)); g.fillRoundRect(dx - 15, dy - 150, 30, 200, 10, 10); 
            g.setColor(new Color(140, 140, 145)); g.fillRoundRect(dx - 45, dy - 100, 20, 150, 10, 10); g.fillRoundRect(dx + 25, dy - 100, 20, 150, 10, 10); 
            g.setColor(new Color(190, 190, 200)); g.fillRoundRect(dx - 55, dy - 80, 40, 30, 10, 10); g.fillRoundRect(dx + 15, dy - 80, 40, 30, 10, 10); g.fillRoundRect(dx - 25, dy - 120, 50, 40, 15, 15); g.fillRoundRect(dx - 20, dy - 40, 40, 30, 10, 10); 
            g.setColor(new Color(50, 50, 60)); g.fillRect(dx - 55, dy - 75, 40, 10); g.fillRect(dx + 15, dy - 75, 40, 10); g.fillRect(dx - 25, dy - 110, 50, 15); g.fillRect(dx - 20, dy - 35, 40, 10); 
            g.setColor(Color.BLACK); g.fillOval(dx - 48, dy - 40, 6, 8); g.fillOval(dx - 40, dy - 10, 6, 8); g.fillOval(dx + 30, dy - 20, 6, 8); g.fillOval(dx + 25, dy + 10, 6, 8); g.fillOval(dx - 8, dy - 60, 6, 8); 
            g.setColor(new Color(100, 100, 105)); g.fillRect(dx - 3, dy - 190, 6, 40); g.fillRect(dx - 1, dy - 210, 2, 20); 
            if ((System.currentTimeMillis() / 500) % 2 == 0) { g.setColor(Color.RED); g.fillOval(dx - 4, dy - 215, 8, 8); } 
        } 
    }

    public class Building { 
        int x, y, w, h; Rectangle hitbox; 
        public Building(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; this.hitbox = new Rectangle(x, y, w, h); } 
        public void draw(Graphics2D g2d, int camX, int camY, ImageManager im) { 
            if (!im.drawSmart(g2d, "building", x + camX, y + camY, w, h)) { 
                g2d.setColor(Color.GRAY); g2d.fillRect(x + camX - 10, y + camY - 10, w + 20, h + 20); 
                g2d.setColor(new Color(150, 70, 50)); g2d.fillRect(x + camX, y + camY, w, h); 
                g2d.setColor(new Color(100, 40, 30)); g2d.drawRect(x + camX, y + camY, w, h); 
                g2d.setColor(new Color(50, 120, 50)); g2d.fillRect(x + camX + 40, y + camY + 40, w - 80, h - 80); 
            } 
        } 
        public Rectangle getSmartHitbox(ImageManager im) { Rectangle r = im.getSmartRect("building", x, y, w, h); r.grow(-5, -5); return r; } 
    }

    class TrashPile { 
        int x, y; Color color; int[] ox, oy; 
        public TrashPile(int x, int y, Color color) { 
            this.x = x; this.y = y; this.color = color; Random r = new Random(); ox = new int[5]; oy = new int[5]; 
            for (int i = 0; i < 5; i++) { ox[i] = r.nextInt(25) - 12; oy[i] = r.nextInt(25) - 12; } 
        } 
        public void draw(Graphics2D g, int cx, int cy) { 
            g.setColor(color); for (int i = 0; i < 5; i++) g.fillOval(x + cx + ox[i], y + cy + oy[i], 16, 14); 
            g.setColor(color.darker()); for (int i = 0; i < 5; i++) g.drawOval(x + cx + ox[i], y + cy + oy[i], 16, 14); 
        } 
    }

    class SmokeParticle { 
        double x, y, opacity = 120, size = 10; 
        public SmokeParticle(double x, double y) { this.x = x; this.y = y; } 
        public boolean update() { opacity -= 3; size += 0.5; return opacity > 0; } 
        public void draw(Graphics2D g, int cx, int cy) { 
            g.setColor(new Color(150, 150, 150, (int)opacity)); 
            g.fillOval((int)(x + cx - size / 2), (int)(y + cy - size / 2), (int)size, (int)size); 
        } 
    }

    class TireTrack { 
        double x, y, ang; 
        public TireTrack(double x, double y, double ang) { this.x = x; this.y = y; this.ang = ang; } 
        public void draw(Graphics2D g, int cx, int cy) { 
            AffineTransform old = g.getTransform(); g.translate(x + cx, y + cy); g.rotate(ang); 
            g.setColor(new Color(0, 0, 0, 40)); g.fillRect(-40, -18, 15, 6); g.fillRect(-40, 12, 15, 6); 
            g.setTransform(old); 
        } 
    }

    class Bin { 
        int x, y; Color color; boolean isEmpty; 
        public Bin(int x, int y, Color color, boolean isEmpty) { this.x = x; this.y = y; this.color = color; this.isEmpty = isEmpty; } 
        public void draw(Graphics2D g2d, int camX, int camY, ImageManager im) { 
            if (!im.drawSmart(g2d, "bin", x + camX, y + camY, 26, 36)) { 
                g2d.setColor(new Color(0, 0, 0, 50)); g2d.fillRect(x + camX + 4, y + camY + 4, 26, 36); 
                g2d.setColor(color); g2d.fillRoundRect(x + camX, y + camY, 26, 36, 6, 6); 
                g2d.setColor(Color.BLACK); g2d.drawRoundRect(x + camX, y + camY, 26, 36, 6, 6); 
            } else { 
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100)); 
                g2d.fillRect(x + camX, y + camY, 26, 36); 
            } 
        } 
        public Rectangle getSmartBounds(ImageManager im) { return im.getSmartRect("bin", x, y, 26, 36); } 
    }

    class Tree { 
        int x, y; 
        public Tree(int x, int y) { this.x = x; this.y = y; } 
        public void draw(Graphics2D g2d, int camX, int camY, ImageManager im) { 
            if (!im.drawSmart(g2d, "tree", x + camX, y + camY, 45, 45)) { 
                g2d.setColor(new Color(0, 0, 0, 50)); g2d.fillOval(x + camX + 8, y + camY + 8, 45, 45); 
                g2d.setColor(new Color(34, 139, 34)); g2d.fillOval(x + camX, y + camY, 45, 45); 
            } 
        } 
    }

    class Pedestrian { 
        double x, y, startX, startY; boolean horizontal; int direction = 1, animationFrame = 0; 
        Color shirtColor; boolean isHit = false, isPickedUp = false; 
        public Pedestrian(int x, int y, boolean h) { 
            this.x = x; this.y = y; this.startX = x; this.startY = y; this.horizontal = h; 
            Color[] cs = {Color.RED, Color.BLUE, Color.ORANGE, Color.PINK, Color.CYAN}; 
            this.shirtColor = cs[new Random().nextInt(cs.length)]; 
        }
        public void update(Rectangle truckBox, Rectangle rivalBox) { 
            if (isHit || isPickedUp) return; 
            if (rivalBox != null && getBounds().intersects(rivalBox)) { isPickedUp = true; return; } 
            int sightX = (int)x + (horizontal ? direction * 20 : 0) - 15; 
            int sightY = (int)y + (!horizontal ? direction * 20 : 0) - 15; 
            Rectangle sight = new Rectangle(sightX, sightY, 30, 30); 
            boolean blocked = sight.intersects(truckBox) || (rivalBox != null && sight.intersects(rivalBox)); 
            if (blocked) { 
                direction *= -1; 
                if (horizontal) x += direction * 5; else y += direction * 5; 
                animationFrame++; return; 
            } 
            if (horizontal) { x += 1.5 * direction; if (Math.abs(x - startX) > 300) direction *= -1; } 
            else { y += 1.5 * direction; if (Math.abs(y - startY) > 300) direction *= -1; } 
            animationFrame++; 
        }
        public void draw(Graphics2D g2d, int cx, int cy) { 
            if (isPickedUp) return; 
            AffineTransform old = g2d.getTransform(); g2d.translate((int)x + cx, (int)y + cy); 
            if (isHit) g2d.rotate(Math.PI / 2); 
            else { if (horizontal) { if (direction == -1) g2d.rotate(Math.PI); } else { if (direction == 1) g2d.rotate(Math.PI / 2); else g2d.rotate(-Math.PI / 2); } } 
            g2d.setColor(shirtColor); g2d.fillOval(-10, -8, 20, 16); 
            g2d.setColor(new Color(255, 220, 180)); g2d.fillOval(-5, -5, 10, 10); 
            g2d.setColor(Color.BLACK); 
            int fo = (animationFrame / 10) % 2 == 0 ? 3 : -3; 
            g2d.fillRect(fo - 5, 4, 6, 4); g2d.fillRect(-fo + 2, 4, 6, 4); 
            g2d.setTransform(old); 
        }
        public Rectangle getBounds() { return new Rectangle((int)x - 10, (int)y - 10, 20, 20); }
    }

    class Cyclist { 
        double x, y, startX, startY; boolean horizontal, isScooter; int direction = 1; 
        boolean isHit = false, isPickedUp = false; 
        public Cyclist(int x, int y, boolean h, boolean isScooter) { 
            this.x = x; this.y = y; this.startX = x; this.startY = y; this.horizontal = h; this.isScooter = isScooter; 
        } 
        public void update(Rectangle truckBox, Rectangle rivalBox) { 
            if (isHit || isPickedUp) return; 
            if (rivalBox != null && getBounds().intersects(rivalBox)) { isPickedUp = true; return; } 
            int sightX = (int)x + (horizontal ? direction * 30 : 0) - 20; 
            int sightY = (int)y + (!horizontal ? direction * 30 : 0) - 20; 
            Rectangle sight = new Rectangle(sightX, sightY, 40, 40); 
            boolean blocked = sight.intersects(truckBox) || (rivalBox != null && sight.intersects(rivalBox)); 
            if (blocked) { 
                direction *= -1; 
                if (horizontal) x += direction * 5; else y += direction * 5; 
                return; 
            } 
            if (horizontal) { x += 2 * direction; if (Math.abs(x - startX) > 500) direction *= -1; } 
            else { y += 2 * direction; if (Math.abs(y - startY) > 500) direction *= -1; } 
        } 
        public void draw(Graphics2D g2d, int cx, int cy, ImageManager im) { 
            if (isPickedUp) return; 
            AffineTransform old = g2d.getTransform(); g2d.translate((int)x + cx, (int)y + cy); 
            if (isHit) g2d.rotate(Math.PI / 2); 
            else { if (horizontal) { if (direction == -1) g2d.rotate(Math.PI); } else { if (direction == 1) g2d.rotate(Math.PI / 2); else g2d.rotate(-Math.PI / 2); } } 
            String imgName = isScooter ? "scooter" : "bike"; 
            if (!im.drawSmart(g2d, imgName, -15, -10, 30, 20)) { 
                g2d.setColor(Color.YELLOW); g2d.fillOval(-10, -5, 20, 10); 
                g2d.setColor(Color.BLACK); g2d.fillOval(-5, -5, 10, 10); 
            } 
            g2d.setTransform(old); 
        } 
        public Rectangle getBounds() { return new Rectangle((int)x - 15, (int)y - 10, 30, 20); } 
    }

    class NPCVehicle { 
        int x, y, startX, startY, speed, currentSpeed; boolean horizontal; Color color; 
        public NPCVehicle(int x, int y, int s, boolean h, Color c) { 
            this.x = x; this.y = y; this.startX = x; this.startY = y; this.speed = s; this.horizontal = h; this.color = c; this.currentSpeed = s; 
        } 
        public void update(List<TrafficLight> lights, Rectangle truckBox, Rectangle rivalBox, List<NPCVehicle> allTraffic, ImageManager im, SoundManager sm, int globalFrame) { 
            boolean stopLight = false, carAhead = false; 
            Rectangle sight; int sightDist = 80; 
            if (horizontal) { sight = new Rectangle(speed > 0 ? x + 60 : x - sightDist, y - 20, sightDist, 70); } 
            else { sight = new Rectangle(x - 20, speed > 0 ? y + 60 : y - sightDist, 70, sightDist); } 
            for (TrafficLight tl : lights) { 
                if (sight.intersects(tl.bounds)) { 
                    if (horizontal && tl.state != 0) stopLight = true; 
                    if (!horizontal && tl.state != 2) stopLight = true; 
                } 
            } 
            for (NPCVehicle other : allTraffic) { 
                if (other != this && sight.intersects(other.getSmartBounds(im))) { carAhead = true; break; } 
            } 
            Rectangle vb; 
            if (horizontal) { vb = new Rectangle(speed > 0 ? x + 60 : x - 150, y - 20, 150, 70); } 
            else { vb = new Rectangle(x - 20, speed > 0 ? y + 60 : y - 150, 70, 150); } 
            boolean blockedByPlayer = vb.intersects(truckBox); 
            boolean blockedByRival = (rivalBox != null && vb.intersects(rivalBox)); 
            
            if (blockedByPlayer || blockedByRival || stopLight || carAhead) { 
                currentSpeed = 0; 
                if ((blockedByPlayer || blockedByRival) && globalFrame % 50 == 0) sm.play("car_horn"); 
            } else { 
                currentSpeed = speed; 
            } 
            
            if (horizontal) { 
                x += currentSpeed; 
                if (speed > 0 && x > 4500) x = -1000; 
                if (speed < 0 && x < -1000) x = 4500; 
            } else { 
                y += currentSpeed; 
                if (speed > 0 && y > 4500) y = -1000; 
                if (speed < 0 && y < -1000) y = 4500; 
            } 
        } 
        public void draw(Graphics2D g2d, int cx, int cy, ImageManager im) { 
            AffineTransform old = g2d.getTransform(); g2d.translate(x + cx, y + cy); if (!horizontal) g2d.rotate(Math.PI / 2); 
            int cw = 60, ch = 30; 
            if (!im.drawSmart(g2d, "car", -cw / 2, -ch / 2, cw, ch)) { 
                g2d.setColor(new Color(0, 0, 0, 50)); g2d.fillRoundRect(-cw / 2 + 5, -ch / 2 + 5, cw, ch, 8, 8); 
                g2d.setColor(color); g2d.fillRoundRect(-cw / 2, -ch / 2, cw, ch, 10, 10); 
            } 
            g2d.setTransform(old); 
        } 
        public void drawHeadlights(Graphics2D g2d, int cx, int cy) { 
            AffineTransform old = g2d.getTransform(); g2d.translate(x + cx, y + cy); if (!horizontal) g2d.rotate(Math.PI / 2); 
            g2d.setColor(new Color(255, 255, 150, 60)); 
            int cw = 60, hl = 90; 
            if (speed > 0) { 
                int[] hx = {cw / 2, cw / 2 + hl, cw / 2 + hl}; int[] hy1 = {-11, -30, -5}; int[] hy2 = {11, 30, 5}; 
                g2d.fillPolygon(hx, hy1, 3); g2d.fillPolygon(hx, hy2, 3); 
            } else { 
                int[] hx = {-cw / 2, -cw / 2 - hl, -cw / 2 - hl}; int[] hy1 = {-11, -30, -5}; int[] hy2 = {11, 30, 5}; 
                g2d.fillPolygon(hx, hy1, 3); g2d.fillPolygon(hx, hy2, 3); 
            } 
            g2d.setTransform(old); 
        } 
        public Rectangle getBounds() { return new Rectangle(x - 30, y - 30, 60, 60); } 
        public Rectangle getSmartBounds(ImageManager im) { 
            Rectangle r; 
            if (horizontal) { r = im.getSmartRect("car", x - 30, y - 15, 60, 30); } 
            else { Rectangle sr = im.getSmartRect("car", x - 15, y - 30, 60, 30); r = new Rectangle(x - sr.height / 2, y - sr.width / 2, sr.height, sr.width); } 
            r.grow(-6, -6); return r; 
        } 
    }

    class SoundManager { 
        private Map<String, Clip> clips = new HashMap<>(); 
        public void load(String fileName, String name) { 
            try { 
                File soundFile = new File(fileName); 
                if (!soundFile.exists()) return; 
                AudioInputStream ais = AudioSystem.getAudioInputStream(soundFile); 
                Clip clip = AudioSystem.getClip(); clip.open(ais); clips.put(name, clip); 
            } catch (Exception e) {} 
        } 
        public void play(String name) { Clip clip = clips.get(name); if (clip != null) { clip.stop(); clip.setFramePosition(0); clip.start(); } } 
        public void loop(String name) { Clip clip = clips.get(name); if (clip != null) { clip.setFramePosition(0); clip.loop(Clip.LOOP_CONTINUOUSLY); } } 
        public void stop(String name) { Clip clip = clips.get(name); if (clip != null && clip.isRunning()) { clip.stop(); } } 
        public boolean isPlaying(String name) { Clip clip = clips.get(name); return (clip != null && clip.isRunning()); } 
        public void stopAll() { for (Clip c : clips.values()) { c.stop(); } } 
    }
}