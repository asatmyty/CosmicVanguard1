import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
// Import tambahan untuk audio library bawaan Java
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;

public class CosmicVanguard extends JPanel implements ActionListener, KeyListener {

    // Ukuran Layar Game
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // --- GAME STATE MANAGEMENT ---
    private static final int STATE_MENU = 0;
    private static final int STATE_GAMEPLAY = 1;
    private static final int STATE_GAMEOVER = 2;
    private static final int STATE_PAUSE = 3;
    private int gameState = STATE_MENU; 

    private Timer timer;

    // --- ASSETS GAMBAR ---
    private BufferedImage playerImage;
    private BufferedImage playerRedImage;
    private BufferedImage alienImage;
    private BufferedImage bulletImage;
    private BufferedImage playerRedBulletImage;
    private BufferedImage alienBulletImage;
    private BufferedImage redBulletImage;
    private BufferedImage bossImage;
    
    private int playerX = 375, playerY = 500;
    private int playerSpeed = 6;
    private int playerHp = 100;
    private int shakeDuration = 0;
    private int shakeIntensity = 0;
    private int energyBar = 0;
    private int comboCount = 0;
    private int highScore = 0;
    private final String HIGHSCORE_FILE = "highscore.txt";
    private int weaponLevel = 1;
    private static final int MAX_WEAPON_LEVEL = 3;
    private final int MAX_ENERGY = 100;
    

    private boolean isBluePolarity = true;
    private boolean up, down, left, right;

    private ArrayList<FloatingText> floatingTexts = new ArrayList<>();
    private ArrayList<Bullet> playerBullets = new ArrayList<>();
    private ArrayList<AlienBullet> alienBullets = new ArrayList<>();
    private ArrayList<Alien> aliens = new ArrayList<>();
    private ArrayList<Star> stars = new ArrayList<>();
    private ArrayList<Particle> particles = new ArrayList<>();
    private ArrayList<ItemDrop> itemDrops = new ArrayList<>();

    private int score = 0;
    private int nextBossScore = 5000; // Target poin pertama untuk memunculkan boss
    private Random random = new Random();
    private int spawnCounter = 0;
    private int shootCooldown = 0;
    private final int SHOOT_DELAY = 12;

    private boolean bossActive = false;
    private int bossX = 300, bossY = -100; 
    private int bossHp = 0;
    private final int BOSS_MAX_HP = 2000;
    private int bossSpeedX = 3;
    private int bossShootCounter = 0;

    // --- AUDIO CLIP MANAGEMENT ---
    private Clip bgmClip; // Untuk BGM Looping

    public CosmicVanguard() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        loadHighScore();

        try {
            playerImage = ImageIO.read(new File("Bplayer_ship.png"));
            playerRedImage = ImageIO.read(new File("Rplayer_ship1.png"));
            alienImage = ImageIO.read(new File("alien.png"));
            bulletImage = ImageIO.read(new File("bulletss.png"));
            playerRedBulletImage = ImageIO.read(new File("Rbullets.png"));
            alienBulletImage = ImageIO.read(new File("ebullets.png"));
            redBulletImage = ImageIO.read(new File("redbullet.png"));
            bossImage = ImageIO.read(new File("boss.png"));
        } catch (Exception ex) {
            System.err.println("Gagal memuat gambar: " + ex.getMessage());
        }

        // Jalankan BGM Techno otomatis saat masuk Menu Utama
        playBackgroundMusic("bgm_techno.wav");

        for (int i = 0; i < 100; i++) {
            int starX = random.nextInt(WIDTH);
            int starY = random.nextInt(HEIGHT);
            
            // Mengatur sistem lapisan paralaks menggunakan peluang acak
            int layerChance = random.nextInt(100);
            int speed, size;
            
            if (layerChance < 50) {
                // LAPISAN 1: Jauh Sekali (Sangat lambat, ukuran kecil) - Peluang 50%
                speed = 1;
                size = 1;
            } else if (layerChance < 85) {
                // LAPISAN 2: Jarak Menengah (Kecepatan sedang, ukuran medium) - Peluang 35%
                speed = 2;
                size = 2;
            } else {
                // LAPISAN 3: Jarak Dekat (Bergerak cepat, ukuran besar) - Peluang 15%
                speed = 4;
                size = 3;
            }
            
            stars.add(new Star(starX, starY, speed, size));
        }
    

        timer = new Timer(16, this);
        timer.start();
    }

    // 1. Method untuk memutar BGM secara terus menerus (Looping)
    private void playBackgroundMusic(String filename) {
        try {
            File musicPath = new File(filename);
            if (musicPath.exists()) {
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicPath);
                bgmClip = AudioSystem.getClip();
                bgmClip.open(audioInput);
                bgmClip.loop(Clip.LOOP_CONTINUOUSLY); // Mengatur agar lagu mengulang otomatis
                bgmClip.start();
            } else {
                System.err.println("File BGM tidak ditemukan: " + filename);
            }
        } catch (Exception ex) {
            System.err.println("Gagal memutar BGM: " + ex.getMessage());
        }
    }

    // 2. Method untuk menghentikan BGM (misal saat Game Over jika ingin hening atau ganti lagu)
    private void stopBackgroundMusic() {
        if (bgmClip != null && bgmClip.isRunning()) {
            bgmClip.stop();
        }
    }

    // 3. Method khusus SFX (One-shot sound effect). Berjalan asinkronus agar tidak membuat game patah-patah
    private void playSoundEffect(String filename) {
        new Thread(() -> {
            try {
                File soundPath = new File(filename);
                if (soundPath.exists()) {
                    AudioInputStream audioInput = AudioSystem.getAudioInputStream(soundPath);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioInput);
                    clip.start();
                    
                    // Listener untuk menutup resource clip setelah audio selesai diputar agar tidak memakan memori RAM
                    clip.addLineListener(event -> {
                        if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });
                }
            } catch (Exception ex) {
                System.err.println("Gagal memutar SFX: " + ex.getMessage());
            }
        }).start();
    }

    // --- GAME LOOP (UPDATE & RENDERING) ---
    @Override
    public void actionPerformed(ActionEvent e) {
        updateStars();


        if (gameState == STATE_GAMEPLAY) {
            updateParticles();
            updateFloatingTexts();  

            if (playerHp > 0) {
                updatePlayer();
                updateBullets();
                updateItemDrops();

                if (shakeDuration > 0) {
                    shakeDuration--;
                }

                if (score >= nextBossScore && !bossActive && bossHp == 0) {
                    triggerBossSpawn();
                    bossActive = true;
                    nextBossScore += 5000;
                }
                if (bossActive) {
                    updateBoss();
                } else {
                    updateAliens();
                    spawnAliens();
                }
                checkCollisions();
            } else {
                gameState = STATE_GAMEOVER;

                if (score > highScore) {
                    highScore = score; // Update memori lokal
                    saveHighScore();   // Tulis rekor baru ke dalam file teks (highscore.txt)
                }
            }
        }
        
        repaint();
    }

    private void triggerBossSpawn() {
        System.out.println("Peringatan: Boss Datang!");
        bossActive = true;
        bossHp = BOSS_MAX_HP;
        bossX = WIDTH / 2 - 100;
        bossY = -120; 
        aliens.clear(); 
    }

    private void updatePlayer() {
        if (up && playerY > 0) playerY -= playerSpeed;
        if (down && playerY < HEIGHT - 50) playerY += playerSpeed;
        if (left && playerX > 0) playerX -= playerSpeed;
        if (right && playerX < WIDTH - 40) playerX += playerSpeed;
        if (spawnCounter % 2 == 0) {


        int trailX = playerX + 18; 
        int trailY = playerY + 70; 

        // Warna partikel disesuaikan dengan polaritas pesawat agar terlihat keren
        Color trailColor = isBluePolarity ? new Color(0, 150, 255, 180) : new Color(255, 50, 50, 180);
        
        // Menambahkan partikel ke dalam ArrayList particles yang sudah ada
        particles.add(new Particle(trailX, trailY, trailColor));
        }

        if (shootCooldown > 0) {
        shootCooldown--;
        }

        if (shootCooldown == 0) {
            switch (weaponLevel) {
                case 2:
                    // Level 2: Double Shot (Dua peluru sejajar di sisi kiri dan kanan pesawat)
                    playerBullets.add(new Bullet(playerX + -10, playerY, isBluePolarity));
                    playerBullets.add(new Bullet(playerX + 35, playerY, isBluePolarity));
                    break;
                    
                case 3:
                    // Level 3: Spread Shot (Tiga peluru: lurus tengah, serong kiri, serong kanan)
                    // Catatan: Agar peluru bisa bergerak miring, idealnya kelas Bullet memiliki variabel kecepatan horizontal (speedX).
                    // Namun jika struktur Bullet Anda saat ini hanya melaju lurus ke atas, kita buat 3 tembakan sejajar melebar:
                    playerBullets.add(new Bullet(playerX - 10, playerY + 10, isBluePolarity)); // Kiri
                    playerBullets.add(new Bullet(playerX + 12, playerY, isBluePolarity));      // Tengah
                    playerBullets.add(new Bullet(playerX + 35 , playerY + 10, isBluePolarity)); // Kanan
                    break;
                    
                case 1:
                default:
                    // Level 1: Single Shot standar (Satu peluru di tengah)
                    playerBullets.add(new Bullet(playerX + 12, playerY, isBluePolarity));
                    break;
            }
        // memicu suara laser tipis setiap menembak otomatis
        playSoundEffect("sfx_laser.wav");

        shootCooldown = SHOOT_DELAY;
        }
        }

    private void updateBoss() {
        if (bossY < 60) {
            bossY += 2;
        } else {
            bossX += bossSpeedX;
            if (bossX <= 10 || bossX >= WIDTH - 210) {
                bossSpeedX = -bossSpeedX;
            }

            bossShootCounter++;
            boolean isPhase2 = bossHp < (BOSS_MAX_HP / 2);
            int shootInterval = isPhase2 ? 15 : 25; 

            if (bossShootCounter % shootInterval == 0) {
                boolean c1 = random.nextBoolean();
                boolean c2 = random.nextBoolean();
                alienBullets.add(new AlienBullet(bossX + 30, bossY + 100, c1));
                alienBullets.add(new AlienBullet(bossX + 170, bossY + 100, c2));
                
                if (isPhase2) {
                    AlienBullet cross1 = new AlienBullet(bossX + 100, bossY + 100, true);
                    cross1.speed = 6;
                    alienBullets.add(cross1);
                }
            }

            if (bossShootCounter % 80 == 0) {
                alienBullets.add(new AlienBullet(bossX + 100, bossY + 110, true));
                alienBullets.add(new AlienBullet(bossX + 80, bossY + 110, false));
                alienBullets.add(new AlienBullet(bossX + 140, bossY + 110, false));
            }
        }
    }

    private void updateStars() {
        for (int i = 0; i < stars.size(); i++) {
            Star s = stars.get(i);
            s.y += s.speed;
            if (s.y > HEIGHT) {
                s.y = 0;
                s.x = random.nextInt(WIDTH);
            }
        }
    }

    private void updateParticles() {
        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            p.x += p.dx;
            p.y += p.dy;
            p.lifetime--;
            if (p.lifetime <= 0) {
                particles.remove(i--);
            }
        }
    }
    private void updateFloatingTexts() {
        for (int i = 0; i < floatingTexts.size(); i++) {
            FloatingText ft = floatingTexts.get(i);
            ft.lifetime--; // Kurangi sisa waktu hidup teks
            ft.y -= 1.2;   // Gerakkan teks perlahan ke atas

            // Jika lifetime habis, hapus dari daftar agar tidak membebani memori
            if (ft.lifetime <= 0) {
                floatingTexts.remove(i--);
            }
        }
    }
    private void updateBullets() {
        for (int i = 0; i < playerBullets.size(); i++) {
            Bullet b = playerBullets.get(i);
            b.y -= 10;
            if (b.y < 0) playerBullets.remove(i--);
        }

        for (int i = 0; i < alienBullets.size(); i++) {
            AlienBullet ab = alienBullets.get(i);
            ab.y += ab.speed;
            if (ab.y > HEIGHT) alienBullets.remove(i--);
        }
    }

    private void updateAliens() {
        int extraSpeed = score / 2000; // Penambahan kesulitan global dari skor dasar
        
        for (int i = 0; i < aliens.size(); i++) {
            Alien a = aliens.get(i);
            
            // Logika pergerakan berdasarkan Tipe Alien
            switch (a.type) {
                case Alien.TYPE_NORMAL:
                    // Gerakan sinusoide standar (kode lama Anda)
                    a.y += (a.speedY + extraSpeed);
                    a.x += Math.sin(a.y * 0.05) * 3;
                    break;
                    
                case Alien.TYPE_TANKER:
                    // Bergerak lurus ke bawah dengan sangat lambat
                    a.y += a.speedY; 
                    break;
                    
                case Alien.TYPE_SCOUT:
                    // Bergerak lurus ke bawah dengan sangat cepat tanpa meliuk
                    a.y += (a.speedY + extraSpeed);
                    break;
                    
                case Alien.TYPE_KAMIKAZE:
                    // Jatuh ke bawah sekaligus mengejar posisi koordinat X milik player
                    a.y += (a.speedY + extraSpeed);
                    if (a.x < playerX + 20) {
                        a.x += 2; // Mengejar ke kanan
                    } else if (a.x > playerX + 20) {
                        a.x -= 2; // Mengejar ke kiri
                    }
                    break;
            }

            // Peluang menembak (Alien Tanker menembak lebih sering, Scout tidak menembak)
            int shootChance = (a.type == Alien.TYPE_TANKER) ? 30 : 60; 
            if (a.type != Alien.TYPE_SCOUT && random.nextInt(shootChance) == 1) {
                boolean bulletColor = random.nextBoolean();
                alienBullets.add(new AlienBullet(a.x + 18, a.y + 40, bulletColor));
            }

            if (a.y > HEIGHT) aliens.remove(i--);
        }
    }
    private void updateItemDrops() {
        for (int i = 0; i < itemDrops.size(); i++) {
            ItemDrop item = itemDrops.get(i);
            item.y += item.speed; // Item bergerak ke bawah

            // Jika item melewati batas bawah layar, hapus dari memori
            if (item.y > HEIGHT) {
                itemDrops.remove(i--);
            }
        }
    }

    private void checkItemCollisions(Rectangle playerRect) {
        for (int i = 0; i < itemDrops.size(); i++) {
            ItemDrop item = itemDrops.get(i);
            Rectangle itemRect = new Rectangle(item.x, item.y, 20, 20); // Ukuran hitbox item

            if (playerRect.intersects(itemRect)) {
                // Mainkan SFX jika ada (Opsional, pastikan filenya ada di folder project)
                // playSoundEffect("sfx_pickup.wav"); 

                if (item.type == ItemDrop.TYPE_HEAL) {
                    playerHp += 25; // Menambah 25 HP
                    if (playerHp > 100) playerHp = 100; // Batasi maksimal 100 HP
                } else if (item.type == ItemDrop.TYPE_ENERGY) {
                    energyBar += 20; // Menambah 20 POW
                    if (energyBar > MAX_ENERGY) energyBar = MAX_ENERGY;
                }else if (item.type == ItemDrop.TYPE_POWERUP) {
                 // Menaikkan level senjata hingga batas maksimum (Level 3)
                 if (weaponLevel < MAX_WEAPON_LEVEL) {
                     weaponLevel++;
                }else {
                    score += 500; // Bonus jika level senjata sudah maksimal
                }
            }

                // Beri partikel bonus saat mengambil item agar terlihat memuaskan
                Color effectColor = (item.type == ItemDrop.TYPE_HEAL) ? Color.GREEN : Color.ORANGE;
                for (int k = 0; k < 15; k++) {
                    particles.add(new Particle(item.x + 10, item.y + 10, effectColor));
                }

                itemDrops.remove(i--); // Hapus item setelah diambil
            }
        }
    }

    private void spawnAliens() {
        spawnCounter++;
        int currentSpawnInterval = Math.max(20, 60 - (score / 1000) * 5);
        if (spawnCounter % currentSpawnInterval == 0) {
            int spawnX = random.nextInt(WIDTH - 50);
            
            // Mengacak tipe alien berdasarkan peluang matematika sederhana
            int chance = random.nextInt(100);
            int alienType;
            
            if (chance < 50) {
                alienType = Alien.TYPE_NORMAL;
            } else if (chance < 70) {
                alienType = Alien.TYPE_TANKER;
            } else if (chance < 90) {
                alienType = Alien.TYPE_SCOUT;
            } else {
                alienType = Alien.TYPE_KAMIKAZE;
            }
            
            // Memasukkan alien baru dengan parameter tipenya
            aliens.add(new Alien(spawnX, -40, alienType));
        }
    }

    private void checkCollisions() {
        // Rectangle playerRect = new Rectangle(playerX, playerY, 80, 100);
        // Ukuran hitbox tengah (bisa disesuaikan, misalnya 16x16 pixel)
        int hitboxWidth = 30;
        int hitboxHeight = 30;

        // Menghitung posisi koordinat agar tepat berada di tengah aset pesawat (lebar 80, tinggi 100)
        int hitboxX = playerX + (40 / 2) - (hitboxWidth / 2);
        int hitboxY = playerY + (90 / 2) - (hitboxHeight / 2);

        Rectangle playerRect = new Rectangle(hitboxX, hitboxY, hitboxWidth, hitboxHeight);
        checkItemCollisions(playerRect);

        for (int i = 0; i < alienBullets.size(); i++) {
            AlienBullet ab = alienBullets.get(i);
            Rectangle bulletRect = new Rectangle(ab.x, ab.y, 16, 16);

            if (playerRect.intersects(bulletRect)) {
                if (this.isBluePolarity == ab.isBlue) {
                    energyBar += 10;
                    if (energyBar > MAX_ENERGY) energyBar = MAX_ENERGY;
                    score += 10;
                    comboCount++; // Tambahkan hitungan combo
                     int bonusScore = 10 * comboCount; // Pengganda skor dasar dikali jumlah combo
                     score += bonusScore;
                     Color textColor = isBluePolarity ? Color.CYAN : Color.RED;
                     floatingTexts.add(new FloatingText(playerX + 10, playerY - 15,
                         "COMBO X" +
                          comboCount +
                           " (+" + bonusScore + ")", textColor, 40));
                } else {
                    playerHp -= 15;
                    comboCount = 0;
                    if (weaponLevel > 1) {
                         weaponLevel--;
                         floatingTexts.add(new FloatingText(playerX + 5, playerY - 25, "WEAPON DOWN!", Color.ORANGE, 40));
                     }
                    if (playerHp <= 0) {
                        playerHp = 0;
                        shakeDuration = 0; 
                        shakeIntensity = 0;
                    } else {
                        shakeDuration = 8; 
                        shakeIntensity = 5;
                    }
                }
                alienBullets.remove(i--);
            }
        }

        for (int i = 0; i < playerBullets.size(); i++) {
            Bullet b = playerBullets.get(i);
            Rectangle bRect = new Rectangle(b.x, b.y, 16, 16);
            boolean bulletRemoved = false;

            if (bossActive) {
                Rectangle bossRect = new Rectangle(bossX, bossY, 200, 100);
                if (bRect.intersects(bossRect)) {
                    bossHp -= 10; 
                    playerBullets.remove(i--);
                    bulletRemoved = true;
                    particles.add(new Particle(b.x, b.y, random.nextBoolean() ? Color.ORANGE : Color.RED));

                    if (bossHp <= 0) {
                        bossActive = false;
                        score += 2500; 
                        // Memicu ledakan heavy bass saat Boss hancur
                        playSoundEffect("sfx_explosion.wav");
                        bossActive = false;

                        for (int k = 0; k < 60; k++) {
                            particles.add(new Particle(bossX + 100, bossY + 50, new Color(255, 50, 0)));
                            particles.add(new Particle(bossX + 100, bossY + 50, Color.YELLOW));
                        }
                        alienBullets.clear(); 
                    }
                }
            } else {
                for (int j = 0; j < aliens.size(); j++) {
                    Alien a = aliens.get(j);
                    Rectangle aRect = new Rectangle(a.x, a.y, 45, 45);

                    if (bRect.intersects(aRect)) {
                        // Efek partikel setiap kali peluru mengenai tubuh alien
                        for (int k = 0; k < 5; k++) {
                            particles.add(new Particle(b.x, b.y, new Color(150, 0, 255)));
                        }   
                        a.hp -= 10;
                        playerBullets.remove(i--);
                        bulletRemoved = true;

                        if (a.hp <= 0) {
                            // Partikel ledakan besar saat mati
                            for (int k = 0; k < 10; k++) {
                                particles.add(new Particle(a.x + 22, a.y + 22, new Color(200, 50, 255)));
                            }
                            aliens.remove(j);
                            if (a.type == Alien.TYPE_TANKER) score += 50;
                            else if (a.type == Alien.TYPE_KAMIKAZE) score += 30;
                            else score += 20;

                            // Logika Item Drop
                            if (random.nextInt(100) < 40) { 
                            int chance = random.nextInt(100); // Acak angka baru 0-99 untuk menentukan jenis item
                            int itemType;
                            if (chance < 40) {
                                // Peluang 40% (angka 0 s.d 39): Menjadi Item HEAL (Darah)
                                itemType = ItemDrop.TYPE_HEAL;
                            } else if (chance < 75) {
                                // Peluang 35% (angka 40 s.d 74): Menjadi Item ENERGY (Skill Ultimate)
                                itemType = ItemDrop.TYPE_ENERGY;
                            } else {
                                // Peluang 25% (angka 75 s.d 99): Menjadi Item POWERUP (Senjata)
                                itemType = ItemDrop.TYPE_POWERUP; 
                            }
                            itemDrops.add(new ItemDrop(a.x + 15, a.y + 15, itemType));
                    }
                        }
                        break;
                    }
                    if (playerRect.intersects(aRect)) {
                        playerHp -= 20; // Mengurangi HP player karena tabrakan keras
                        comboCount = 0;
                        if (weaponLevel > 1) {
                        weaponLevel--;
                        floatingTexts.add(new FloatingText(playerX + 5, playerY - 25, "WEAPON DOWN!", Color.ORANGE, 40));
                    }
                        
                        // Efek guncangan layar agar benturan terasa dramatis
                        shakeDuration = 8; 
                        shakeIntensity = 6;
                        
                        // Memicu efek suara ledakan benturan (jika file audio tersedia)
                        playSoundEffect("sfx_explosion.wav"); 
                        
                        // Hasilkan partikel hancurnya alien di posisi benturan
                        for (int k = 0; k < 15; k++) {
                            particles.add(new Particle(a.x + 22, a.y + 22, Color.ORANGE));
                        }
                        
                        // Hapus alien yang ditabrak dari layar
                        aliens.remove(j); 
                        j--; // Sesuaikan index loop agar tidak melompati alien berikutnya
                        
                        // Validasi jika HP Player habis akibat tabrakan
                        if (playerHp < 0) playerHp = 0; 
                    }
                    // ------------------------------------------
                }
            }
            if (bulletRemoved) continue;
        }
    }

    // --- VISUAL RENDERING ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (gameState == STATE_GAMEPLAY && shakeDuration > 0) {
            int offsetX = random.nextInt(shakeIntensity * 2) - shakeIntensity;
            int offsetY = random.nextInt(shakeIntensity * 2) - shakeIntensity;
            g2d.translate(offsetX, offsetY);
        }

        g2d.setColor(Color.WHITE);
        for (Star s : stars) {
            // Memberikan efek pencahayaan berbeda berdasarkan kedalaman lapisan
            if (s.size == 1) {
                g2d.setColor(new Color(150, 150, 150)); // Abu-abu redup untuk bintang jauh
            } else if (s.size == 2) {
                g2d.setColor(new Color(200, 200, 255)); // Putih kebiruan sedang
            } else {
                g2d.setColor(Color.WHITE); // Putih terang bersinar untuk bintang dekat
            }
            
            // Menggambar bintang sesuai koordinat dan ukuran lapisannya
            g2d.fillRect(s.x, s.y, s.size, s.size);
        }

        for (Particle p : particles) {
            g2d.setColor(p.color);
            g2d.fillRect((int)p.x, (int)p.y, 4, 4);
        }

        if (gameState == STATE_MENU) {
            drawMenuScreen(g2d);
        } else if (gameState == STATE_GAMEPLAY) {
            drawGameplayScreen(g2d);
        } else if (gameState == STATE_PAUSE) {
            drawGameplayScreen(g2d); 
            drawPauseScreen(g2d);
        } else if (gameState == STATE_GAMEOVER) {
            drawGameOverScreen(g2d);
            
        }
    }

    private void drawMenuScreen(Graphics2D g2d) {
        for (Star s : stars) {
            g2d.setColor(s.size == 1 ? new Color(120, 120, 120) : s.size == 2 ? new Color(180, 180, 220) : Color.WHITE);
            g2d.fillRect(s.x, s.y, s.size, s.size);
        }
        g2d.setColor(new Color(0, 200, 255));
        g2d.setFont(new Font("Courier New", Font.BOLD, 50));
        g2d.drawString("COSMIC VANGUARD", WIDTH / 2 - 210, HEIGHT / 2 - 120);
        
        g2d.setColor(new Color(255, 0, 100));
        g2d.setFont(new Font("Arial", Font.ITALIC | Font.BOLD, 20));
        g2d.drawString("Neo-Earth Prototype", WIDTH / 2 - 95, HEIGHT / 2 - 85);

        g2d.setColor(Color.YELLOW);
        g2d.drawString("HIGH SCORE: " + highScore, WIDTH / 2 - 80, HEIGHT / 2 - 40);
        g2d.setFont(new Font("Courier New", Font.BOLD, 22));
        g2d.drawString("TEKAN [ENTER] UNTUK MEMULAI GAME", WIDTH / 2 - 200, HEIGHT / 2 - 10);

        // Perbaikan perataan teks Panduan Kontrol agar rapi & rata kiri
        g2d.setColor(new Color(255, 255, 255, 40));
        g2d.fillRoundRect(WIDTH / 2 - 250, HEIGHT / 2 + 40, 500, 180, 15, 15);
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(WIDTH / 2 - 250, HEIGHT / 2 + 40, 500, 180, 15, 15);

        g2d.setFont(new Font("Courier New", Font.BOLD, 16));
        g2d.setColor(new Color(0, 255, 150));
        g2d.drawString("PANDUAN KONTROL & MEKANIK:", WIDTH / 2 - 230, HEIGHT / 2 + 70);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.setColor(Color.WHITE);
        g2d.drawString("• Bergerak          : W, A, S, D  atau  Tombol Panah", WIDTH / 2 - 230, HEIGHT / 2 + 100);
        g2d.drawString("• Ganti Mode       : [SPACEBAR] (Plasma Biru / Anti-M Merah)", WIDTH / 2 - 230, HEIGHT / 2 + 120);
        g2d.drawString("• Ultimate Skill    : [X] (Saat Energi POW 100%)", WIDTH / 2 - 230, HEIGHT / 2 + 140);
        g2d.drawString("• Pause / Jeda Game : [P] atau [ESC]", WIDTH / 2 - 230, HEIGHT / 2 + 160);
        g2d.drawString("• Sistem Shield    : Serap peluru sewarna untuk isi POW & Skor!", WIDTH / 2 - 230, HEIGHT / 2 + 180);
        g2d.drawString("                         Ketika terkena peluru warna berbeda mengurangi HP.", WIDTH / 2 - 213, HEIGHT / 2 + 195);
    }   

    private void drawGameplayScreen(Graphics2D g2d) {
        Color polarityColor = isBluePolarity ? new Color(0, 200, 255) : new Color(255, 0, 100);
        g2d.setColor(polarityColor);
        // g2d.setStroke(new BasicStroke(4));
        // g2d.drawOval(playerX + 15, playerY + 25, 50, 50);

        BufferedImage currentShipImage = isBluePolarity ? playerImage : playerRedImage;

        if (currentShipImage != null) {
            int imageWidth = 80;  
            int imageHeight = 100;
            int drawX = playerX - (imageWidth - 40) / 2;
            int drawY = playerY - (imageHeight - 40) / 2;
            g2d.drawImage(currentShipImage, drawX, drawY, imageWidth, imageHeight, null);
        } else {
            g2d.setColor(isBluePolarity ? Color.WHITE : Color.RED);
            int[] xPts = {playerX + 20, playerX, playerX + 40};
            int[] yPts = {playerY, playerY + 40, playerY + 40};
            g2d.fillPolygon(xPts, yPts, 3);
        } 
        
        if (bossActive) {
            if (bossImage != null) {
                g2d.drawImage(bossImage, bossX, bossY, 200, 110, null);
            } else {
                g2d.setColor(new Color(255, 0, 50));
                g2d.fillRect(bossX, bossY, 200, 100);
            }
            g2d.setColor(new Color(180, 0, 255, 100));
            g2d.setStroke(new BasicStroke(5));
            g2d.drawRoundRect(bossX - 5, bossY - 5, 210, 120, 20, 20);
        }
        for (FloatingText ft : floatingTexts) {
            int alpha = ft.getAlpha();
            if (alpha < 0) alpha = 0;
            if (alpha > 255) alpha = 255;

            // Buat warna dengan efek memudar (Fade Out)
            Color fadeColor = new Color(ft.color.getRed(), ft.color.getGreen(), ft.color.getBlue(), alpha);
            g2d.setColor(fadeColor);
            
            // Atur ukuran dan jenis font teks melayang
            g2d.setFont(new Font("Courier New", Font.BOLD, 16));
            g2d.drawString(ft.text, (int)ft.x, (int)ft.y);
        }

        for (Bullet b : playerBullets) {
            if (b.isBlue) {
                if (bulletImage != null) {
                    g2d.drawImage(bulletImage, b.x, b.y, 16, 16, null);
                } else {
                    g2d.setColor(Color.YELLOW);
                    g2d.fillRect(b.x, b.y, 6, 15);
                }
            } else {
                if (playerRedBulletImage != null) {
                    g2d.drawImage(playerRedBulletImage, b.x, b.y - 8, 12, 24, null);
                } else {
                    g2d.setColor(Color.RED);
                    g2d.fillRect(b.x, b.y, 6, 15);
                }
            }
        }

        if (!bossActive) {
            for (Alien a : aliens) {
                if (alienImage != null) {
                    g2d.drawImage(alienImage, a.x, a.y, 45, 45, null);
                } else {
                    g2d.setColor(new Color(150, 0, 255));
                    g2d.fillRect(a.x, a.y, 35, 35);
                }
            }
        }
        
        for (AlienBullet ab : alienBullets) {
            if (alienBulletImage != null && redBulletImage != null) {
                if (ab.isBlue) {
                    g2d.setColor(new Color(0, 200, 255, 150)); 
                } else {
                    g2d.setColor(new Color(255, 0, 70, 150));   
                }
                g2d.fillOval(ab.x - 3, ab.y - 3, 22, 22);

                if (ab.isBlue) {
                    g2d.drawImage(alienBulletImage, ab.x, ab.y, 16, 16, null);
                } else {
                    g2d.drawImage(redBulletImage, ab.x, ab.y, 16, 16, null);
                }
            } else {
                if (ab.isBlue) {
                    g2d.setColor(new Color(0, 230, 255));
                } else {
                    g2d.setColor(new Color(255, 0, 100));
                }
                g2d.fillOval(ab.x, ab.y, 12, 12);
            }
        }

        for (ItemDrop item : itemDrops) {
            if (item.type == ItemDrop.TYPE_HEAL) {
                // Efek Glow Hijau untuk HP
                g2d.setColor(new Color(0, 255, 100, 80));
                g2d.fillOval(item.x - 4, item.y - 4, 28, 28);
                
                g2d.setColor(Color.GREEN);
                g2d.fillOval(item.x, item.y, 20, 20);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.BOLD, 16));
                g2d.drawString("+", item.x + 5, item.y + 16); // Simbol Plus
            } else if (item.type == ItemDrop.TYPE_ENERGY) {
                // Efek Glow Oranye untuk POW
                g2d.setColor(new Color(255, 150, 0, 80));
                g2d.fillOval(item.x - 4, item.y - 4, 28, 28);
                
                g2d.setColor(Color.ORANGE);
                g2d.fillOval(item.x, item.y, 20, 20);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.BOLD, 12));
                g2d.drawString("P", item.x + 6, item.y + 15); // Simbol Power
            }
            else if (item.type == ItemDrop.TYPE_POWERUP) {
                g2d.setColor(Color.ORANGE); // Berikan warna orange terang atau kuning berkilau
                
                // Menggambar bentuk berlian/diamond khusus untuk item upgrade agar menonjol
                int[] xPoints = {item.x + 10, item.x + 20, item.x + 10, item.x};
                int[] yPoints = {item.y, item.y + 10, item.y + 20, item.y + 10};
                g2d.fillPolygon(xPoints, yPoints, 4);
                
                // Efek border putih luar agar berkilau
                g2d.setColor(Color.WHITE);
                g2d.drawPolygon(xPoints, yPoints, 4);
            }
        }

        Font retroFontSmall = new Font("Courier New", Font.BOLD, 14);
        Font retroFontLarge = new Font("Courier New", Font.BOLD, 16);
        g2d.setFont(retroFontSmall);

        // HP BAR
        int hpBarX = 20;
        int hpBarY = 20;
        int barWidth = 200;
        int barHeight = 20;

        g2d.setColor(Color.BLACK);
        g2d.fillRect(hpBarX, hpBarY, barWidth, barHeight);

        if (playerHp > 0) {
            int currentHpWidth = (int) ((playerHp / 100.0) * barWidth);
            g2d.setColor(new Color(0, 215, 0));
            g2d.fillRect(hpBarX, hpBarY, currentHpWidth, barHeight);
            g2d.setColor(new Color(150, 255, 150));
            g2d.fillRect(hpBarX, hpBarY, currentHpWidth, 4);
            g2d.setColor(new Color(0, 100, 0));
            g2d.fillRect(hpBarX, hpBarY + barHeight - 4, currentHpWidth, 4);
            
            g2d.setColor(Color.BLACK);
            for (int i = hpBarX + 20; i < hpBarX + currentHpWidth; i += 20) {
                g2d.fillRect(i, hpBarY, 2, barHeight);
            }
        }
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRect(hpBarX, hpBarY, barWidth, barHeight);
        g2d.drawString("HP: " + playerHp + "/100", hpBarX + 5, hpBarY - 6);

        // ENERGY BAR
        int energyBarY = 65;
        g2d.setColor(Color.BLACK);
        g2d.fillRect(hpBarX, energyBarY, barWidth, barHeight);

        if (energyBar > 0) {
            int currentEnergyWidth = (int) ((energyBar / 100.0) * barWidth);
            g2d.setColor(new Color(255, 140, 0));
            g2d.fillRect(hpBarX, energyBarY, currentEnergyWidth, barHeight);
            g2d.setColor(new Color(255, 200, 100));
            g2d.fillRect(hpBarX, energyBarY, currentEnergyWidth, 4);
            g2d.setColor(new Color(139, 69, 0));
            g2d.fillRect(hpBarX, energyBarY + barHeight - 4, currentEnergyWidth, 4);
            g2d.setColor(Color.BLACK);

            for (int i = hpBarX + 20; i < hpBarX + currentEnergyWidth; i += 20) {
                g2d.fillRect(i, energyBarY, 2, barHeight);
            }
        }
        g2d.setColor(Color.WHITE);
        g2d.drawRect(hpBarX, energyBarY, barWidth, barHeight);
        g2d.drawString("POW: " + energyBar + "%", hpBarX + 5, energyBarY - 6);

        if (energyBar >= MAX_ENERGY) {
            g2d.setColor(Color.YELLOW);
            g2d.drawString("[TEKAN X (ULTIMATE)]", hpBarX + barWidth + 15, energyBarY + 15);
        }

        if (bossActive) {
            int bossBarW = 300;
            int bossBarX = (WIDTH / 2) - (bossBarW / 2);
            int bossBarY = 25;

            g2d.setColor(Color.BLACK);
            g2d.fillRect(bossBarX, bossBarY, bossBarW, 16);

            int currentBossWidth = (int) ((double) bossHp / BOSS_MAX_HP * bossBarW);
            g2d.setColor(new Color(160, 0, 240));
            g2d.fillRect(bossBarX, bossBarY, currentBossWidth, 16);
            g2d.setColor(new Color(220, 130, 255)); 
            g2d.fillRect(bossBarX, bossBarY, currentBossWidth, 3);

            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(bossBarX, bossBarY, bossBarW, 16);

            g2d.setFont(new Font("Courier New", Font.BOLD, 12));
            g2d.drawString("BOSS VANGUARD PROTO", bossBarX, bossBarY - 6);
        }

        g2d.setFont(retroFontSmall);
        g2d.setColor(polarityColor);
        g2d.drawString("SHIELD: " + (isBluePolarity ? "PLASMA" : "ANTI-M"), hpBarX, energyBarY + barHeight + 20);

        g2d.setFont(retroFontLarge);
        g2d.setColor(Color.WHITE);
        String formattedScore = String.format("%06d", score);
        g2d.drawString("SCORE: " + formattedScore, WIDTH - 180, 35);
    }

    private void drawGameOverScreen(Graphics2D g2d) {
        for (Star s : stars) {
            g2d.setColor(s.size == 1 ? new Color(120, 120, 120) : s.size == 2 ? new Color(180, 180, 220) : Color.WHITE);
            g2d.fillRect(s.x, s.y, s.size, s.size);
        }
        g2d.setColor(Color.RED);
        g2d.setFont(new Font("Arial", Font.BOLD, 50));
        g2d.drawString("GAME OVER", WIDTH / 2 - 145, HEIGHT / 2 - 20);

        g2d.setFont(new Font("Arial", Font.PLAIN, 20));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Skor Akhir: " + score, WIDTH / 2 - 60, HEIGHT / 2 + 30);

        g2d.setFont(new Font("Courier New", Font.BOLD, 16));
        g2d.setColor(Color.YELLOW);
        g2d.drawString("REKOR TERTINGGI: " + highScore, WIDTH / 2 - 95, HEIGHT / 2 + 55);
        g2d.drawString("TEKAN 'R' UNTUK MAIN LAGI", WIDTH / 2 - 120, HEIGHT / 2 + 80);
        g2d.drawString("TEKAN 'M' UNTUK KEMBALI KE MENU", WIDTH / 2 - 140, HEIGHT / 2 + 110);
    }
    private void drawPauseScreen(Graphics2D g2d) {
        // 1. Buat efek overlay gelap transparan menutupi layar gameplay belakang
        g2d.setColor(new Color(0, 0, 0, 150)); // Hitam dengan opasitas 150/255
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // 2. Gambar Kotak Dialog Pause di Tengah Layar
        g2d.setColor(new Color(0, 200, 255, 50));
        g2d.fillRoundRect(WIDTH / 2 - 200, HEIGHT / 2 - 80, 400, 140, 15, 15);
        g2d.setColor(new Color(0, 200, 255));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(WIDTH / 2 - 200, HEIGHT / 2 - 80, 400, 140, 15, 15);

        // 3. Tampilkan Teks GAME PAUSED
        g2d.setFont(new Font("Courier New", Font.BOLD, 36));
        g2d.setColor(Color.YELLOW);
        g2d.drawString("GAME PAUSED", WIDTH / 2 - 115, HEIGHT / 2 - 20);

        // 4. Tampilkan Teks Petunjuk untuk Melanjutkan
        g2d.setFont(new Font("Courier New", Font.BOLD, 14));
        g2d.setColor(Color.WHITE);
        g2d.drawString("TEKAN 'P' ATAU 'ESC' UNTUK MELANJUTKAN", WIDTH / 2 - 160, HEIGHT / 2 + 25);
    }

    // --- KONTROL KEYBOARD ---
    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (gameState == STATE_MENU) {
            if (key == KeyEvent.VK_ENTER) {
                restartGame(); 
                gameState = STATE_GAMEPLAY;
            }
        }
        else if (gameState == STATE_GAMEPLAY) {
            if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) up = true;
            if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) down = true;
            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) left = true;
            if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) right = true;

            if (key == KeyEvent.VK_SPACE) {
                isBluePolarity = !isBluePolarity;
            }

            if (key == KeyEvent.VK_X && energyBar >= MAX_ENERGY) {
                triggerSuperNovaUltimate();
            }
            if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
                gameState = STATE_PAUSE;
            }
        } 
        else if (gameState == STATE_PAUSE) {
            // Kembali bermain jika menekan P atau ESC lagi saat sedang dijeda
            if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
                gameState = STATE_GAMEPLAY;
                
                // Reset status tombol bergerak agar pesawat tidak terus meluncur otomatis akibat input yang tertahan
                up = down = left = right = false; 
            }
        } 
        else if (gameState == STATE_GAMEOVER) {
            if (key == KeyEvent.VK_R) {
                restartGame();
                gameState = STATE_GAMEPLAY;
            }
            if (key == KeyEvent.VK_M) {
                gameState = STATE_MENU;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) up = false;
        if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) down = false;
        if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) left = false;
        if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) right = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    private void triggerSuperNovaUltimate() {
        shakeDuration = 25;   
        shakeIntensity = 12;

        // Memicu ledakan heavy bass saat Ultimate dilepaskan
        playSoundEffect("sfx_explosion.wav");

        if (bossActive) {
            bossHp -= 100; 
            for (int k = 0; k < 30; k++) {
                particles.add(new Particle(bossX + 100, bossY + 50, Color.YELLOW));
            }

            if (bossHp <= 0) {
                bossActive = false;
                score += 2500;
                alienBullets.clear();
                shakeDuration = 30;   
                shakeIntensity = 15;
            }
        } else {
            for (Alien a : aliens) {
                for (int k = 0; k < 5; k++) {
                    particles.add(new Particle(a.x + 22, a.y + 22, Color.YELLOW));
                }
            }
            aliens.clear();
            score += 500;
        }
        alienBullets.clear();
        energyBar = 0;
    }
    private void loadHighScore() {
        try (BufferedReader br = new BufferedReader(new FileReader(HIGHSCORE_FILE))) {
            String line = br.readLine();
            if (line != null) {
                highScore = Integer.parseInt(line.trim());
            }
        } catch (IOException | NumberFormatException e) {
            // Jika file belum ada (baru pertama kali dimainkan), set default ke 0
            highScore = 0;
            System.out.println("Belum ada file high score sebelumnya. Di-set ke 0.");
        }
    }
    private void saveHighScore() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HIGHSCORE_FILE))) {
            bw.write(String.valueOf(highScore));
            System.out.println("High score berhasil disimpan: " + highScore);
        } catch (IOException e) {
            System.out.println("Gagal menyimpan high score: " + e.getMessage());
        }
    }

    private void restartGame() {
        playerX = 375;
        playerY = 500;
        playerHp = 100;

        energyBar = 0;
        score = 0;
        weaponLevel = 1;
        spawnCounter = 0;
        shootCooldown = 0;
        comboCount = 0;

        isBluePolarity = true;
        bossActive = false;

        bossHp = 0;
        bossShootCounter = 0;

        floatingTexts.clear();
        playerBullets.clear();
        alienBullets.clear();
        aliens.clear();
        particles.clear();
        itemDrops.clear();
        up = down = left = right = false;
    }   

    public static void main(String[] args) {
        JFrame frame = new JFrame("Cosmic Vanguard: Neo-Earth - Prototype");
        CosmicVanguard gamePanel = new CosmicVanguard();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(gamePanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    // --- SUB-CLASSES ---
    class Bullet {
        int x, y;
        boolean isBlue; 
        Bullet(int x, int y, boolean isBlue) { 
            this.x = x; 
            this.y = y; 
            this.isBlue = isBlue;
        }
    }

    class AlienBullet {
        int x, y;
        int speed = 5;
        boolean isBlue;
        AlienBullet(int x, int y, boolean isBlue) {
            this.x = x;
            this.y = y;
            this.isBlue = isBlue;
        }
    }

    class Alien {
        int x, y;
        int type;         // 0 = Normal, 1 = Tanker, 2 = Scout, 3 = Kamikaze
        int hp;           // Nyawa alien
        double speedX;    // Kecepatan horizontal (opsional untuk pola khusus)
        double speedY;    // Kecepatan vertikal

        // Definisi Konstanta Tipe Alien
        static final int TYPE_NORMAL = 0;
        static final int TYPE_TANKER = 1;
        static final int TYPE_SCOUT = 2;
        static final int TYPE_KAMIKAZE = 3;

        Alien(int x, int y, int type) {
            this.x = x;
            this.y = y;
            this.type = type;

            // Inisialisasi status unik berdasarkan tipenya
            switch (type) {
                case TYPE_TANKER:
                    this.hp = 40;        // HP sangat tebal (4x peluru biasa)
                    this.speedY = 1.0;   // Bergerak lambat ke bawah
                    break;
                case TYPE_SCOUT:
                    this.hp = 10;        // HP standar
                    this.speedY = 5.0;   // Bergerak sangat cepat lurus ke bawah
                    break;
                case TYPE_KAMIKAZE:
                    this.hp = 10;        // HP standar
                    this.speedY = 3.0;   // Kecepatan jatuh sedang
                    break;
                case TYPE_NORMAL:
                default:
                    this.hp = 10;        // Alien standar
                    this.speedY = 2.0;
                    break;
            }
        }
    }
    
    class Star {
        int x, y;
        int speed;
        int size; // <-- Tambahkan variabel ukuran bintang

        Star(int x, int y, int speed, int size) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.size = size; // Inisialisasi ukuran bintang
        }
    }
    
    class ItemDrop {
        int x, y;
        int speed = 3;
        int type; // 0 = Heal, 1 = Energy
        static final int TYPE_HEAL = 0;
        static final int TYPE_ENERGY = 1;
        static final int TYPE_POWERUP = 2;

        ItemDrop(int x, int y, int type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    class Particle {
    double x, y, dx, dy;
    int lifetime;
    Color color;

    Particle(int x, int y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
        Random r = new Random();
        
        // dx dibuat kecil agar tidak terlalu menyebar ke samping
        this.dx = (r.nextDouble() - 0.5) * 2; 
        
        // dy dibuat positif (2.0 ke atas) agar partikel "jatuh" ke bawah pesawat
        this.dy = r.nextDouble() * 3 + 2; 
        
        // Lifetime singkat agar jejak tidak terlalu panjang
        this.lifetime = r.nextInt(10) + 30;  
    }
  
    }
    class FloatingText {
    double x, y;
    String text;
    Color color;
    int lifetime; // Durasi teks tampil di layar (dalam hitungan frame)
    int maxLifetime;

    FloatingText(double x, double y, String text, Color color, int lifetime) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.lifetime = lifetime;
        this.maxLifetime = lifetime;
    }

    // Mengembalikan nilai transparansi (Alpha) berbasis sisa lifetime agar teks memudar (fade out)
    int getAlpha() {
        double ratio = (double) lifetime / maxLifetime;
        return (int) (ratio * 255);
    }
}
}
