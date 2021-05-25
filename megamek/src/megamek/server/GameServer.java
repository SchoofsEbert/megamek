package megamek.server;

import megamek.MegaMek;
import megamek.common.*;
import megamek.common.event.GameListener;
import megamek.common.net.IConnection;
import megamek.common.net.Packet;
import megamek.common.options.OptionsConstants;
import megamek.common.preference.PreferenceManager;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.WeaponHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class GameServer extends ServerRefactored{
    private GameLogic gamelogic;

    //TODO, once refactoring is done, all connections with server should be removed, because the method calls to server are on their place
    //AND the following constructors should be deleted!
    private Server server;

    public GameServer(String password, int port, Server server) throws IOException {
        this(password, port, false, "", server);
    }
    public GameServer(String password, int port, boolean registerWithServerBrowser,
                      String metaServerUrl, Server server) throws IOException {
        this(password, port, registerWithServerBrowser, metaServerUrl);
        this.server = server;
        gamelogic = new GameLogic(server);
    }

    //END constructors to be deleted

    public GameServer(String password, int port) throws IOException {
        this(password, port, false, "");
    }

    /**
     * Construct a new GameHost and begin listening for incoming clients.
     *
     *
     * @param password                  the <code>String</code> that is set as a password
     * @param port                      the <code>int</code> value that specifies the port that is
     *                                  used
     * @param registerWithServerBrowser a <code>boolean</code> indicating whether we should register
     *                                  with the master server browser on megamek.info
     */
    public GameServer(String password, int port, boolean registerWithServerBrowser,
                            String metaServerUrl) throws IOException {
        super(password, port, registerWithServerBrowser, metaServerUrl);
        // TODO IMPLEMENT

        gamelogic = new GameLogic();
    }

    //TODO once refactored, should be set to private
    void receivePlayerVersion(Packet packet, int connId) {
        String version = (String) packet.getObject(0);
        String clientChecksum = (String) packet.getObject(1);
        String serverChecksum = MegaMek.getMegaMekSHA256();
        StringBuilder buf = new StringBuilder();
        boolean needs = false;
        needs = verifyVersion(version, buf, needs);
        // print a message indicating client doesn't have jar file
        if (clientChecksum == null) {
            needs = verifyVersionChecksumNull(version, buf, "client");
            // print message indicating server doesn't have jar file
        } else if (serverChecksum == null) {
            needs = verifyVersionChecksumNull(version, buf, "server");
            // print message indicating a client/server checksum mismatch
        } else if (!clientChecksum.equals(serverChecksum)) {
            needs = verifyVersionClientServerMismatch(version, clientChecksum, serverChecksum, buf);
        }

        // Now, if we need to, send message!
        if (needs) {
            IPlayer player = getPlayer(connId);
            if (null != player) {
                //TODO this should be just sendServerChat once refactoring is done and the method is moved to ServerRefactored
                server.sendServerChat("For " + player.getName() + " Server reports:"
                        + System.lineSeparator()
                        + buf.toString());
            }
        } else {
            MegaMek.getLogger().info("SUCCESS: Client/Server Version (" + version + ") and Checksum ("
                    + clientChecksum + ") matched");
        }
    }

    private boolean verifyVersionClientServerMismatch(String version, String clientChecksum, String serverChecksum, StringBuilder buf) {
        boolean needs;
        if (!version.equals(MegaMek.VERSION)) {
            buf.append(System.lineSeparator());
            buf.append(System.lineSeparator());
        }
        buf.append("Client/Server checksum mismatch. Server reports: ").append(serverChecksum)
                .append(", Client reports: ").append(clientChecksum);
        MegaMek.getLogger().error("Client/Server Checksum Mismatch -- Client: " + clientChecksum
                + " Server: " + serverChecksum);

        needs = true;
        return needs;
    }


    private boolean verifyVersionChecksumNull(String version, StringBuilder buf, String kind) {
        boolean needs;
        if (!version.equals(MegaMek.VERSION)) {
            buf.append(System.lineSeparator()).append(System.lineSeparator());
        }
        if (kind == "server") {
            buf.append("Server Checksum is null. Server may not have a jar file");
            MegaMek.getLogger().info("Server does not have a jar file");
        } else if (kind == "client") {
            buf.append("Client Checksum is null. Client may not have a jar file");
            MegaMek.getLogger().info("Client does not have a jar file");
        }
        needs = true;
        return needs;
    }

    private boolean verifyVersion(String version, StringBuilder buf, boolean needs) {
        if (!version.equals(MegaMek.VERSION)) {
            buf.append("Client/Server version mismatch. Server reports: ").append(MegaMek.VERSION)
                    .append(", Client reports: ").append(version);
            MegaMek.getLogger().error("Client/Server Version Mismatch -- Client: "
                    + version + " Server: " + MegaMek.VERSION);
            needs = true;
        }
        return needs;
    }

    /**
     * Receives a player name, sent from a pending connection, and connects that
     * connection.
     */
    //TODO once refactored, should be set to private
    void receivePlayerName(Packet packet, int connId) {
        final IConnection conn = server.getPendingConnection(connId);
        String name = (String) packet.getObject(0);
        boolean returning = false;

        // this had better be from a pending connection
        if (conn == null) {

            MegaMek.getLogger().warning("Got a client name from a non-pending connection");
            return;
        }

        // check if they're connecting with the same name as a ghost player
        int existing_connId = gamelogic.getGhostConnIdByName(name);
        returning = existing_connId >= 0;
        if(returning) {
            connId = existing_connId;
            conn.setId(connId);
        }
        else {
            // Check to avoid duplicate names...
            name = gamelogic.correctDupeName(name);
            server.sendToPending(connId, new Packet(Packet.COMMAND_SERVER_CORRECT_NAME, name));
        }

        // right, switch the connection into the "active" bin
        server.connectionsPending.removeElement(conn);
        server.connections.addElement(conn);
        server.connectionIds.put(conn.getId(), conn);

        // add and validate the player info
        if (!returning) {
            gamelogic.addNewPlayer(connId, name);
        }

        // if it is not the lounge phase, this player becomes an observer
        IPlayer player = getPlayer(connId);
        if ( (gamelogic.getGame().getPhase() != IGame.Phase.PHASE_LOUNGE) && (null != player)
                &&  (gamelogic.getGame().getEntitiesOwnedBy(player) < 1)) {
            player.setObserver(true);
        }

        sendPlayerMatchSetUp(connId);

        final boolean showIPAddressesInChat = PreferenceManager.getClientPreferences().getShowIPAddressesInChat();

        try {
            InetAddress[] addresses = InetAddress.getAllByName(InetAddress
                    .getLocalHost().getHostName());
            for (InetAddress address : addresses) {
                MegaMek.getLogger().info("s: machine IP " + address.getHostAddress());
                if (showIPAddressesInChat) {
                    server.sendServerChat(connId,
                            "Machine IP is " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            // oh well.
        }

        getPlayerConnectionDetails(connId, showIPAddressesInChat);

    }

    private void sendPlayerMatchSetUp(int connId) {
        // send the player the motd
        server.sendServerChat(connId, server.motd);

        // send info that the player has connected
        server.send(server.createPlayerConnectPacket(connId));

        // tell them their local playerId
        server.send(connId, new Packet(Packet.COMMAND_LOCAL_PN, connId));

        // send current gameserver.getGame() info
        server.sendCurrentInfo(connId);
    }

    private void getPlayerConnectionDetails(int connId, boolean showIPAddressesInChat) {
        IPlayer player;
        MegaMek.getLogger().info("s: listening on port " + server.serverSocket.getLocalPort());
        if (showIPAddressesInChat) {
            // Send the port we're listening on. Only useful for the player
            // on the server machine to check.
            server.sendServerChat(connId,
                    "Listening on port " + server.serverSocket.getLocalPort());
        }

        // Get the player *again*, because they may have disconnected.
        player = getPlayer(connId);
        if (null != player) {
            String who = player.getName() + " connected from " + server.getClient(connId).getInetAddress();
            MegaMek.getLogger().info("s: player #" + connId + ", " + who);
            if (showIPAddressesInChat) {
                server.sendServerChat(who);
            }

        } // Found the player
    }

    /**
     * Allow the player to set whatever parameters he is able to
     */
    //TODO once refactored, should be set to private
    void receivePlayerInfo(Packet packet, int connId) {
        IPlayer player = (IPlayer) packet.getObject(0);
        IPlayer gamePlayer = gamelogic.getGame().getPlayer(connId);
        if (null != gamePlayer) { //TODO INTER: ONLY GAME
            if (gamePlayer.getConstantInitBonus()
                != player.getConstantInitBonus()) {
            server.sendServerChat("Player " + gamePlayer.getName()
                    + " changed their initiative bonus from "
                    + gamePlayer.getConstantInitBonus()
                    + " to " + player.getConstantInitBonus() + ".");
            }
            gamePlayer.update(player);
        }
    }

    //TODO Once refactored vPhaseReport should be an attribute of GameServer / GameLogic
    public void prepareForPhaseVictory() {
        server.resetPlayersDone();
        server.clearReports();
        server.prepareVictoryReport();
        gamelogic.getGame().addReports(server.vPhaseReport);
        gamelogic.PrepareEntitiesForVictory();
        server.send(server.createFullEntitiesPacket());
        server.send(server.createReportPacket(null));
        server.send(server.createEndOfGamePacket());
    }

    public void prepareForPhaseEnd(IGame.Phase phase){
        server.resetEntityPhase(phase);
        server.clearReports();
        server.resolveHeat();
        checkGlobalDamage();

        resolveDamage();

        // Moved this to the very end because it makes it difficult to see
        // more important updates when you have 300+ messages of smoke filling
        // whatever hex. Please don't move it above the other things again.
        // Thanks! Ralgith - 2018/03/15
        updateField();
    }

    private void updateField() {
        server.hexUpdateSet.clear();
        for (DynamicTerrainProcessor tp : server.terrainProcessors) {
            tp.doEndPhaseChanges(server.vPhaseReport);
        }
        server.sendChangedHexes(server.hexUpdateSet);

        server.checkForObservers();
        server.transmitAllPlayerUpdates();
        server.entityAllUpdate();
    }

    private void resolveDamage() {
        server.applyBuildingDamage();
        server.addReport(getGame().ageFlares());
        server.send(server.createFlarePacket());
        server.resolveAmmoDumps();
        server.resolveCrewWakeUp();
        server.resolveConsoleCrewSwaps();
        server.resolveSelfDestruct();
        server.resolveShutdownCrashes();
        server.checkForIndustrialEndOfTurn();
        server.resolveMechWarriorPickUp();
        server.resolveVeeINarcPodRemoval();
        server.resolveFortify();
    }

    private void checkGlobalDamage() {
        if  (gamelogic.getGame().getPlanetaryConditions().isSandBlowing()
                &&  (gamelogic.getGame().getPlanetaryConditions().getWindStrength() > PlanetaryConditions.WI_LIGHT_GALE)) {
            server.addReport(server.resolveBlowingSandDamage());
        }
        server.addReport(server.resolveControlRolls());
        server.addReport(server.checkForTraitors());
        // write End Phase header
        server.addReport(new Report(5005, Report.PUBLIC));
        server.checkLayExplosives();
        server.resolveHarJelRepairs();
        server.resolveEmergencyCoolantSystem();
        server.checkForSuffocation();
        gamelogic.getGame().getPlanetaryConditions().determineWind();
        server.send(server.createPlanetaryConditionsPacket());
    }

    public void prepareForPhaseEndReport(){
        server.resetActivePlayersDone();
        server.sendReport();
        if  (gamelogic.getGame().getOptions().booleanOption(OptionsConstants.BASE_PARANOID_AUTOSAVE)) {
            server.autoSave();
        }
    }

    public void endCurrentPhaseVictory() {
        gamelogic.endCurrentPhaseVictory();
        server.transmitGameVictoryEventToAll();
        server.resetGame();
    }

    public void endCurrentPhaseEnd(){
        // remove any entities that died in the heat/end phase before
        // checking for victory
        server.resetEntityPhase(IGame.Phase.PHASE_END);
        boolean victory = gamelogic.victory(); // note this may add reports
        // check phase report
        // HACK: hardcoded message ID check
        if ((server.vPhaseReport.size() > 3) || ((server.vPhaseReport.size() > 1)
                && (server.vPhaseReport.elementAt(1).messageId != 1205))) {
            gamelogic.getGame().addReports(server.vPhaseReport);
            server.changePhase(IGame.Phase.PHASE_END_REPORT);
        } else {
            // just the heat and end headers, so we'll add
            // the <nothing> label
            server.addReport(new Report(1205, Report.PUBLIC));
            gamelogic.getGame().addReports(server.vPhaseReport);
            server.sendReport();
            if (victory) {
                server.changePhase(IGame.Phase.PHASE_VICTORY);
            } else {
                server.changePhase(IGame.Phase.PHASE_INITIATIVE);
            }
        }
        // Decrement the ASEWAffected counter
        server.decrementASEWTurns();
    }

    public void endCurrentPhaseEndReport(){
        if (server.changePlayersTeam) {
            server.processTeamChange();
        }
        if (victory()) {
            server.changePhase(IGame.Phase.PHASE_VICTORY);
        } else {
            server.changePhase(IGame.Phase.PHASE_INITIATIVE);
        }
    }


    ////////TODO once refactored, these shortcut methods should be deleted.
    /**
     * Shortcut to gamelogic.getGame().getPlayer(id)
     */
    public IPlayer getPlayer(int id) {
        return gamelogic.getGame().getPlayer(id);
    }

    /**
     * Shortcut to gamelogic.getGame()
     */
    public IGame getGame() {
        return gamelogic.getGame();
    }

    /**
     * Shortcut to gamelogic.setGame()
     */
    public void setGame(IGame game) {
        gamelogic.setGame(game);
    }

    public IPlayer addNewPlayer(int connId, String name) {
        return gamelogic.addNewPlayer(connId, name);
    }

    /**
     * Validates the player info.
     */
    public void validatePlayerInfo(int playerId) {
        gamelogic.validatePlayerInfo(playerId);
    }

    /**
     * Returns true if victory conditions have been met. Victory conditions are
     * when there is only one player left with mechs or only one team. will also
     * add some reports to reporting
     */
    public boolean victory() {
        return gamelogic.victory();
    }

    public void updatePlayerScores() {
        gamelogic.updatePlayerScores();
    }
}
