package megamek.common;

import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.event.GameListener;
import megamek.common.icons.Camouflage;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.WeaponHandler;
import megamek.server.Server;
import megamek.server.victory.VictoryResult;

import java.util.*;

public class GameLogic {
    //TODO IMPLEMENT

    private IGame game = new Game();

    //TODO, once refactoring is done, all connections with server should be removed, because the method calls to server are on their place
    //AND the following constructors should be deleted!
    private Server server;

    public GameLogic(Server server) {
        this();
        this.server = server;
    }

    public GameLogic() {
        game.getOptions().initialize();
        game.getOptions().loadOptions();
    }

    /**
     * Sets the game for this server. Restores any transient fields, and sets
     * all players as ghosts. This should only be called during server
     * initialization before any players have connected.
     */
    public void setGame(IGame g) {
        // game listeners are transient so we need to save and restore them
        Vector<GameListener> gameListenersClone = new Vector<>(getGame().getGameListeners());

        game = g;

        for (GameListener listener : gameListenersClone) {
            getGame().addGameListener(listener);
        }

        List<Integer> orphanEntities = new ArrayList<>();

        // reattach the transient fields and ghost the players
        for (Iterator<Entity> e = game.getEntities(); e.hasNext(); ) {
            Entity ent = e.next();
            ent.setGame(game);

            if(ent.getOwner() == null) {
                orphanEntities.add(ent.getId());
                continue;
            }

            if (ent instanceof Mech) {
                ((Mech) ent).setBAGrabBars();
                ((Mech) ent).setProtomechClampMounts();
            }
            if (ent instanceof Tank) {
                ((Tank) ent).setBAGrabBars();
            }
        }

        game.removeEntities(orphanEntities, IEntityRemovalConditions.REMOVE_UNKNOWN);

        game.setOutOfGameEntitiesVector(game.getOutOfGameEntitiesVector());
        for (Enumeration<IPlayer> e = game.getPlayers(); e.hasMoreElements(); ) {
            IPlayer p = e.nextElement();
            p.setGame(game);
            p.setGhost(true);
        }
        // might need to restore weapon type for some attacks that take multiple
        // turns (like artillery)
        for (Enumeration<AttackHandler> a = game.getAttacks(); a
                .hasMoreElements(); ) {
            AttackHandler handler = a.nextElement();
            if (handler instanceof WeaponHandler) {
                ((WeaponHandler) handler).restore();
            }
        }

    }

    /**
     * Returns the current game object
     */
    public IGame getGame() {
        return game;
    }

    /**
     * Adds a new player to the game
     */
    public IPlayer addNewPlayer(int connId, String name) {
        int team = IPlayer.TEAM_UNASSIGNED;
        if  (game.getPhase() == IGame.Phase.PHASE_LOUNGE) {
            team = IPlayer.TEAM_NONE;
            for (IPlayer p : game.getPlayersVector()) {
                if (p.getTeam() > team) {
                    team = p.getTeam();
                }
            }
            team++;
        }
        IPlayer newPlayer = new Player(connId, name);
        PlayerColour colour = newPlayer.getColour();
        Enumeration<IPlayer> players = game.getPlayers();
        final PlayerColour[] colours = PlayerColour.values();
        while (players.hasMoreElements()) {
            final IPlayer p = players.nextElement();
            if (p.getId() == newPlayer.getId()) {
                continue;
            }

            if ((p.getColour() == colour) && (colours.length > (colour.ordinal() + 1))) {
                colour = colours[colour.ordinal() + 1];
            }
        }
        newPlayer.setColour(colour);
        newPlayer.setCamoCategory(Camouflage.COLOUR_CAMOUFLAGE);
        newPlayer.setCamoFileName(colour.name());
        newPlayer.setTeam(Math.min(team, 5));
        game.addPlayer(connId, newPlayer);
        validatePlayerInfo(connId);
        return newPlayer;
    }

    /**
     * Validates the player info.
     */
    public void validatePlayerInfo(int playerId) { //TODO INTEREST
        final IPlayer player = game.getPlayer(playerId);

        if (player != null) {
            // TODO : check for duplicate or reserved names

            // Colour Assignment
            final PlayerColour[] playerColours = PlayerColour.values();
            boolean allUsed = true;
            Set<PlayerColour> colourUtilization = new HashSet<>();
            for (Enumeration<IPlayer> i = game.getPlayers(); i.hasMoreElements(); ) {
                final IPlayer otherPlayer = i.nextElement();
                if (otherPlayer.getId() != playerId) {
                    colourUtilization.add(otherPlayer.getColour());
                } else {
                    allUsed = false;
                }
            }

            if (!allUsed && colourUtilization.contains(player.getColour())) {
                for (PlayerColour colour : playerColours) {
                    if (!colourUtilization.contains(colour)) {
                        player.setColour(colour);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Correct a duplicate player name
     *
     * @param oldName the <code>String</code> old player name, that is a duplicate
     * @return the <code>String</code> new player name
     */
    public String correctDupeName(String oldName) {
        for (Enumeration<IPlayer> i = game.getPlayers(); i.hasMoreElements(); ) {
            IPlayer player = i.nextElement();
            if (player.getName().equals(oldName)) {
                // We need to correct it.
                String newName = oldName;
                int dupNum;
                try {
                    dupNum = Integer.parseInt(oldName.substring(oldName.lastIndexOf(".") + 1));
                    dupNum++;
                    newName = oldName.substring(0, oldName.lastIndexOf("."));
                } catch (Exception e) {
                    // If this fails, we don't care much.
                    // Just assume it's the first time for this name.
                    dupNum = 2;
                }
                newName = newName.concat(".").concat(Integer.toString(dupNum));
                return correctDupeName(newName);
            }
        }
        return oldName;
    }

    /**
     * Returns true if victory conditions have been met. Victory conditions are
     * when there is only one player left with mechs or only one team. will also
     * add some reports to reporting
     */
    public boolean victory() { //TODO INTEREST
        VictoryResult vr = game.getVictory().checkForVictory(game, game.getVictoryContext());
        for (Report r : vr.getReports()) {
            // TODO this should obviously be the task of the reporter, once refactored
            server.addReport(r);
        }

        if (vr.victory()) {
            boolean draw = vr.isDraw();
            int wonPlayer = vr.getWinningPlayer();
            int wonTeam = vr.getWinningTeam();

            if (wonPlayer != IPlayer.PLAYER_NONE) {
                Report r = new Report(7200, Report.PUBLIC);
                r.add(getColorForPlayer (game.getPlayer(wonPlayer)));
                server.addReport(r);
            }
            if (wonTeam != IPlayer.TEAM_NONE) {
                Report r = new Report(7200, Report.PUBLIC);
                r.add("Team " + wonTeam);
                server.addReport(r);
            }
            if (draw) {
                // multiple-won draw
                game.setVictoryPlayerId(IPlayer.PLAYER_NONE);
                game.setVictoryTeam(IPlayer.TEAM_NONE);
            } else {
                // nobody-won draw or
                // single player won or
                // single team won
                game.setVictoryPlayerId(wonPlayer);
                game.setVictoryTeam(wonTeam);
            }
        } else {
            game.setVictoryPlayerId(IPlayer.PLAYER_NONE);
            game.setVictoryTeam(IPlayer.TEAM_NONE);
            if  (game.isForceVictory()) {
                server.cancelVictory();
            }
        }
        return vr.victory();
    }// end victory

    //TODO set to private once refactoring is done
    public static String getColorForPlayer(IPlayer p) {
        return "<B><font color='" + p.getColour().getHexString(0x00F0F0F0) + "'>" + p.getName() + "</font></B>";
    }
}


