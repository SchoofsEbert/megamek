package megamek.server;

import junit.framework.TestCase;
import megamek.MegaMek;
import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.*;
import megamek.common.icons.Camouflage;
import megamek.common.logging.FakeLogger;
import megamek.common.logging.MMLogger;
import megamek.common.net.ConnectionFactory;
import megamek.common.net.IConnection;
import megamek.common.net.Packet;
import megamek.common.score.EloScore;
import megamek.server.victory.Victory;
import megamek.server.victory.VictoryResult;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

public class ServerTest extends TestCase {
    Server server;
    Game game;
    BipedMech entity;
    Player player;
    MMLogger logger;

    @Override
    public void setUp() throws Exception {
        server = new Server("password",8123);

        game =  new Game();
        entity = new BipedMech();
        player = new Player(0, "JohnDoe");
        game.addPlayer(0, player);
        game.addEntity(entity);

        logger = Mockito.mock(FakeLogger.class);
        MegaMek.setLogger(logger);

        server.setGame(game);
    }

    @Override
    public void tearDown() {
        server.die();
    }

    //Moved to GameLogic
    public void testSetGame() {
        assertEquals(game, entity.getGame());
        assertTrue(player.isGhost());
        assertEquals(game, server.getGame());
    }

    //Stayed in Server, should be deleted later
    public void testGetPlayer() {
        assertEquals(server.getPlayer(0), player);
    }

    //Moved to GameServer
    public void testReceivePlayerVersionCorrect() {
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = MegaMek.getMegaMekSHA256();
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData));
        Mockito.verify(logger, Mockito.times(1)).info("SUCCESS: Client/Server Version (" + MegaMek.VERSION + ") and Checksum ("
                + MegaMek.getMegaMekSHA256() + ") matched");
    }

    public void testReceivePlayerVersionMismatch() {
        Object[] versionData = new Object[2];
        versionData[0] = "Faulty";
        versionData[1] = MegaMek.getMegaMekSHA256();
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData));
        Mockito.verify(logger, Mockito.times(1)).error("Client/Server version Mismatch -- Client: Faulty Server: "+MegaMek.VERSION);
    }

    public void testReceivePlayerVersionChecksumMismatch() {
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = "Faulty";
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData));
        Mockito.verify(logger, Mockito.times(1)).error("Client/Server checksum Mismatch -- Client: Faulty Server: " + MegaMek.getMegaMekSHA256());
    }

    private ArrayList<IConnection> sendPlayerNames(List<Integer> connIds, List<String> names) throws NoSuchFieldException, IllegalAccessException {
        if (connIds.size() != names.size()){
            fail();
        }
        Field connectionsPending = Server.class.getDeclaredField("connectionsPending");
        connectionsPending.setAccessible(true);
        Socket s = new Socket();
        ArrayList<IConnection> connections = new ArrayList<>(4);
        for(int connId: connIds) {
            IConnection connection = Mockito.mock(ConnectionFactory.getInstance().createServerConnection(s, connId).getClass());
            Mockito.when(connection.getId()).thenReturn(connId);
            connections.add(connection);
        }
        connectionsPending.set(server, new Vector<>(connections));
        for(int i = 0; i < connIds.size(); i++) {
            int connId = connIds.get(i);
            assertEquals(server.getPendingConnection(connId), connections.get(i));
            server.handle(connId, new Packet(Packet.COMMAND_CLIENT_NAME, names.get(i)));
        }
        return connections;
    }

    //Moved to GameServer
    public void testReceivePlayerNameExisting() throws NoSuchFieldException, IllegalAccessException {
        IConnection connection = sendPlayerNames(List.of(0), List.of("JohnDoe")).get(0);

        assertNull(server.getPendingConnection(0));
        assertFalse(player.isGhost());
        assertEquals(server.getConnection(0), connection);
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        String who = player.getName() + " connected from " + connection.getInetAddress();
        Mockito.verify(logger, Mockito.times(1)).info("s: player #" + 0 + ", " + who);
    }

    //Moved to GameServer
    public void testReceivePlayerNameNew() throws NoSuchFieldException, IllegalAccessException, UnknownHostException {
        IConnection connection = sendPlayerNames(List.of(1), List.of("NewPlayer")).get(0);

        assertNull(server.getPendingConnection(1));
        IPlayer newplayer = server.getPlayer(1);
        assertFalse(newplayer.isGhost());
        assertEquals(server.getConnection(1), connection);
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        InetAddress[] addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        for (InetAddress address : addresses) {
            Mockito.verify(logger, Mockito.times(1)).info("s: machine IP " + address.getHostAddress());
        }
        String who = newplayer.getName() + " connected from " + connection.getInetAddress();
        Mockito.verify(logger, Mockito.times(1)).info("s: player #" + 1 + ", " + who);

        assertEquals(newplayer.getColour(), PlayerColour.RED);
        assertEquals(newplayer.getCamoCategory(), Camouflage.COLOUR_CAMOUFLAGE);
        assertEquals(newplayer.getScore().getTotalScore(), 0);
    }

    //Moved to GameServer
    public void testReceivePlayerNameNewDuplicate() throws NoSuchFieldException, IllegalAccessException{
        sendPlayerNames(List.of(0, 1), List.of("JohnDoe", "JohnDoe"));

        IPlayer player = server.getPlayer(0);
        assertEquals(player.getName(), "JohnDoe");
        IPlayer newplayer = server.getPlayer(1);
        assertEquals(newplayer.getName(), "JohnDoe.2");
    }

    //Moved to GameServer
    public void testReceivePlayerInfo() {
        player.setColour(PlayerColour.BROWN);
        player.setStartingPos(5);
        player.setTeam(3);
        player.setCamoCategory("cat");
        player.setCamoFileName("cat.file");
        player.setNbrMFCommand(1);
        player.setNbrMFVibra(1);
        player.setNbrMFActive(1);
        player.setNbrMFInferno(1);
        player.setConstantInitBonus(10);
        EloScore score = new EloScore();
        score.win(1000);
        score.win(500);
        player.setScore(score);

        server.handle(0,new Packet(Packet.COMMAND_PLAYER_UPDATE, player));

        IPlayer gameplayer = server.getPlayer(0);
        assertEquals(gameplayer.getColour(), player.getColour());
        assertEquals(gameplayer.getStartingPos(), player.getStartingPos());
        assertEquals(gameplayer.getTeam(), player.getTeam());
        assertEquals(gameplayer.getCamoCategory(), player.getCamoCategory());
        assertEquals(gameplayer.getCamoFileName(), player.getCamoFileName());
        assertEquals(gameplayer.getNbrMFConventional(), player.getNbrMFConventional());
        assertEquals(gameplayer.getNbrMFCommand(), player.getNbrMFCommand());
        assertEquals(gameplayer.getNbrMFVibra(), player.getNbrMFVibra());
        assertEquals(gameplayer.getNbrMFActive(), player.getNbrMFActive());
        assertEquals(gameplayer.getNbrMFInferno(), player.getNbrMFInferno());
        assertEquals(gameplayer.getConstantInitBonus(), player.getConstantInitBonus());
        assertEquals(gameplayer.getScore().getTotalScore(), 1150);
    }

    private void setVictoryResult(VictoryResult result) throws IllegalAccessException, NoSuchFieldException {
        Victory vic = Mockito.mock(Victory.class);
        Mockito.when(vic.checkForVictory(game, game.getVictoryContext())).thenReturn(result);
        Field victory = Game.class.getDeclaredField("victory");
        victory.setAccessible(true);
        victory.set(game, vic);
    }

    //Moved to GameLogic
    public void testVictoryFalse() throws NoSuchFieldException, IllegalAccessException {
        VictoryResult result = Mockito.mock(VictoryResult.class);
        Mockito.when(result.victory()).thenReturn(false);

        setVictoryResult(result);

        assertFalse(server.victory());
        assertEquals(game.getVictoryPlayerId(), IPlayer.PLAYER_NONE);
        assertEquals(game.getVictoryTeam(), IPlayer.TEAM_NONE);
    }

    //Moved to GameLogic
    public void testVictoryDraw() throws NoSuchFieldException, IllegalAccessException {
        VictoryResult draw = Mockito.mock(VictoryResult.class);
        Mockito.when(draw.isDraw()).thenReturn(true);
        Mockito.when(draw.victory()).thenReturn(true);

        setVictoryResult(draw);

        assertTrue(server.victory());
        assertEquals(game.getVictoryPlayerId(), IPlayer.PLAYER_NONE);
        assertEquals(game.getVictoryTeam(), IPlayer.TEAM_NONE);
    }

    //Moved to GameLogic
    public void testVictoryWon() throws NoSuchFieldException, IllegalAccessException {
        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.isDraw()).thenReturn(false);
        Mockito.when(won.victory()).thenReturn(true);
        Mockito.when(won.getWinningPlayer()).thenReturn(0);
        Mockito.when(won.getWinningTeam()).thenReturn(1);

        setVictoryResult(won);

        assertTrue(server.victory());
        assertEquals(game.getVictoryPlayerId(), 0);
        assertEquals(game.getVictoryTeam(), 1);

    }

    public void testExecutePhaseVictory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        game.setPhase(IGame.Phase.PHASE_VICTORY);
        EloScore score = new EloScore();
        score.win(1000);
        score.win(500);
        player.setScore(score);
        IPlayer player2 = new Player(1, "Opponent");
        EloScore score2 = new EloScore();
        score2.win(1000);
        score2.lose(1000);
        score2.lose(500);
        player2.setScore(score2);
        game.addPlayer(1, player2);

        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.isDraw()).thenReturn(false);
        Mockito.when(won.victory()).thenReturn(true);
        Mockito.when(won.getWinningPlayer()).thenReturn(1);
        setVictoryResult(won);

        Method executePhase = Server.class.getDeclaredMethod("executePhase", IGame.Phase.class);
        executePhase.setAccessible(true);
        executePhase.invoke(server, IGame.Phase.PHASE_VICTORY);

        assertEquals(player.getScore().getTotalScore(), 866);
        assertEquals(player2.getScore().getTotalScore(), 712);
    }

    private void prepareForPhase(IGame.Phase phase) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method prepareforphase = Server.class.getDeclaredMethod("prepareForPhase", IGame.Phase.class);
        prepareforphase.setAccessible(true);
        prepareforphase.invoke(server, phase);

    }
    public void testPrepareForPhaseVictory() throws  IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        player.setDone(true);

        prepareForPhase(IGame.Phase.PHASE_VICTORY);

        assertFalse(player.isDone());
        assertEquals(server.getvPhaseReport(), server.getGame().getReports(0));
    }

    public void testPrepareForPhaseEndReport() throws NoSuchMethodException, InvocationTargetException,
            IllegalAccessException{
        player.setDone(true);

        prepareForPhase(IGame.Phase.PHASE_END_REPORT);

        assertFalse(player.isDone());
    }

    public void testPrepareForPhaseEnd() throws NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, NoSuchFieldException, NullPointerException{
        Field explodingcharges = Server.class.getDeclaredField("explodingCharges");
        explodingcharges.setAccessible(true);
        List<Building.DemolitionCharge> dem = new ArrayList<>();
        dem.add(new Building.DemolitionCharge(0,-1,new Coords(5, 5)));
        explodingcharges.set(server, dem);
        Field hexupdateset = Server.class.getDeclaredField("hexUpdateSet");
        hexupdateset.setAccessible(true);
        Set<Coords> hex = new LinkedHashSet<>();
        hex.add(new Coords(5,5));
        hexupdateset.set(server, hex);
        server.getGame().addIlluminatedPosition(new Coords(5, 5));

        prepareForPhase(IGame.Phase.PHASE_END);

        List<Building.DemolitionCharge> al = (List<Building.DemolitionCharge>) explodingcharges.get(server);
        Set<Coords> hus = (Set<Coords>) hexupdateset.get(server);

        assertEquals(server.getGame().getIlluminatedPositions().size(), 0);
        assertEquals(server.getvPhaseReport().size(), 4);
        assertEquals(al.size(), 0);
        assertEquals(hus.size(), 0);
    }

    private void endCurrentPhase(IGame.Phase phase) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        game.setPhase(phase);

        Method endcurrentphase = Server.class.getDeclaredMethod("endCurrentPhase");
        endcurrentphase.setAccessible(true);
        endcurrentphase.invoke(server);
    }

    public void testEndCurrentPhaseVictory() throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException{
        endCurrentPhase(IGame.Phase.PHASE_VICTORY);

        assertEquals(IGame.Phase.PHASE_LOUNGE, game.getPhase());
    }

    public void testEndCurrentPhaseEndReport1() throws NoSuchMethodException,
            IllegalAccessException, NoSuchFieldException, NullPointerException ,
            InvocationTargetException{
        game.setPhase(IGame.Phase.PHASE_END_REPORT);

        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.victory()).thenReturn(true);
        setVictoryResult(won);

        endCurrentPhase(IGame.Phase.PHASE_END_REPORT);

        assertEquals(IGame.Phase.PHASE_VICTORY, game.getPhase());

    }

    public void testEndCurrentPhaseEndReport2() throws NoSuchMethodException,
            IllegalAccessException, NoSuchFieldException, NullPointerException ,
            InvocationTargetException{
        VictoryResult result = Mockito.mock(VictoryResult.class);
        Mockito.when(result.victory()).thenReturn(false);
        setVictoryResult(result);

        endCurrentPhase(IGame.Phase.PHASE_END_REPORT);

        assertEquals(IGame.Phase.PHASE_INITIATIVE_REPORT, game.getPhase());
    }

    public void testEndCurrentPhaseEnd1() throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, NullPointerException, NoSuchFieldException{
        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.victory()).thenReturn(true);
        Mockito.when(won.getWinningPlayer()).thenReturn(0);
        setVictoryResult(won);

        endCurrentPhase(IGame.Phase.PHASE_END);

        assertEquals(IGame.Phase.PHASE_VICTORY, game.getPhase());
    }

    public void testEndCurrentPhaseEnd2() throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, NullPointerException, NoSuchFieldException{
        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.victory()).thenReturn(false);
        setVictoryResult(won);

        endCurrentPhase(IGame.Phase.PHASE_END);

        assertEquals(IGame.Phase.PHASE_INITIATIVE_REPORT, game.getPhase());
    }
}