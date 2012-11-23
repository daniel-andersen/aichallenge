import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MyBot extends Bot {
    private static final Logger logger = Logger.getLogger(MyBot.class.getName());

    private static final int DEFEND_HILL_MAX_DEFENDERS_PER_HILL = 4;
    private static final int DEFEND_HILL_RADIUS = 8;

    private static final int PATHMAP_DISTANCE_LAST_VISITED = 5;
    private static final int PATHMAP_DISTANCE_MY_ANTS = 30;
    private static final int PATHMAP_DISTANCE_AVOID_ENEMY = 3;

    private static final int EXPLORE_SCORE_LAST_VISITED = 15;
    private static final int EXPLORE_SCORE_UNEXPLORED_AREA = 10;
    private static final int EXPLORE_SCORE_ENEMY_HILL = 20;

    private int turn = 0;
    private Ants ants;

    private Set<MyAnt> myAnts;
    private Set<MapElem> myFood;
    private Set<MapElem> myFoodUncovered;
    private Set<MapElem> myHills;
    private Set<MapElem> myEnemyAnts;
    private Set<MapElem> myEnemyHills;
    private Set<MapElem> visibleBorderTiles;

    private boolean collisionMap[][];
    private boolean exploredMap[][];
    private boolean visibleMap[][];
    private boolean visibleBorder[][];
    private int myAntAttackMap[][];
    private int enemyAttackMap[][];
    private int lastVisitedMap[][];
    private MyAnt myAntMap[][];
    private MapElem enemyAntMap[][];

    private PathMap[] pathMapFood = new PathMap[2];
    private PathMap pathMapMyHills;
    private PathMap pathMapEnemies;
    private PathMap pathMapEnemyHills;
    private PathMap pathMapMyAnts;
    private PathMap pathMapExploration;

    private Tile[][] mapTiles;

    public static void main(String[] args) throws IOException {
        FileHandler fh = new FileHandler("outputLog.log");
        fh.setFormatter(new SimpleFormatter());
        logger.addHandler(fh);
        new MyBot().readSystemInput();
    }

    public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2, int attackRadius2, int spawnRadius2) {
        super.setup(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);
        ants = getAnts();
        myAnts = new HashSet<MyAnt>();
        initializePathAlgorithms();
        collisionMap = new boolean[ants.getRows()][ants.getCols()];
        exploredMap = new boolean[ants.getRows()][ants.getCols()];
        visibleMap = new boolean[ants.getRows()][ants.getCols()];
        visibleBorder = new boolean[ants.getRows()][ants.getCols()];
        myAntAttackMap = new int[ants.getRows()][ants.getCols()];
        enemyAttackMap = new int[ants.getRows()][ants.getCols()];
        lastVisitedMap = new int[ants.getRows()][ants.getCols()];
        myAntMap = new MyAnt[ants.getRows()][ants.getCols()];
        enemyAntMap = new MapElem[ants.getRows()][ants.getCols()];
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(exploredMap[i], false);
            Arrays.fill(visibleMap[i], false);
            Arrays.fill(visibleBorder[i], false);
            Arrays.fill(lastVisitedMap[i], -1000);
        }
        initializeAttackMaps();
    }

    private void initializeAttackMaps() {
        for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
            for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                ATTACK_MAP_ENEMY_DIR[i][j] = 0;
                for (int k = 0; k < 5; k++) {
                    ATTACK_MAP_ANT_DIR[k][i][j] = 0;
                }
            }
        }
        for (int k = 0; k < 5; k++) {
            for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
                for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                    if (ATTACK_MAP[i][j] != 0) {
                        Tile enemyTile = getTileFromInternalAim(new Tile(i, j), k);
                        for (int l = 0; l < 5; l++) {
                            Tile antTile = getTileFromInternalAim(enemyTile, l);
                            ATTACK_MAP_ENEMY_DIR[antTile.getRow()][antTile.getCol()] |= 1 << k;
                        }
                    }
                }
            }
        }
        for (int k = 0; k < 5; k++) {
            for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
                for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                    if (ATTACK_MAP[i][j] != 0) {
                        Tile enemyTile = getTileFromInternalAim(new Tile(i, j), k);
                        for (int l = 0; l < 5; l++) {
                            Tile antTile = getTileFromInternalAim(enemyTile, l);
                            ATTACK_MAP_ANT_DIR[k][antTile.getRow()][antTile.getCol()] |= (1 << getReverseInternalAim(l));
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
            for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                if (ATTACK_MAP_ENEMY_DIR[i][j] < 10) {
                    sb.append("   ").append(ATTACK_MAP_ENEMY_DIR[i][j]).append(" ");
                } else if (ATTACK_MAP_ENEMY_DIR[i][j] < 100) {
                    sb.append("  ").append(ATTACK_MAP_ENEMY_DIR[i][j]).append(" ");
                } else if (ATTACK_MAP_ENEMY_DIR[i][j] < 1000) {
                    sb.append(" ").append(ATTACK_MAP_ENEMY_DIR[i][j]).append(" ");
                } else {
                    sb.append("").append(ATTACK_MAP_ENEMY_DIR[i][j]).append(" ");
                }
            }
            sb.append("\n");
        }
        logger.info(sb.toString());
        for (int k = 0; k < 5; k++) {
            sb = new StringBuilder();
            sb.append(k).append(":\n");
            for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
                for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                    if (ATTACK_MAP_ANT_DIR[k][i][j] < 10) {
                        sb.append("   ").append(ATTACK_MAP_ANT_DIR[k][i][j]).append(" ");
                    } else if (ATTACK_MAP_ANT_DIR[k][i][j] < 100) {
                        sb.append("  ").append(ATTACK_MAP_ANT_DIR[k][i][j]).append(" ");
                    } else if (ATTACK_MAP_ANT_DIR[k][i][j] < 1000) {
                        sb.append(" ").append(ATTACK_MAP_ANT_DIR[k][i][j]).append(" ");
                    } else {
                        sb.append("").append(ATTACK_MAP_ANT_DIR[k][i][j]).append(" ");
                    }
                }
                sb.append("\n");
            }
            logger.info(sb.toString());
        }
    }

    private int getReverseInternalAim(int aim) {
        if (aim == 1) {
            return 3;
        } else if (aim == 2) {
            return 4;
        } else if (aim == 3) {
            return 1;
        } else if (aim == 4) {
            return 2;
        } else {
            return 0;
        }
    }

    private Tile getTileFromInternalAim(Tile tile, int aim) {
        if (aim == 1) {
            return ants.getTile(tile, Aim.NORTH);
        } else if (aim == 2) {
            return ants.getTile(tile, Aim.EAST);
        } else if (aim == 3) {
            return ants.getTile(tile, Aim.SOUTH);
        } else if (aim == 4) {
            return ants.getTile(tile, Aim.WEST);
        } else {
            return tile;
        }
    }

    @Override
    public void doTurn() {
        ants = getAnts();
        getObjectsOnMap();
        updateAttackMap();
        updateVisibility();
        calculatePahtMaps();
        delegateAntsOnPathMaps();
        delegateAntsFromScore();
        defendOwnHills();
        updateBattlingAnts();
        stepAsideAwaitingAnts();
        issueCommands();
        turn++;
    }

    private void updateAttackMap() {
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(collisionMap[i], false);
            Arrays.fill(myAntAttackMap[i], 0);
            Arrays.fill(enemyAttackMap[i], 0);
        }
        for (MyAnt myAnt : myAnts) {
            for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
                for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                    if (ATTACK_MAP_ENEMY_DIR[i][j] != 0) {
                        int x = (myAnt.tile.getCol() + j - (ATTACK_MAP_ENEMY_DIR.length / 2) + ants.getCols()) % ants.getCols();
                        int y = (myAnt.tile.getRow() + i - (ATTACK_MAP_ENEMY_DIR.length / 2) + ants.getRows()) % ants.getRows();
                        myAntAttackMap[y][x]++;
                    }
                }
            }
        }
        for (MapElem enemy : myEnemyAnts) {
            for (int i = 0; i < ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER.length; i++) {
                for (int j = 0; j < ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER.length; j++) {
                    if (ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER[i][j] == 0) {
                        continue;
                    }
                    int x = (enemy.tile.getCol() + j - (ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER.length / 2) + ants.getCols()) % ants.getCols();
                    int y = (enemy.tile.getRow() + i - (ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER.length / 2) + ants.getRows()) % ants.getRows();
                    if (ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER[i][j] == 1) {
                        enemyAttackMap[y][x] = 1;
                    } else if (ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER[i][j] == 2) {
                        if (enemyAttackMap[y][x] == 0) {
                            enemyAttackMap[y][x] = 2;
                        }
                    }
                }
            }
        }
    }

    private void updateVisibility() {
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (ants.isVisible(mapTiles[i][j])) {
                    if (visibleBorder[i][j] || !visibleMap[i][j]) {
                        visibleBorder[i][j] = false;
                        for (Aim aim : Aim.values()) {
                            int col = (j + aim.getColDelta() + ants.getCols()) % ants.getCols();
                            int row = (i + aim.getRowDelta() + ants.getRows()) % ants.getRows();
                            if (!visibleMap[row][col]) {
                                visibleBorder[row][col] = true;
                            }
                        }
                    }
                    visibleMap[i][j] = true;
                }
            }
        }
        visibleBorderTiles = new HashSet<MapElem>();
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (visibleBorder[i][j]) {
                    visibleBorderTiles.add(new MapElem(mapTiles[i][j]));
                }
            }
        }
    }

    private void calculatePahtMaps() {
        pathMapFood[0].calculateMap(myFood, true, true, Integer.MAX_VALUE);
        myFoodUncovered = getUncoveredElems(pathMapFood[0], myFood);
        pathMapFood[1].calculateMap(myFoodUncovered, true, true, Integer.MAX_VALUE);
        pathMapEnemies.calculateMap(myEnemyAnts);
        pathMapEnemyHills.calculateMap(myEnemyHills);
        pathMapExploration.calculateMap(visibleBorderTiles, false);
        if (turn < 2) {
            pathMapMyHills.calculateMap(myHills, false);
        } else {
            pathMapMyHills.updateMapFromBorderTiles();
        }
    }

    private Set<MapElem> getUncoveredElems(PathMap pathMap, Set<MapElem> mapElems) {
        Set<MapElem> remainingElems = new HashSet<MapElem>(mapElems);
        for (MyAnt ant : myAnts) {
            MapElem elem = pathMap.infoInPathMap(ant.tile);
            if (elem != null) {
                remainingElems.remove(elem);
            }
        }
        return remainingElems;
    }

    private void issueCommands() {
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(collisionMap[i], false);
        }
        for (MyAnt ant : myAnts) {
            exploredMap[ant.tile.getRow()][ant.tile.getCol()] = true;
            if (ant.order == null) {
                collisionMap[ant.tile.getRow()][ant.tile.getCol()] = true;
            }
        }
        for (MyAnt ant : myAnts) {
            if (ant.order == null) {
                continue;
            }
            Tile dest = ants.getTile(ant.tile, ant.order);
            if (!collisionMap[dest.getRow()][dest.getCol()] && ants.getIlk(dest).isPassable()) {
                collisionMap[dest.getRow()][dest.getCol()] = true;
                ants.issueOrder(ant.tile, ant.order);
            } else {
                moveToAvoidCollision(ant);
            }
        }
    }

    private void moveToAvoidCollision(MyAnt ant) {
        for (Aim aim : PREFERRED_MOVE_DIRECTION[ant.order.ordinal()]) {
            Tile dest = ants.getTile(ant.tile, aim);
            if (!collisionMap[dest.getRow()][dest.getCol()] && ants.getIlk(dest).isPassable()) {
                collisionMap[dest.getRow()][dest.getCol()] = true;
                ant.order = aim;
                ants.issueOrder(ant.tile, ant.order);
                return;
            }
        }
        collisionMap[ant.tile.getRow()][ant.tile.getCol()] = true;
        ant.order = null;
    }

    private void defendOwnHills() {
        if (myHills.size() == 0) {
            return;
        }
        for (MapElem hill : myHills) {
            int enemyCount = 0;
            for (MapElem enemy : myEnemyAnts) {
                if (ants.getDistance(hill.tile, enemy.tile) <= DEFEND_HILL_RADIUS * 3 / 2) {
                    enemyCount++;
                }
            }
            int numDefendersPerHill = Math.max(Math.min((myAnts.size() / 5) / myHills.size(), DEFEND_HILL_MAX_DEFENDERS_PER_HILL), enemyCount);
            if (numDefendersPerHill <= 0) {
                continue;
            }
            Set<MyAnt> defenders = new HashSet<MyAnt>();
            for (MyAnt ant : myAnts) {
                if (ants.getDistance(hill.tile, ant.tile) <= DEFEND_HILL_RADIUS) {
                    defenders.add(ant);
                    ant.isDefendingBase = true;
                }
            }
            if (defenders.size() >= numDefendersPerHill) {
                continue;
            }
            MyAnt nearestAnt = null;
            int nearestDist = Integer.MAX_VALUE;
            for (MyAnt ant : myAnts) {
                if (ants.getDistance(hill.tile, ant.tile) <= DEFEND_HILL_RADIUS * 2) {
                    continue;
                }
                int dist = pathMapMyHills.distInPathMap(ant.tile);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestAnt = ant;
                }
            }
            if (nearestAnt != null) {
                nearestAnt.order = pathMapMyHills.getFirstMovementInPath(nearestAnt.tile);
                nearestAnt.isDefendingBase = true;
            }
        }
    }

    private MyAnt findClosestAnt(PathMap pathMap, Set<MyAnt> antSet) {
        int minDist = Integer.MAX_VALUE;
        MyAnt minAnt = null;
        for (MyAnt ant : antSet) {
            int dist = pathMap.distInPathMap(ant.tile);
            if (dist < minDist) {
                minDist = dist;
                minAnt = ant;
            }
        }
        return minAnt;
    }

    private void updateBattlingAnts() {
        logAttackMap();
        Set<EnemyWithBattleResolution> killableEnemies = new HashSet<EnemyWithBattleResolution>();
        Set<EnemyWithBattleResolution> allAttackingEnemies = new HashSet<EnemyWithBattleResolution>();
        int myAntsInRange[] = {0, 0, 0, 0, 0};
        logger.info("Turn " + turn);
        for (MapElem elem : myEnemyAnts) {
            if (myAntAttackMap[elem.tile.getRow()][elem.tile.getCol()] == 0) {
                continue;
            }
            EnemyWithBattleResolution enemy = new EnemyWithBattleResolution(elem.tile);
            myAntsInRange[0] = myAntsInRange[1] = myAntsInRange[2] = myAntsInRange[3] = myAntsInRange[4] = 0;
            Set<MapElem> antsInAttackRange = getAntsInAttackRange(enemy.tile);
            for (int enemyDir = 0; enemyDir < 5; enemyDir++) {
                if (!getIlkFromInternalAim(enemy.tile, enemyDir).isPassable()) {
                    continue;
                }
                for (MapElem ant : antsInAttackRange) {
                    int score = ant instanceof MyAnt ? 1 : -1;
                    int attackDirs = getAttackMapDir(ATTACK_MAP_ENEMY_DIR, enemy.tile, ant.tile);
                    if ((attackDirs & (1 << enemyDir)) != 0) {
                        enemy.moveScore[enemyDir] += score;
                        if (ant instanceof MyAnt) {
                            myAntsInRange[enemyDir]++;
                        }
                    }
                }
            }
            for (int enemyDir = 0; enemyDir < 5; enemyDir++) {
                if (myAntsInRange[enemyDir] == 0) {
                    enemy.moveScore[enemyDir] = 0;
                }
            }
            for (MapElem ant : antsInAttackRange) {
                if (!(ant instanceof MyAnt)) {
                    continue;
                }
                MyAnt myAnt = (MyAnt) ant;
                for (int enemyDir = 0; enemyDir < 5; enemyDir++) {
                    int attackDirs = getAttackMapDir(ATTACK_MAP_ANT_DIR[enemyDir], enemy.tile, ant.tile);
                    if (enemy.moveScore[enemyDir] < 0) {
                        myAnt.battleLoseDirs |= attackDirs;
                    }
                    if (enemy.moveScore[enemyDir] > 0) {
                        myAnt.battleWinDirs |= attackDirs;
                    }
                }
            }
            int winDirs = 0;
            int loseDirs = 0;
            for (int enemyDir = 1; enemyDir < 5; enemyDir++) {
                if (enemy.moveScore[enemyDir] < 0) {
                    loseDirs |= 1 << enemyDir;
                }
                if (enemy.moveScore[enemyDir] > 0) {
                    winDirs |= 1 << enemyDir;
                }
            }
            if (loseDirs == 0 && (winDirs != 0/* || razeMode*/)) {
                killableEnemies.add(enemy);
            }
            allAttackingEnemies.add(enemy);
        }
        int battleResolution[] = {0, 0, 0, 0, 0};
        for (MapElem enemy : killableEnemies) {
            battleResolution[0] = battleResolution[1] = battleResolution[2] = battleResolution[3] = battleResolution[4] = 0;
            Set<MapElem> antsInAttackRange = getAntsInAttackRange(enemy.tile);
            for (MapElem ant : antsInAttackRange) {
                int score = ant instanceof MyAnt ? 1 : -1;
                int attackDirs = getAttackMapDir(ATTACK_MAP_ENEMY_DIR, enemy.tile, ant.tile);
                for (int enemyDir = 0; enemyDir < 5; enemyDir++) {
                    if ((attackDirs & (1 << enemyDir)) != 0) {
                        battleResolution[enemyDir] += score;
                    }
                }
            }
            int minResolution = 0;
            int minDir = 100;
            for (int enemyDir = 1; enemyDir < 5; enemyDir++) {
                logger.info(enemyDir + ": " + battleResolution[enemyDir]);
                if ((battleResolution[enemyDir] > minResolution && battleResolution[enemyDir] > 0) || minResolution == 0) {
                    minResolution = battleResolution[enemyDir];
                    minDir = enemyDir;
                }
            }
            for (MapElem ant : antsInAttackRange) {
                Tile antTile;
                if (ant instanceof MyAnt) {
                    MyAnt myAnt = (MyAnt) ant;
                    if (myAnt.isFighting) {
                        continue;
                    }
                    myAnt.order = attackEnemy(myAnt, enemy, minDir);
                    logger.info("Turn " + turn + " - Ant " + ant.tile + " attacking in dir " + myAnt.order + " / " + getAttackMapDir(ATTACK_MAP_ANT_DIR[minDir], enemy.tile, ant.tile));
                    myAnt.isFighting = true;
                    antTile = myAnt.order != null ? ants.getTile(myAnt.tile, myAnt.order) : myAnt.tile;
                    collisionMap[antTile.getRow()][antTile.getCol()] = true;
                } else {
                    antTile = ant.tile;
                }
                int score = ant instanceof MyAnt ? 1 : -1;
                int attackDirs = getAttackMapDir(ATTACK_MAP_ENEMY_DIR, enemy.tile, antTile);
                for (int enemyDir = 0; enemyDir < 5; enemyDir++) {
                    if ((attackDirs & (1 << enemyDir)) != 0) {
                        battleResolution[enemyDir] += score;
                    }
                }
            }
            boolean canAttack = true;
            /*int minDir = 0;
            for (int enemyDir = 1; enemyDir < 5; enemyDir++) {
                logger.info(enemyDir + ": " + battleResolution[enemyDir]);
                if (battleResolution[enemyDir] < minResolution) {
                    minResolution = battleResolution[enemyDir];
                    minDir = enemyDir;
                }
            }*/
            logger.info("CAN ATTACK: " + (minResolution >= 0) + " - " + enemy.tile);
            if (canAttack) {
                for (MapElem ant : antsInAttackRange) {
                    if (ant instanceof MyAnt) {
                        MyAnt myAnt = (MyAnt) ant;
                        if (myAnt.hasMovedInBattle || !myAnt.isFighting) {
                            continue;
                        }
                        Tile tile = myAnt.order != null ? ants.getTile(myAnt.tile, myAnt.order) : myAnt.tile;
                        collisionMap[tile.getRow()][tile.getCol()] = false;
                        myAnt.order = null;
                        myAnt.isFighting = false;
                    }
                }
            } else {
                for (MapElem ant : antsInAttackRange) {
                    if (ant instanceof MyAnt) {
                        MyAnt myAnt = (MyAnt) ant;
                        myAnt.hasMovedInBattle = true;
                    }
                }
            }
        }
        for (MapElem enemy : allAttackingEnemies) {
            Set<MapElem> antsInAttackRange = getAntsInAttackRange(enemy.tile);
            for (MapElem ant : antsInAttackRange) {
                if (ant instanceof MyAnt) {
                    MyAnt myAnt = (MyAnt) ant;
                    if (myAnt.hasMovedInBattle) {
                        continue;
                    }
                    myAnt.order = getGoodMoveAwayFromEnemy(myAnt);
                    myAnt.hasMovedInBattle = true;
                    Tile tile = myAnt.order != null ? ants.getTile(myAnt.tile, myAnt.order) : myAnt.tile;
                    collisionMap[tile.getRow()][tile.getCol()] = true;
                    logger.info("MOVING AWAY " + myAnt.tile + " to " + tile);
                }
            }
        }
    }

    private Aim attackEnemy(MyAnt myAnt, MapElem enemy, int enemyDir) {
        for (Aim aim : Aim.values()) {
            Tile tile = ants.getTile(myAnt.tile, aim);
            if (!ants.getIlk(tile).isPassable() || collisionMap[tile.getRow()][tile.getCol()]) {
                continue;
            }
            /*int antDir = aim.ordinal() + 1;
            int attackMap = getAttackMapDir(ATTACK_MAP_ANT_DIR[enemyDir], enemy.tile, myAnt.tile);
            if ((attackMap & (1 << antDir)) != 0) {*/
            if ((myAnt.battleWinDirs & (1 << (aim.ordinal() + 1))) != 0) {
                return aim;
            }
        }
        return null;
    }

    private Ilk getIlkFromInternalAim(Tile tile, int dir) {
        if (dir == 1) {
            return ants.getIlk(tile, Aim.NORTH);
        } else if (dir == 2) {
            return ants.getIlk(tile, Aim.EAST);
        } else if (dir == 3) {
            return ants.getIlk(tile, Aim.SOUTH);
        } else if (dir == 4) {
            return ants.getIlk(tile, Aim.WEST);
        } else {
            return ants.getIlk(tile);
        }
    }

    private int getAttackMapDir(int [][] attackMap, Tile enemy, Tile ant) {
        int x1 = enemy.getCol();
        int x2 = ant.getCol();
        if (x1 < 10 && x2 > ants.getCols() - 10) {
            x1 += ants.getCols();
        }
        if (x2 < 10 && x1 > ants.getCols() - 10) {
            x2 += ants.getCols();
        }
        int y1 = enemy.getRow();
        int y2 = ant.getRow();
        if (y1 < 10 && y2 > ants.getRows() - 10) {
            y1 += ants.getRows();
        }
        if (y2 < 10 && y1 > ants.getRows() - 10) {
            y2 += ants.getRows();
        }
        int j = (x2 - x1) + (ATTACK_MAP_ENEMY_DIR.length / 2);
        int i = (y2 - y1) + (ATTACK_MAP_ENEMY_DIR.length / 2);
        if (i < 0 || i >= ATTACK_MAP_ENEMY_DIR.length || j < 0 || j >= ATTACK_MAP_ENEMY_DIR.length) {
            return 0;
        }
        return attackMap[i][j];
    }

    private Set<MapElem> getAntsInAttackRange(Tile tile) {
        Set<MapElem> antsInRange = new HashSet<MapElem>();
        StringBuilder sb = new StringBuilder();
        sb.append(tile).append("\n");
        for (int i = 0; i < ATTACK_MAP_ENEMY_DIR.length; i++) {
            for (int j = 0; j < ATTACK_MAP_ENEMY_DIR.length; j++) {
                if (ATTACK_MAP_ENEMY_DIR[i][j] == 0) {
                    sb.append(" ");
                    continue;
                }
                int mx = (tile.getCol() + j - (ATTACK_MAP_ENEMY_DIR.length / 2) + ants.getCols()) % ants.getCols();
                int my = (tile.getRow() + i - (ATTACK_MAP_ENEMY_DIR.length / 2) + ants.getRows()) % ants.getRows();
                if (myAntMap[my][mx] != null) {
                    antsInRange.add(myAntMap[my][mx]);
                    sb.append("*");
                } else if (enemyAntMap[my][mx] != null) {
                    antsInRange.add(enemyAntMap[my][mx]);
                    sb.append("@");
                } else {
                    sb.append(".");
                }
            }
            sb.append("\n");
        }
        logger.info(sb.toString());
        return antsInRange;
    }

    private Aim getGoodMoveAwayFromEnemy(MyAnt ant) {
        Aim nonBorderMove = null;
        for (Aim aim : Aim.values()) {
            Tile tile = ants.getTile(ant.tile, aim);
            if (!ants.getIlk(tile).isPassable() || collisionMap[tile.getRow()][tile.getCol()]) {
                continue;
            }
            if (enemyAttackMap[tile.getRow()][tile.getCol()] == 2) {
                return aim;
            }
            if (enemyAttackMap[tile.getRow()][tile.getCol()] == 0) {
                nonBorderMove = aim;
            }
        }
        return nonBorderMove;
    }

    private void stepAsideAwaitingAnts() {
        for (MyAnt ant : myAnts) {
            if (ant.order != null) {
                continue;
            }
            Aim stepAside = null;
            for (Aim aim : Aim.values()) {
                if (ants.getIlk(ant.tile, aim) != Ilk.MY_ANT) {
                    continue;
                }
                Tile tile = ants.getTile(ant.tile, aim);
                MyAnt otherAnt = myAntMap[tile.getRow()][tile.getCol()];
                if (otherAnt.order == null) {
                    continue;
                }
                if (ant.tile.equals(ants.getTile(otherAnt.tile, otherAnt.order))) {
                    stepAside = otherAnt.order;
                }
            }
            if (stepAside == null) {
                continue;
            }
            for (Aim aim : PREFERRED_STEP_ASIDE_DIRECTION[stepAside.ordinal()]) {
                Tile stepAsideTile = ants.getTile(ant.tile, aim);
                if (!ants.getIlk(stepAsideTile).isPassable()) {
                    continue;
                }
                if (myAntAttackMap[stepAsideTile.getRow()][stepAsideTile.getCol()] != 0) {
                    continue;
                }
                ant.pathMap = pathMapEnemies;
                ant.order = aim;
                break;
            }
        }
    }

    private void delegateAntsOnPathMaps() {
        delegateAntsOnPathMap(pathMapFood[0], 32);
        restrictNumberOfAntsPerDestination(pathMapFood[0], myFood, 1);
        delegateAntsOnPathMap(pathMapFood[1], 32);
        restrictNumberOfAntsPerDestination(pathMapFood[1], myFoodUncovered, 1);
        delegateAntsOnPathMap(pathMapEnemies, 10);
        restrictNumberOfAntsPerDestination(pathMapEnemies, myEnemyAnts, 6);
        for (MyAnt ant : myAnts) {
            if (ant.pathMap == null) {
                continue;
            }
            ant.order = ant.pathMap.getFirstMovementInPath(ant.tile);
        }
    }

    private void delegateAntsFromScore() {
        Set<MapElem> antsAsMapElems = getAntsAsMapElems();
        pathMapMyAnts.calculateMap(antsAsMapElems, PATHMAP_DISTANCE_MY_ANTS);
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (pathMapMyAnts.distInPathMap(mapTiles[i][j]) < PATHMAP_DISTANCE_LAST_VISITED) {
                    lastVisitedMap[i][j] = turn;
                }
                MyAnt ant = (MyAnt) pathMapMyAnts.infoInPathMap(mapTiles[i][j]);
                if (ant == null || ant.order != null) {
                    continue;
                }
                int distInExplorationMap = Math.min(pathMapExploration.distInPathMap(mapTiles[i][j]), 10000);
                int distInEnemyHillMap = Math.min(pathMapEnemyHills.distInPathMap(mapTiles[i][j]), 50);
                int score = 0;
                score += (50 - distInEnemyHillMap) * EXPLORE_SCORE_ENEMY_HILL;
                if (score == 0) {
                    score += (turn - lastVisitedMap[i][j]) * EXPLORE_SCORE_LAST_VISITED;
                    score += (10000 - distInExplorationMap) * EXPLORE_SCORE_UNEXPLORED_AREA;
                }
                if (score > ant.exploreBestScore) {
                    ant.exploreBestScore = score;
                    ant.exploreBestTile = mapTiles[i][j];
                }
            }
        }
        Set<MapElem> antsWithNoOrders = getAntsWithNoOrders();
        for (MapElem elem : antsWithNoOrders) {
            MyAnt ant = (MyAnt) elem;
            if (ant.exploreBestTile == null) {
                continue;
            }
            ant.pathMap = pathMapMyAnts;
            ant.order = pathMapMyAnts.getLastMovementInPath(ant.exploreBestTile);
        }
    }

    private Set<MapElem> getAntsAsMapElems() {
        Set<MapElem> ants = new HashSet<MapElem>();
        for (MyAnt ant : myAnts) {
            ants.add(ant);
        }
        return ants;
    }

    private Set<MapElem> getAntsWithNoOrders() {
        Set<MapElem> ants = new HashSet<MapElem>();
        for (MyAnt ant : myAnts) {
            if (ant.order == null) {
                ants.add(ant);
            }
        }
        return ants;
    }

    private void delegateAntsOnPathMap(PathMap pathMap, int maxDist) {
        for (MyAnt ant : myAnts) {
            if (ant.pathMap != null) {
                continue;
            }
            if (pathMap.distInPathMap(ant.tile) > maxDist) {
                continue;
            }
            MapElem elem = pathMap.infoInPathMap(ant.tile);
            if (elem == null) {
                continue;
            }
            ant.pathMap = pathMap;
            elem.ants.add(ant);
        }
    }

    private void restrictNumberOfAntsPerDestination(PathMap pathMap, Set<MapElem> mapElems, int maxAnts) {
        for (MapElem elem : mapElems) {
            if (elem.ants.size() <= maxAnts) {
                continue;
            }
            Set<MyAnt> antsLeft = new HashSet<MyAnt>();
            Set<MyAnt> antsOrig = new HashSet<MyAnt>(elem.ants);
            for (int i = 0; i < maxAnts; i++) {
                MyAnt closestAnt = findClosestAnt(pathMap, antsOrig);
                antsLeft.add(closestAnt);
                antsOrig.remove(closestAnt);
            }
            for (MyAnt ant : elem.ants) {
                ant.pathMap = null;
            }
            for (MyAnt ant : antsLeft) {
                ant.pathMap = pathMap;
            }
            elem.ants = antsLeft;
        }
    }

    private void getObjectsOnMap() {
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(myAntMap[i], null);
            Arrays.fill(enemyAntMap[i], null);
        }
        myAnts = new HashSet<MyAnt>();
        for (Tile ant : ants.getMyAnts()) {
            MyAnt myAnt = new MyAnt(ant);
            myAnts.add(myAnt);
            myAntMap[ant.getRow()][ant.getCol()] = myAnt;
        }
        myEnemyAnts = new HashSet<MapElem>();
        for (Tile enemy : ants.getEnemyAnts()) {
            MapElem enemyAnt = new MapElem(enemy);
            myEnemyAnts.add(enemyAnt);
            enemyAntMap[enemy.getRow()][enemy.getCol()] = enemyAnt;
        }
        myFood = new HashSet<MapElem>();
        for (Tile food : ants.getFoodTiles()) {
            myFood.add(new MapElem(food));
        }
        myHills = new HashSet<MapElem>();
        for (Tile myHill : ants.getMyHills()) {
            myHills.add(new MapElem(myHill));
        }
        myEnemyHills = new HashSet<MapElem>();
        for (Tile enemyHill : ants.getEnemyHills()) {
            myEnemyHills.add(new MapElem(enemyHill));
        }
    }

    private void logMap() {
        String s = "Map in turn " + turn + ":\n";
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                Ilk ilk = ants.getIlk(mapTiles[i][j]);
                if (ilk == Ilk.LAND) {
                    if (visibleMap[i][j]) {
                        s += ".";
                    } else {
                        s += " ";
                    }
                } else {
                    s += ants.getIlk(new Tile(i, j));
                }
            }
            s += "\n";
        }
        logger.info(s);
    }

    private void logAttackMap() {
        StringBuilder sb = new StringBuilder().append("Attack map in turn ").append(turn).append(":\n");
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                Ilk ilk = ants.getIlk(new Tile(i, j));
                if (ilk == Ilk.ENEMY_ANT) {
                    sb.append("@");
                } else if (ilk == Ilk.MY_ANT) {
                    sb.append("*");
                } else if (enemyAttackMap[i][j] > 0) {
                    sb.append(enemyAttackMap[i][j]);
                } else {
                    sb.append(" ");
                }
            }
            sb.append("\n");
        }
        logger.info(sb.toString());
    }

    private void logVisibleMap() {
        char s[][] = new char[ants.getRows()][ants.getCols()];
        String r = "Map in turn " + turn + ":\n";
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (ants.getIlk(new Tile(i, j)) == Ilk.WATER) {
                    s[i][j] = '@';
                } else if (visibleBorder[i][j]) {
                    s[i][j] = '*';
                } else if (visibleMap[i][j]) {
                    s[i][j] = '.';
                } else {
                    s[i][j] = ' ';
                }
            }
        }
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                r += s[i][j];
            }
            r += "\n";
        }
        logger.info(r);
    }


    /*
        PATH ALGORITHMS
        ---------------
     */

    private void initializePathAlgorithms() {
        mapTiles = new Tile[ants.getRows()][ants.getCols()];
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                mapTiles[i][j] = new Tile(i, j);
            }
        }
        pathMapFood[0] = new PathMap(1024 * 24);
        pathMapFood[1] = new PathMap(1024 * 24);
        pathMapMyHills = new PathMap(1024 * 16);
        pathMapEnemies = new PathMap(1024 * 16);
        pathMapEnemyHills = new PathMap(1024 * 8);
        pathMapMyAnts = new PathMap(1024 * 16);
        pathMapExploration = new PathMap(1024 * 16);
    }

    private class PathMap {
        public int[][] distMap;
        public Tile[][] destMap;
        public MapElem[][] infoMap;
        private PathElem[] pathQueue;
        private int queueIndex;
        private int queueCount;
        private int maxQueueLength;
        private boolean initial = true;

        public PathMap(int maxQueueLength) {
            this.maxQueueLength = maxQueueLength;
            distMap = new int[ants.getRows()][ants.getCols()];
            destMap = new Tile[ants.getRows()][ants.getCols()];
            infoMap = new MapElem[ants.getRows()][ants.getCols()];
            pathQueue = new PathElem[maxQueueLength];
        }

        public int distInPathMap(Tile tile) {
            return distMap[tile.getRow()][tile.getCol()];
        }

        public Tile destInPathMap(Tile tile) {
            return destMap[tile.getRow()][tile.getCol()];
        }

        public MapElem infoInPathMap(Tile tile) {
            return infoMap[tile.getRow()][tile.getCol()];
        }

        public Aim getFirstMovementInPath(Tile source) {
            if (distMap[source.getRow()][source.getCol()] == Integer.MAX_VALUE) {
                return null;
            }
            int minDist = Integer.MAX_VALUE;
            Aim minAim = null;
            for (Aim aim : Aim.values()) {
                Tile tile = ants.getTile(source, aim);
                int dist = distMap[tile.getRow()][tile.getCol()];
                if (dist < minDist && ants.getIlk(tile).isPassable()) {
                    minDist = dist;
                    minAim = aim;
                }
            }
            return minAim;
        }

        public Aim getLastMovementInPath(Tile source) {
            if (distMap[source.getRow()][source.getCol()] == Integer.MAX_VALUE) {
                return null;
            }
            Tile currentTile = source;
            while (true) {
                int minDist = Integer.MAX_VALUE;
                Aim minAim = null;
                Tile minTile = null;
                for (Aim aim : Aim.values()) {
                    Tile tile = ants.getTile(currentTile, aim);
                    int dist = distMap[tile.getRow()][tile.getCol()];
                    if (dist < minDist && ants.getIlk(tile).isPassable()) {
                        minDist = dist;
                        minAim = aim;
                        minTile = tile;
                    }
                }
                if (minTile == null) {
                    return null;
                }
                currentTile = minTile;
                if (minDist == 0) {
                    return REVERSE_DIRECTION[minAim.ordinal()];
                }
            }
        }

        public void updateMapFromBorderTiles() {
            if (initial) {
                calculateMap(visibleBorderTiles, false);
                return;
            }
            Set<Tile> tilesToInvestigate = new HashSet<Tile>();
            for (MapElem elem : visibleBorderTiles) {
                for (Aim aim: Aim.values()) {
                    Tile tile = ants.getTile(elem.tile, aim);
                    if (!visibleMap[tile.getRow()][tile.getCol()]) {
                        continue;
                    }
                    if (!ants.getIlk(tile).isPassable()) {
                        continue;
                    }
                    tilesToInvestigate.add(tile);
                }
            }
            queueIndex = 0;
            queueCount = 0;
            for (Tile tile : tilesToInvestigate) {
                if (distMap[tile.getRow()][tile.getCol()] < Integer.MAX_VALUE) {
                    continue;
                }
                int minDist = Integer.MAX_VALUE;
                Tile minTile = null;
                for (Aim aim: Aim.values()) {
                    Tile newTile = ants.getTile(tile, aim);
                    if (!ants.getIlk(newTile).isPassable()) {
                        continue;
                    }
                    int dist = distMap[newTile.getRow()][newTile.getCol()];
                    if (dist < minDist) {
                        minDist = dist;
                        minTile = newTile;
                    }
                }
                if (minTile == null) {
                    continue;
                }
                PathElem pathElem = new PathElem(tile.getCol(), tile.getRow(), minDist + 1);
                pathQueue[queueCount++] = pathElem;
                distMap[pathElem.y][pathElem.x] = minDist + 1;
                destMap[pathElem.y][pathElem.x] = destMap[minTile.getRow()][minTile.getCol()];
                infoMap[pathElem.y][pathElem.x] = infoMap[minTile.getRow()][minTile.getCol()];
            }
            calculateMapInternal(false, false, Integer.MAX_VALUE);
        }

        public void calculateMap(Set<MapElem> tiles) {
            calculateMap(tiles, true, false, Integer.MAX_VALUE);
        }

        public void calculateMap(Set<MapElem> tiles, boolean includeInvisibleTiles) {
            calculateMap(tiles, includeInvisibleTiles, false, Integer.MAX_VALUE);
        }

        public void calculateMap(Set<MapElem> tiles, int maxDistance) {
            calculateMap(tiles, true, false, maxDistance);
        }

        public void calculateMap(Set<MapElem> tiles, boolean includeInvisibleTiles, boolean avoidEnemies, int maxDistance) {
            for (int i = 0; i < ants.getRows(); i++) {
                Arrays.fill(distMap[i], Integer.MAX_VALUE);
                Arrays.fill(destMap[i], null);
                Arrays.fill(infoMap[i], null);
            }
            queueIndex = 0;
            queueCount = 0;
            for (MapElem elem : tiles) {
                if (!ants.getIlk(elem.tile).isPassable()) {
                    continue;
                }
                PathElem pathElem = new PathElem(elem.tile.getCol(), elem.tile.getRow(), 0);
                pathQueue[queueCount++] = pathElem;
                distMap[pathElem.y][pathElem.x] = pathElem.count;
                destMap[pathElem.y][pathElem.x] = elem.tile;
                infoMap[pathElem.y][pathElem.x] = elem;
            }
            calculateMapInternal(includeInvisibleTiles, avoidEnemies, maxDistance);
        }

        public void calculateMapInternal(boolean includeInvisibleTiles, boolean avoidEnemies, int maxDistance) {
            initial = false;
            while (queueIndex < queueCount && queueCount < maxQueueLength - 4) {
                PathElem pathElem = pathQueue[queueIndex++];
                if (pathElem.count >= maxDistance) {
                    continue;
                }
                for (Aim aim: Aim.values()) {
                    Tile newTile = ants.getTile(mapTiles[pathElem.y][pathElem.x], aim);
                    if (!ants.getIlk(newTile).isPassable()) {
                        continue;
                    }
                    if (!includeInvisibleTiles && !visibleMap[newTile.getRow()][newTile.getCol()]) {
                        continue;
                    }
                    if (avoidEnemies && pathMapEnemies.distInPathMap(newTile) < PATHMAP_DISTANCE_AVOID_ENEMY) {
                        continue;
                    }
                    int curDist = distMap[newTile.getRow()][newTile.getCol()];
                    if (pathElem.count + 1 >= curDist) {
                        continue;
                    }
                    PathElem newElem = new PathElem(newTile.getCol(), newTile.getRow(), pathElem.count + 1);
                    pathQueue[queueCount++] = newElem;
                    distMap[newElem.y][newElem.x] = newElem.count;
                    destMap[newElem.y][newElem.x] = destMap[pathElem.y][pathElem.x];
                    infoMap[newElem.y][newElem.x] = infoMap[pathElem.y][pathElem.x];
                }
            }
        }

        public void logPathMap() {
            StringBuilder sb = new StringBuilder();
            sb.append("PathMap in turn ").append(turn).append(":\n");
            for (int i = 0; i < ants.getRows(); i++) {
                for (int j = 0; j < ants.getCols(); j++) {
                    int o = distMap[i][j];
                    if (o == Integer.MAX_VALUE) {
                        if (visibleBorder[i][j]) {
                            sb.append(" .");
                        } else {
                            sb.append("  ");
                        }
                    } else if (o < 10) {
                        sb.append("0").append(o);
                    } else {
                        sb.append(o);
                    }
                    if (ants.getIlk(mapTiles[i][j]) == Ilk.FOOD) {
                        sb.append("%");
                    } else if (ants.getIlk(mapTiles[i][j]) == Ilk.MY_ANT) {
                        sb.append("*");
                    } else if (ants.getIlk(mapTiles[i][j]) == Ilk.ENEMY_ANT) {
                        sb.append("@");
                    } else {
                        sb.append(" ");
                    }
                }
                sb.append("\n");
            }
            logger.info(sb.toString());
        }
    }

    private class PathElem {
        public int x, y;
        public int count;
        public PathElem(int x, int y, int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }

    private class MapElem {
        public Tile tile;
        public Set<MyAnt> ants = new HashSet<MyAnt>();
        public MapElem(Tile tile) {
            this.tile = tile;
        }
    }

    private class MyAnt extends MapElem {
        public Aim order;
        public PathMap pathMap;
        public boolean isFighting = false;
        public boolean hasMovedInBattle = false;
        public boolean isDefendingBase = false;
        public Tile exploreBestTile = null;
        public int exploreBestScore = Integer.MIN_VALUE;
        public int battleWinDirs = 0;
        public int battleLoseDirs = 0;

        public MyAnt(Tile tile) {
            super(tile);
        }
    }

    private class EnemyWithBattleResolution extends MapElem {
        int[] moveScore = {0, 0, 0, 0, 0};
        public EnemyWithBattleResolution(Tile tile) {
            super(tile);
        }
    }

    private static final int[][] ATTACK_MAP = new int[][]
            {
                    {0, 0, 0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 1, 1, 1, 0, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 0, 1, 1, 1, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0, 0, 0}
            };

    private static final int[][] ATTACK_MAP_ENEMY_DIR = new int[9][9];
    private static final int[][][] ATTACK_MAP_ANT_DIR = new int[5][9][9];

    private static final int[][] ATTACK_MAP_ONE_MOVEMENT_WITH_BORDER = new int[][]
            {
                    {0, 0, 0, 2, 2, 2, 0, 0, 0},
                    {0, 0, 2, 1, 1, 1, 2, 0, 0},
                    {0, 2, 1, 1, 1, 1, 1, 2, 0},
                    {2, 1, 1, 1, 1, 1, 1, 1, 2},
                    {2, 1, 1, 1, 1, 1, 1, 1, 2},
                    {2, 1, 1, 1, 1, 1, 1, 1, 2},
                    {0, 2, 1, 1, 1, 1, 1, 2, 0},
                    {0, 0, 2, 1, 1, 1, 2, 0, 0},
                    {0, 0, 0, 2, 2, 2, 0, 0, 0}
            };

    private static final Aim PREFERRED_STEP_ASIDE_DIRECTION[][] =
            {
                    {Aim.EAST, Aim.WEST, Aim.NORTH}, // Other ant from NORTH
                    {Aim.SOUTH, Aim.NORTH, Aim.EAST}, // Other ant from EAST
                    {Aim.WEST, Aim.EAST, Aim.SOUTH}, // Other ant from SOUTH
                    {Aim.NORTH, Aim.SOUTH, Aim.WEST}, // Other ant from WEST
            };

    private static final Aim PREFERRED_MOVE_DIRECTION[][] =
            {
                    {Aim.EAST, Aim.WEST, Aim.SOUTH}, // Obstacle at NORTH
                    {Aim.SOUTH, Aim.NORTH, Aim.WEST}, // Obstacle at EAST
                    {Aim.WEST, Aim.EAST, Aim.NORTH}, // Obstacle at SOUTH
                    {Aim.NORTH, Aim.SOUTH, Aim.EAST}, // Obstacle at WEST
            };

    private static final Aim REVERSE_DIRECTION[] = {Aim.SOUTH, Aim.WEST, Aim.NORTH, Aim.EAST};
}
